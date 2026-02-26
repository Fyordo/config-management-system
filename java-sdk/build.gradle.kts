import java.util.Properties

plugins {
    id("java")
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
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.0")
    implementation("org.jetbrains:annotations:26.1.0")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}