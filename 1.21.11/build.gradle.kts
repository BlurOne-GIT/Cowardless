plugins {
    java
    kotlin("jvm") version "2.2.21"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
}

dependencies {
    implementation(project(":core"))
    implementation(kotlin("stdlib", "2.2.21"))
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
}