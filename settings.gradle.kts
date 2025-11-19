pluginManagement {
    plugins {
        kotlin("jvm") version "2.2.20"
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "Cowardless-paper"

include(
    "core",
    "plugin",
    "1.21.1",
    "1.21.4",
    "1.21.5",
    "1.21.8",
    "1.21.9"
)