#!make

REGISTRY_HOST = 192.168.0.187:5000
IMAGE_NAME = cms-java-example
FULL_IMAGE_NAME = ${REGISTRY_HOST}/${IMAGE_NAME}

build-jar:
	./gradlew clean bootJar;

docker-build-and-push:
	@echo "--- Извлечение версии из build.number ---"
	@MAIN_VERSION=$$(grep 'mainVersion=' build.number | cut -d'=' -f2); \
	 MAJOR_VERSION=$$(grep 'majorVersion=' build.number | cut -d'=' -f2); \
	 MINOR_VERSION=$$(grep 'minorVersion=' build.number | cut -d'=' -f2); \
	 VERSION="$${MAIN_VERSION}.$${MAJOR_VERSION}.$${MINOR_VERSION}"; \
	 echo "Building Docker image with version: $${VERSION}"; \
	 \
	 echo "--- Подготовка JAR-файла ---"; \
	 cp build/libs/*.jar .; \
	 \
	 JAR_FILE=$$(ls build/libs/*.jar); \
	 \
	echo "--- Сборка образа $${VERSION} для linux/amd64 ---"; \
	 docker build --platform=linux/amd64 --build-arg JAR_FILE=$$JAR_FILE -t $(FULL_IMAGE_NAME):$${VERSION} -f .docker/java/Dockerfile .; \
	 docker tag $(FULL_IMAGE_NAME):$${VERSION} $(FULL_IMAGE_NAME):latest; \
	 \
	 echo "--- Загрузка образа в registry $(REGISTRY_HOST) ---"; \
	 docker push $(FULL_IMAGE_NAME):$${VERSION}; \
	 docker push $(FULL_IMAGE_NAME):latest; \
	 \
	 echo "--- Образ $${VERSION} успешно загружен в registry $(REGISTRY_HOST) ---"; \
	 echo "Доступен по адресам:"; \
	 echo "  - $(FULL_IMAGE_NAME):$${VERSION}"; \
	 echo "  - $(FULL_IMAGE_NAME):latest"; \
	 \
	 rm java-example-*.jar
