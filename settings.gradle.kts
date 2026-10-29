pluginManagement {
    repositories {
        // Maven Central GCS mirror first for plugin/buildscript resolution too:
        // the plugin portal proxies most artifacts to repo.maven.apache.org,
        // which throttles our shared egress IP (see root build.gradle.kts).
        // Portal-only plugins 404 on the mirror and fall through.
        maven {
            name = "MavenCentralGcsMirror"
            url = uri("https://maven-central.storage-download.googleapis.com/maven2")
        }
        gradlePluginPortal()
    }
}

rootProject.name = "javalin-openapi"

include(
    "openapi-specification",
    "openapi-generator",
    "openapi-annotation-processor",
    "javalin-plugins",
    "javalin-plugins:javalin-openapi-plugin",
    "javalin-plugins:javalin-swagger-plugin",
    "javalin-plugins:javalin-redoc-plugin",
    "examples",
    "examples:javalin-gradle-kotlin"
)