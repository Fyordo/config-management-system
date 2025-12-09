import com.google.protobuf.gradle.id
import java.util.Properties

plugins {
	kotlin("jvm") version "2.2.21"
	kotlin("plugin.spring") version "2.2.21"
	id("org.springframework.boot") version "4.0.0"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.google.protobuf") version "0.9.5"
}

fun getVersionFromBuildNumber(): String {
	val buildNumberFile = file("build.number")
	if (!buildNumberFile.exists()) {
		return "0.0.1"
	}

	val props = Properties()
	buildNumberFile.inputStream().use { props.load(it) }

	val mainVersion = props.getProperty("mainVersion", "0")
	val majorVersion = props.getProperty("majorVersion", "0")
	val minorVersion = props.getProperty("minorVersion", "1")

	return "${mainVersion}.${majorVersion}.${minorVersion}"
}

group = "com.fyordo.cms"
version = getVersionFromBuildNumber()
description = "Server application for CMS project"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

extra["springGrpcVersion"] = "1.0.0"

dependencies {
	// ===== SPRING =====
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("org.springframework.boot:spring-boot-configuration-processor")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")

	// ===== ROCKSDB =====
	implementation("org.rocksdb:rocksdbjni:9.1.1")

	// ===== COROUTINES =====
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
	implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")

	// ===== RAFT (Apache Ratis) =====
	val ratisVersion = "2.3.0"
	implementation("org.apache.ratis:ratis-server:$ratisVersion")
	implementation("org.apache.ratis:ratis-client:$ratisVersion")
	implementation("org.apache.ratis:ratis-netty:$ratisVersion")
	implementation("org.apache.ratis:ratis-grpc:$ratisVersion")

	// ===== GRPC =====
	implementation("io.grpc:grpc-services")
	implementation("org.springframework.grpc:spring-grpc-spring-boot-starter")

	// ===== TEST =====
	testImplementation("org.springframework.grpc:spring-grpc-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

	// ===== HELPERS =====
	implementation("tools.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.grpc:spring-grpc-dependencies:${property("springGrpcVersion")}")
	}
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

protobuf {
	protoc {
		artifact = "com.google.protobuf:protoc"
	}
	plugins {
		id("grpc") {
			artifact = "io.grpc:protoc-gen-grpc-java"
		}
	}
	generateProtoTasks {
		all().forEach {
			it.plugins {
				id("grpc") {
					option("@generated=omit")
				}
			}
		}
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
