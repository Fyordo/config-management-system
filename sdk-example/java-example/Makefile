#!make

IMAGE_NAME = cms-java-example

build-jar:
	./gradlew clean bootJar;

docker-build-and-push:
	@echo "--- Fetching version from build.number ---"
	@MAIN_VERSION=$$(grep 'mainVersion=' build.number | cut -d'=' -f2); \
	 MAJOR_VERSION=$$(grep 'majorVersion=' build.number | cut -d'=' -f2); \
	 MINOR_VERSION=$$(grep 'minorVersion=' build.number | cut -d'=' -f2); \
	 VERSION="$${MAIN_VERSION}.$${MAJOR_VERSION}.$${MINOR_VERSION}"; \
	 echo "Building Docker image with version: $${VERSION}"; \
	 \
	 echo "--- Preparing JAR-file ---"; \
	 cp build/libs/*.jar .; \
	 \
	 JAR_FILE=$$(ls build/libs/*.jar); \
	 \
	 echo "--- Building image cms-java-example:$${VERSION} ---"; \
	 docker build --build-arg JAR_FILE=$$JAR_FILE -t $(IMAGE_NAME):$${VERSION} -f .docker/java/Dockerfile .; \
	 docker tag $(IMAGE_NAME):$${VERSION} $(IMAGE_NAME):latest; \
	 \
	 minikube image load $(IMAGE_NAME):$${VERSION}
	 \
	 echo "--- Image cms-java-example:$${VERSION} uploaded to minikube ---"; \
	 \
	 rm java-example-*.jar
