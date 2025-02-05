import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    java
    kotlin("jvm") version "2.1.10"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.14"
    id("com.gradleup.shadow") version "9.0.0-beta7"
}

group = "code.blurone"
version = "3.0.0-P0"

repositories {
    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    implementation(kotlin("stdlib", "2.1.10"))
    paperweight.paperDevBundle("1.21.4-R0.1-SNAPSHOT")
    shadow(kotlin("stdlib", "2.1.10"))
}

val shadowImplementation: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("*plugin.yml") {
        expand(props)
    }
}

val javaTargetVersion = 21

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaTargetVersion))
}

kotlin {
    jvmToolchain(javaTargetVersion)
    compilerOptions {
        apiVersion.set(KotlinVersion.KOTLIN_2_1)
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.build {
    dependsOn("shadowJar")
}

tasks.jar {
    archiveClassifier.set("kotlinless")
    manifest {
        attributes["paperweight-mappings-namespace"] = "mojang"
    }
}
// if you have shadowJar configured
tasks.shadowJar {
    manifest {
        attributes["paperweight-mappings-namespace"] = "mojang"
    }
    minimize()
    archiveClassifier.set("")
    enableRelocation = true
    relocationPrefix = "code.blurone.cowardless"
}