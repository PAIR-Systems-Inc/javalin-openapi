import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
    signing
}

val githubOwner = "PAIR-Systems-Inc"
val githubRepo = "javalin-openapi"
val releaseVersion =
    providers.gradleProperty("releaseVersion")
        .orElse(providers.environmentVariable("RELEASE_VERSION"))
        .orElse("8.0.0")
        .get()

description = "Javalin OpenAPI Parent | Parent"

allprojects {
    apply(plugin = "java-library")
    apply(plugin = "signing")
    apply(plugin = "maven-publish")

    group = "ai.pairsys"
    version = releaseVersion

    repositories {
        mavenCentral()
    }

    publishing {
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/$githubOwner/$githubRepo")
                credentials {
                    username =
                        providers.gradleProperty("gpr.user")
                            .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                            .orNull
                    password =
                        providers.gradleProperty("gpr.key")
                            .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                            .orNull
                }
            }
        }
    }

    afterEvaluate {
        description
            ?.takeIf { it.isNotEmpty() }
            ?.split("|")
            ?.let { (projectName, projectDescription) ->
                publishing {
                    publications {
                        create<MavenPublication>("library") {
                            groupId = project.group.toString()
                            artifactId = project.name
                            version = project.version.toString()

                            pom {
                                name.set(projectName)
                                description.set(projectDescription)
                                url.set("https://github.com/$githubOwner/$githubRepo")

                                licenses {
                                    license {
                                        name.set("The Apache License, Version 2.0")
                                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                                    }
                                }
                                developers {
                                    developer {
                                        id.set("PAIR-Systems-Inc")
                                        name.set("PAIR-Systems-Inc")
                                    }
                                }
                                scm {
                                    connection.set("scm:git:https://github.com/$githubOwner/$githubRepo.git")
                                    developerConnection.set("scm:git:ssh://git@github.com:$githubOwner/$githubRepo.git")
                                    url.set("https://github.com/$githubOwner/$githubRepo")
                                }
                            }

                            from(components.getByName("java"))
                        }
                    }
                }

                if (findProperty("signing.keyId").takeIf { it != null && it.toString().trim().isNotEmpty() } != null) {
                    signing {
                        sign(publishing.publications.getByName("library"))
                    }
                }
            }
    }

    java {
        withJavadocJar()
        withSourcesJar()
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

subprojects {
    apply(plugin = "application")
    apply(plugin = "org.jetbrains.kotlin.jvm")

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            languageVersion.set(KotlinVersion.KOTLIN_2_2)
            javaParameters.set(true)
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

val mavenExamples = listOf("javalin-maven-java", "javalin-maven-kotlin")

mavenExamples.forEach { example ->
    tasks.register<Exec>("test-maven-example-$example") {
        description = "Compile Maven example: $example"
        group = "verification"
        workingDir = file("examples/$example")
        commandLine("./mvnw", "compile", "-B", "-q")
        environment("JAVA_HOME", System.getProperty("java.home"))
        dependsOn(subprojects.map { it.tasks.named("publishToMavenLocal") })
    }

    tasks.register<Exec>("run-maven-example-$example") {
        description = "Run Maven example: $example"
        group = "application"
        workingDir = file("examples/$example")
        commandLine("./mvnw", "compile", "exec:java", "-B", "-q")
        environment("JAVA_HOME", System.getProperty("java.home"))
        dependsOn(subprojects.map { it.tasks.named("publishToMavenLocal") })
    }
}

tasks.register("test-maven-examples") {
    description = "Compile all Maven examples"
    group = "verification"
    dependsOn(mavenExamples.map { "test-maven-example-$it" })
}
