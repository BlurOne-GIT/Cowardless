import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    java
    kotlin("jvm") version "2.2.20"
    id("com.gradleup.shadow") version "9.2.1"
}

dependencies {
    implementation(kotlin("stdlib", "2.2.20"))
    // Using implementation of adventure to have version 4.20 api on versions older than 1.21.4
    implementation("net.kyori:adventure-api:4.25.0")
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    implementation(project(":core"))
    implementation(project(":1.21.1"))
    implementation(project(":1.21.4"))
    implementation(project(":1.21.5"))
    implementation(project(":1.21.8"))
    implementation(project(":1.21.9"))
    shadow(kotlin("stdlib", "2.2.20"))
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
        apiVersion.set(KotlinVersion.KOTLIN_2_2)
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.build {
    dependsOn("shadowJar")
}

/*
tasks.jar {
    archiveClassifier.set("kotlinless")
    manifest {
        attributes["paperweight-mappings-namespace"] = "mojang"
    }
}
*/
// if you have shadowJar configured
tasks.shadowJar {
    manifest {
        attributes["paperweight-mappings-namespace"] = "mojang"
    }
    minimize()
    archiveClassifier.set("")
    enableAutoRelocation = false

    relocate("kotlin", "code.blurone.cowardless.shaded.kotlin") {
        exclude("code/blurone/cowardless/**")
    }

    /* relocate("net.kyori.adventure", "code.blurone.cowardless.shaded.adventure") {
        exclude("code/blurone/cowardless/**")
    } */*/
    //exclude("code/blurone/cowardless/**")
    //relocationPrefix = "code.blurone.cowardless.shaded"
}
