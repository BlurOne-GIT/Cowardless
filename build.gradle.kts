import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    java
    kotlin("jvm") version "2.2.21"
    //id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
    id("com.gradleup.shadow") version "9.2.2"
}

allprojects {
    group = "code.blurone"
    version = "4.1.0-P0"

    repositories {
        mavenCentral()
        maven {
            name = "papermc-repo"
            url = uri("https://repo.papermc.io/repository/maven-public/")
        }
    }

    /*dependencies {
        implementation(kotlin("stdlib", "2.2.20"))
    }*/
}

dependencies {
    implementation(project(":core"))
    implementation(project(":plugin"))
    implementation(project(":1.21.1"))
    implementation(project(":1.21.4"))
    implementation(project(":1.21.5"))
    implementation(project(":1.21.8"))
    implementation(project(":1.21.9"))
    shadow(kotlin("stdlib", "2.2.21"))
    //paperweight.paperDevBundle("1.21.4-R0.1-SNAPSHOT")
    //compileOnly("dev.folia:folia-api:1.20.4-R0.1-SNAPSHOT")
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
    dependsOn(":plugin:shadowJar")
}