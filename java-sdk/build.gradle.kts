import java.util.Properties
import com.google.protobuf.gradle.id

plugins {
    id("java")
    id("maven-publish")
    id("com.google.protobuf") version "0.9.5"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
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

group = "com.fyordo.cms.sdk"
version = getVersionFromBuildNumber()
description = "Java SDK for CMS Project"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.2")
    implementation("org.jetbrains:annotations:26.1.0")

    implementation("com.google.protobuf:protobuf-java:4.34.1")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

java {
    withSourcesJar()
    withJavadocJar()
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.66.0"
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

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            groupId = project.group.toString()
            artifactId = "java-sdk"
            version = project.version.toString()

            pom {
                name.set("CMS Java SDK")
                description.set(project.description)
            }
        }
    }
    repositories {
        mavenLocal()
    }
}