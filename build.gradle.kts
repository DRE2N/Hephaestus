import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions

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
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
    id("xyz.jpenilla.run-paper") version "3.0.2" // Adds runServer and runMojangMappedServer tasks for testing
}

group = "de.erethon.hephaestus"
version = "26.1-SNAPSHOT"
description = "Items"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
    withJavadocJar()
}

val papyrusVersion = "26.1.2-SNAPSHOT"

dependencies {
    paperweight.devBundle("de.erethon.papyrus", papyrusVersion) { isChanging = true}
    compileOnly("de.erethon.hecate:Hecate:1.3-SNAPSHOT")
    compileOnly("de.erethon.tyche:Tyche:1.0-SNAPSHOT")
    compileOnly("de.erethon.questsxl:QuestsXL:1.0.6-SNAPSHOT")
}

tasks {
    val webDir = layout.projectDirectory.dir("web")
    val isWindows = System.getProperty("os.name").lowercase().contains("windows")

    val installWebAuctionHouse by registering(Exec::class) {
        group = "build"
        description = "Installs Hephaestus web auction house dependencies with Bun."
        workingDir = webDir.asFile
        commandLine(if (isWindows) listOf("cmd", "/c", "bun", "install") else listOf("bun", "install"))
        inputs.file(webDir.file("package.json"))
        inputs.file(webDir.file("bun.lock"))
        outputs.dir(webDir.dir("node_modules"))
    }

    val buildWebAuctionHouse by registering(Exec::class) {
        group = "build"
        description = "Builds the Hephaestus web auction house into plugin resources."
        dependsOn(installWebAuctionHouse)
        workingDir = webDir.asFile
        commandLine(if (isWindows) listOf("cmd", "/c", "bun", "run", "build") else listOf("bun", "run", "build"))
        inputs.dir(webDir.dir("src"))
        inputs.file(webDir.file("index.html"))
        inputs.file(webDir.file("package.json"))
        inputs.file(webDir.file("vite.config.ts"))
        outputs.dir(layout.projectDirectory.dir("src/main/resources/web"))
    }

    paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION
    runServer {
        if (!project.buildDir.exists()) {
            project.buildDir.mkdir()
        }
        val f = File(project.buildDir, "server.jar");
        uri("https://github.com/DRE2N/Papyrus/releases/download/latest/papyrus-paperclip-26.1.2.jar").toURL().openStream().use { it.copyTo(f.outputStream()) }
        serverJar(f)
        runDirectory.set(file("C:\\Dev\\Erethon"))
    }
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(25)
    }
    processResources {
        dependsOn(buildWebAuctionHouse)
        filteringCharset = Charsets.UTF_8.name()
    }

    named("sourcesJar") { // Gradle 9 needs this for some reason, it is not happy otherwise
        dependsOn(buildWebAuctionHouse)
    }

    withType<Javadoc> {
        options.encoding = Charsets.UTF_8.name()
        (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
        (options as StandardJavadocDocletOptions).locale = "en"
        isFailOnError = false
    }

    jar {
        manifest {
            attributes(
                "paperweight-mappings-namespace" to "mojang"
            )
        }
    }

}

tasks.register<Copy>("deployToSharedServer") {
    doNotTrackState("")
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
            url = uri("https://repo.erethon.de/snapshots")
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
        }
    }
}
