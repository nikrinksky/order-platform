.PHONY: dev-up dev-down dev-build

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