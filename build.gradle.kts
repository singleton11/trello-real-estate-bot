plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinx.serialization)
}

val mainClassFqn = "io.ktor.server.netty.EngineMain"
application {
    mainClass.set(mainClassFqn)

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.bundles.ktor.server.jvm)
    implementation(libs.bundles.ktor.client.jvm)
    implementation(libs.logback)
}

ktor {
    docker {
        externalRegistry.set(
            io.ktor.plugin.features.DockerImageRegistry.dockerHub(
                appName = provider { "ktor-app" },
                username = providers.environmentVariable("DOCKER_HUB_USERNAME"),
                password = providers.environmentVariable("DOCKER_HUB_PASSWORD")
            )
        )
    }
}

jib {
    container {
        mainClass = mainClassFqn
    }
}
