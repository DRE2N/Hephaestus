
import java.io.FileInputStream

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/maven-public/")
    maven("https://repo.erethon.de/snapshots/")
    maven("https://repo.erethon.de/releases/")
}
plugins {
    `java-library`
    `maven-publish`
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.14"
    id("xyz.jpenilla.run-paper") version "1.0.6" // Adds runServer and runMojangMappedServer tasks for testing
}

group = "de.erethon.hephaestus"
version = "1.0-SNAPSHOT"
description = "Items"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

val papyrusVersion = "1.21.4-R0.1-SNAPSHOT"

dependencies {
    paperweight.devBundle("de.erethon.papyrus", papyrusVersion) { isChanging = true}
}

tasks {
    paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION
    runServer {
        if (!project.buildDir.exists()) {
            project.buildDir.mkdir()
        }
        val f = File(project.buildDir, "server.jar");
        uri("https://github.com/DRE2N/Papyrus/releases/download/latest/papyrus-paperclip-$papyrusVersion-mojmap.jar").toURL().openStream().use { it.copyTo(f.outputStream()) }
        serverJar(f)
    }
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(21)
    }
    processResources {
        filteringCharset = Charsets.UTF_8.name()
    }

    javadoc {
        options.encoding = Charsets.UTF_8.name()
    }
    val javadocJar by creating(Jar::class) {
        dependsOn(javadoc)
        archiveClassifier.set("javadoc")
        from(javadoc)
    }
    val sourcesJar by creating(Jar::class) {
        archiveClassifier.set("sources")
        from(sourceSets.main.get().allSource)
    }

    jar {
        manifest {
            attributes(
                "paperweight-mappings-namespace" to "mojang"
            )
        }
    }

    assemble {
        dependsOn(javadocJar)
        dependsOn(sourcesJar)
    }
}

tasks.register<Copy>("deployToSharedServer") {
    group = "Erethon"
    description = "Used for deploying the plugin to the shared server. runServer will do this automatically." +
            "This task is only for manual deployment when running runServer from another plugin."
    dependsOn(":jar")
    from(layout.buildDirectory.file("libs/Hephaestus-$version.jar"))
    into("C:\\Dev\\Erethon\\plugins")
}

publishing {
    repositories {
        maven {
            name = "erethon"
            url = uri("https://reposilite.fyreum.de/snapshots/")
            credentials(PasswordCredentials::class)
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            groupId = "${project.group}"
            artifactId = "Hephaestus"
            version = "${project.version}"

            from(components["java"])
            artifact(tasks["javadocJar"])
        }
    }
}
