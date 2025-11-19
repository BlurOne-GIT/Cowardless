plugins {
    java
    kotlin("jvm") version "2.2.20"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
}

dependencies {
    implementation(project(":core"))
    implementation(kotlin("stdlib", "2.2.20"))
    paperweight.paperDevBundle("1.21.9-R0.1-SNAPSHOT")
}