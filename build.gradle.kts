plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.epictrails"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    implementation("com.github.retrooper:packetevents-spigot:2.4.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    implementation("com.mysql:mysql-connector-j:9.1.0")
    implementation("net.kyori:adventure-text-minimessage:4.17.0")
    implementation("net.kyori:adventure-text-serializer-legacy:4.17.0")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        relocate("com.github.retrooper.packetevents", "com.epictrails.libs.packetevents")
        relocate("io.github.retrooper.packetevents", "com.epictrails.libs.packeteventsimpl")
        relocate("com.zaxxer.hikari", "com.epictrails.libs.hikari")
        relocate("org.sqlite", "com.epictrails.libs.sqlite")
        relocate("com.mysql", "com.epictrails.libs.mysql")
        relocate("net.kyori.adventure.text.minimessage", "com.epictrails.libs.minimessage")
        relocate("net.kyori.adventure.text.serializer.legacy", "com.epictrails.libs.legacyserializer")
    }
    build {
        dependsOn(shadowJar)
    }
    compileJava {
        options.encoding = "UTF-8"
    }
}
