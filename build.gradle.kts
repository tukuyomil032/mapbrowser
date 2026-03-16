plugins {
    java
    id("com.gradleup.shadow") version "9.2.0"
}

group = "com.tukuyomil032.mapbrowser"
version = "1.0.0"

repositories {
    mavenCentral()

    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.jar {
    archiveBaseName.set("mapbrowser")

    manifest {
        attributes(
            "paperweight-mappings-namespace" to "spigot"
        )
    }
}

tasks.shadowJar {
    archiveBaseName.set("MapBrowser")
    archiveClassifier.set("all")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}