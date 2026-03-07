# pairsys-javalin-openapi

PairSys-maintained Javalin OpenAPI plugins and annotation processor for the Javalin 7 line.

Coordinates:

```text
groupId:    ai.pairsys
artifactId: javalin-openapi-plugin
artifactId: javalin-redoc-plugin
artifactId: javalin-swagger-plugin
artifactId: openapi-annotation-processor
```

## Build

```bash
./gradlew test
```

## Publish

This repository publishes to GitHub Packages for [`PAIR-Systems-Inc/javalin-openapi`](https://github.com/PAIR-Systems-Inc/javalin-openapi).

To publish from GitHub:

1. Create and publish a GitHub release with a tag such as `v8.0.0`.
2. The `Publish Package` workflow will run automatically.
3. GitHub Packages will publish the `ai.pairsys:*` artifacts for that version.

To publish locally:

```bash
./gradlew publish -PreleaseVersion=8.0.0
```

Gradle credentials for local publishing or local consumption can be provided in `~/.gradle/gradle.properties`:

```text
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_TOKEN
```

To consume from another Gradle build:

```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/PAIR-Systems-Inc/javalin-openapi")
        credentials {
            username = findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("ai.pairsys:javalin-openapi-plugin:8.0.0")
    implementation("ai.pairsys:javalin-redoc-plugin:8.0.0")
    implementation("ai.pairsys:javalin-swagger-plugin:8.0.0")
    annotationProcessor("ai.pairsys:openapi-annotation-processor:8.0.0")
}
```
