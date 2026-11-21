.PHONY: dev-up dev-down dev-build k3d-up k3d-down create-secrets inject-secrets helm-lint helm-template

# Local compose stack
dev-up:
	@echo "Starting Order Platform..."
	docker-compose --profile dev up -d
	@echo "Waiting for services..."
	sleep 20
	@echo "Platform ready!"
	@echo "API Gateway: http://localhost:8080"
	@echo "Auth Service: http://localhost:8090"

dev-down:
	@echo "Stopping Order Platform..."
	docker-compose --profile dev down -v
	@echo "All services stopped"

dev-build:
	@echo "Building services..."
	docker-compose build
	@echo "Build complete"

# ============= LOCAL KUBERNETES (k3d) =============
# The cluster publishes the entry points on the host through the k3d load balancer, so a service
# is reachable on the same localhost port the compose stack uses. Stop the compose stack first:
# two sets of containers cannot bind the same host port.
CLUSTER ?= order-platform
NAMESPACE ?= order-platform
RELEASE ?= order-platform
CHART ?= helm/order-platform

SERVICES := api-gateway auth-service user-service product-service inventory-service \
            order-service notification-service

# Everything the chart runs. The infrastructure images are imported as well: the kubelet would
# otherwise pull them from Docker Hub on the first start, which on a slow connection leaves the
# pods in ContainerCreating for tens of minutes.
SERVICE_IMAGES := $(foreach s,$(SERVICES),order-platform-$(s):latest)
INFRA_IMAGES := postgres:15-alpine mongo:7 redis:7-alpine redpandadata/redpanda:v23.3.1 \
                confluentinc/cp-schema-registry:7.5.0 mailhog/mailhog:latest \
                prom/prometheus:v2.53.4 grafana/grafana:11.3.0 grafana/loki:2.9.8 \
                jaegertracing/all-in-one:1.62.0 timberio/vector:0.49.0-debian busybox:1.36

# Host ports are parameters because a port already taken by the compose stack (or by another
# cluster) makes `k3d cluster create` fail with an error that says nothing about the real cause.
GATEWAY_PORT ?= 8080
GRAFANA_PORT ?= 3000
PROMETHEUS_PORT ?= 9090
JAEGER_PORT ?= 16686
MAILHOG_PORT ?= 8025

K3D_PORTS = \
	--port "$(GATEWAY_PORT):30080@loadbalancer" \
	--port "$(GRAFANA_PORT):30000@loadbalancer" \
	--port "$(PROMETHEUS_PORT):30090@loadbalancer" \
	--port "$(JAEGER_PORT):30686@loadbalancer" \
	--port "$(MAILHOG_PORT):30825@loadbalancer"

k3d-up:
	@echo "Creating k3d cluster $(CLUSTER)..."
	# One agent is enough for the whole platform and keeps the memory footprint near 4 GB.
	# Traefik is disabled: the entry points are published through the k3d load balancer, so an
	# ingress controller would only consume resources and add a hop.
	k3d cluster create $(CLUSTER) \
		--agents 1 \
		$(K3D_PORTS) \
		--k3s-arg "--disable=traefik@server:0" \
		--wait
	@echo "Building the images..."
	docker compose build
	@echo "Importing the images into the cluster..."
	# Through containerd, not `k3d image import`: that one silently skips multi-arch images
	# (redpanda, schema-registry, mailhog) and the pods then hang on a Docker Hub pull.
	sh scripts/import-images.sh $(CLUSTER) $(SERVICE_IMAGES) $(INFRA_IMAGES)
	@echo "Creating the namespace..."
	kubectl create namespace $(NAMESPACE) --dry-run=client -o yaml | kubectl apply -f -
	@echo "Creating the secrets..."
	# auth-service declares jwt.secret as ${JWT_SECRET} without a default, so the Secret has to
	# exist before the pod starts - otherwise the placeholder does not resolve and the context
	# fails on startup.
	$(MAKE) create-secrets
	@echo "Installing the chart..."
	helm upgrade --install $(RELEASE) $(CHART) --namespace $(NAMESPACE) --wait --timeout 15m
	@echo ""
	@echo "Platform ready:"
	@echo "  API Gateway / Swagger UI: http://localhost:$(GATEWAY_PORT)"
	@echo "  Grafana:                  http://localhost:$(GRAFANA_PORT) (admin/admin)"
	@echo "  Prometheus:               http://localhost:$(PROMETHEUS_PORT)"
	@echo "  Jaeger:                   http://localhost:$(JAEGER_PORT)"
	@echo "  MailHog:                  http://localhost:$(MAILHOG_PORT)"

k3d-down:
	@echo "Removing the release and the cluster..."
	helm uninstall $(RELEASE) --namespace $(NAMESPACE) || true
	k3d cluster delete $(CLUSTER)
	@echo "Cluster removed"

# Creates the Secret the pods read their JWT secret and the bootstrap admin from. The file is not
# in git: .secrets/dev/platform.env.example shows the keys.
create-secrets:
	@test -f .secrets/dev/platform.env || \
		(echo "Missing .secrets/dev/platform.env - copy .secrets/dev/platform.env.example and fill it in"; exit 1)
	kubectl create secret generic platform-secrets \
		--namespace $(NAMESPACE) \
		--from-env-file=.secrets/dev/platform.env \
		--dry-run=client -o yaml | kubectl apply -f -
	@echo "Secret platform-secrets applied to $(NAMESPACE)"

# Same, then restarts the pods so a running platform picks the values up. The pods read the secret
# through an optional reference, so they start before it exists; a restart is what makes them read
# it. `make k3d-up` calls create-secrets instead: there is nothing to restart yet.
inject-secrets: create-secrets
	kubectl rollout restart deployment --namespace $(NAMESPACE) \
		$(foreach s,$(SERVICES),$(s))

helm-lint:
	helm lint $(CHART)

helm-template:
	helm template $(RELEASE) $(CHART) --namespace $(NAMESPACE)