#!make

IMAGE_NAME = cms-go-example

REPO_ROOT = ../..

build:
	go build -o bin/$(IMAGE_NAME) .

docker-build-and-push:
	@echo "--- Fetching version from build.number ---"
	@MAIN_VERSION=$$(grep 'mainVersion=' build.number | cut -d'=' -f2); \
	 MAJOR_VERSION=$$(grep 'majorVersion=' build.number | cut -d'=' -f2); \
	 MINOR_VERSION=$$(grep 'minorVersion=' build.number | cut -d'=' -f2); \
	 VERSION="$${MAIN_VERSION}.$${MAJOR_VERSION}.$${MINOR_VERSION}"; \
	 echo "Building Docker image with version: $${VERSION}"; \
	 \
	 echo "--- Building image $(IMAGE_NAME):$${VERSION} ---"; \
	 docker build \
	   -t $(IMAGE_NAME):$${VERSION} \
	   -f .docker/Dockerfile \
	   $(REPO_ROOT); \
	 docker tag $(IMAGE_NAME):$${VERSION} $(IMAGE_NAME):latest; \
	 \
	 minikube image load $(IMAGE_NAME):$${VERSION}; \
	 \
	 echo "--- Image $(IMAGE_NAME):$${VERSION} uploaded to minikube ---"
