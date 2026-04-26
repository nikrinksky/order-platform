# Makefile
.PHONY: help dev-up dev-down dev-build logs-clean test k3d-up

help:
	@echo "Available commands:"
	@echo "  make dev-up       - Start all services"
	@echo "  make dev-down     - Stop all services and remove volumes"
	@echo "  make dev-build    - Rebuild all services"
	@echo "  make logs-clean   - Clean up old containers and volumes"
	@echo "  make test         - Run integration tests"
	@echo "  make k3d-up       - Deploy to local k3d cluster"

dev-up:
	docker-compose up -d
	@echo "✅ All services started"
	@echo "📊 Access points:"
	@echo "  API Gateway: http://localhost:8080"
	@echo "  Auth Service: http://localhost:8081"
	@echo "  User Service: http://localhost:8082"
	@echo "  Product Service: http://localhost:8083"
	@echo "  Inventory Service: http://localhost:8084"
	@echo "  Order Service: http://localhost:8085"
	@echo "  Notification Service: http://localhost:8086"
	@echo "  PostgreSQL: localhost:5432"
	@echo "  MongoDB: localhost:27017"
	@echo "  Redis: localhost:6379"
	@echo "  Kafka (Redpanda): localhost:19092"
	@echo "  Schema Registry: http://localhost:8081"
	@echo "  Prometheus: http://localhost:9090"
	@echo "  Grafana: http://localhost:3000 (admin/admin)"
	@echo "  Jaeger: http://localhost:16686"
	@echo "  MailHog: http://localhost:8025"
	@echo "  pgAdmin: http://localhost:5050 (admin@orderplatform.com/admin)"
	@echo "  mongo-express: http://localhost:8087 (admin/admin)"
	@echo "  redis-commander: http://localhost:8088"

dev-down:
	docker-compose down -v
	@echo "✅ All services stopped and volumes removed"

dev-build:
	docker-compose build --no-cache
	@echo "✅ All services rebuilt"

logs-clean:
	docker system prune -a --volumes -f
	@echo "✅ Cleanup completed"

test:
	docker-compose -f docker-compose.test.yaml up --abort-on-container-exit
	@echo "✅ Tests completed"

k3d-up:
	k3d cluster create order-platform --api-port 6443 -p "8080:80@loadbalancer"
	kubectl create namespace dev
	kubectl create namespace prod
	helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
	helm repo update
	helm upgrade --install order-platform ./helm-charts --namespace dev
	@echo "✅ K3d cluster ready"