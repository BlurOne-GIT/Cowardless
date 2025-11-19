plugins {
    java
    kotlin("jvm") version "2.2.20"
}

dependencies {
    implementation(kotlin("stdlib", "2.2.20"))
    // Using implementation of adventure to have version 4.20 api on versions older than 1.21.4
    implementation("net.kyori:adventure-api:4.25.0")
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}
