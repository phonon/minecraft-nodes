/*
 * This file was generated by the Gradle 'init' task.
 *
 * This generated file contains a sample Kotlin application project to get you started.
 */

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

// disable default versioning
version = ""

// custom versioning flag
val VERSION = "0.0.10"

// jvm target
val JVM = 17 // 1.8 for 8, 11 for 11

// base of output jar name
val OUTPUT_JAR_NAME = "nodes"

// target will be set to minecraft version by cli input parameter
var target = "1.19"

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm") version "1.6.10"
    id("com.github.johnrengelman.shadow") version "7.0.0"
    // maven() // no longer needed in gradle 7

    // Apply the application plugin to add support for building a CLI application.
    application
}

repositories {
    // Use jcenter for resolving dependencies.
    // You can declare any Maven/Ivy/file repository here.
    jcenter()
    
    maven { // paper
        url = uri("https://papermc.io/repo/repository/maven-public/")
    }
    maven { // protocol lib
        url = uri("https://repo.dmulloy2.net/nexus/repository/public/")
    }
    maven {
        url = uri("https://repo.essentialsx.net/releases/")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(JVM))
    }
}

configurations {
    create("resolvableImplementation") {
        isCanBeResolved = true
        isCanBeConsumed = true
    }
}

dependencies {
    // Align versions of all Kotlin components
    compileOnly(platform("org.jetbrains.kotlin:kotlin-bom"))

    // Use the Kotlin JDK 8 standard library.
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    if ( project.hasProperty("no-kotlin") == false ) { // shadow kotlin unless "no-kotlin" flag
        configurations["resolvableImplementation"]("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    }

    // google json
    compileOnly("com.google.code.gson:gson:2.8.6")
    configurations["resolvableImplementation"]("com.google.code.gson:gson:2.8.6")
    
//    // put spigot/paper on path otherwise kotlin vs code plugin screeches
//    api("com.destroystokyo.paper:paper-api:1.19.2-R0.1-SNAPSHOT")
    
    // essentials
    compileOnly("net.essentialsx:EssentialsX:2.19.7")

    // protocol lib (nametag packets)
    compileOnly("com.comphenix.protocol:ProtocolLib:4.5.0")

    // Use the Kotlin test library.
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    // Use the Kotlin JUnit integration.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")

    if ( project.hasProperty("1.12") == true ) {
        target = "1.12"
        // spigot/paper api
        compileOnly("com.destroystokyo.paper:paper-api:1.12.2-R0.1-SNAPSHOT")
        // fast block edit internal lib
        compileOnly(files("./lib/block_edit/build/libs/block-edit-lib-1.12.jar"))
        configurations["resolvableImplementation"](files("./lib/block_edit/build/libs/block-edit-lib-1.12.jar"))
    } else if ( project.hasProperty("1.16") == true ) {
        target = "1.16"
        // spigot/paper api
        compileOnly("com.destroystokyo.paper:paper-api:1.16.5-R0.1-SNAPSHOT")
    } else if ( project.hasProperty("1.18") == true ) {
        target = "1.18"
        // spigot/paper api
        compileOnly("io.papermc.paper:paper-api:1.18.1-R0.1-SNAPSHOT")
    } else if ( project.hasProperty("1.19") == true ) {
        target = "1.19"
        compileOnly("io.papermc.paper:paper-api:1.19.2-R0.1-SNAPSHOT")
        api("io.papermc.paper:paper-api:1.19.2-R0.1-SNAPSHOT")
    }
}

application {
    // Define the main class for the application.
    mainClassName = "phonon.nodes.NodesPluginKt"
}

tasks.withType<KotlinCompile> {
    kotlinOptions.freeCompilerArgs = listOf("-Xallow-result-return-type")
}

tasks {
    named<ShadowJar>("shadowJar") {
        // verify valid target minecraft version
        doFirst {
            val supportedMinecraftVersions = setOf("1.12", "1.16", "1.18", "1.19")
            System.out.println("target: ${target}")
            if ( !supportedMinecraftVersions.contains(target) ) {
                throw Exception("Invalid Minecraft version! Supported versions are: 1.12, 1.16, 1.18, 1.19")
            }
        }

        classifier = ""
        configurations = mutableListOf(project.configurations.named("resolvableImplementation").get())
        relocate("com.google", "nodes.shadow.gson")
    }
}

tasks {
    build {
        dependsOn(shadowJar)
    }
    
    test {
        testLogging.showStandardStreams = true
    }
}

gradle.taskGraph.whenReady {
    tasks {
        named<ShadowJar>("shadowJar") {
            if ( hasTask(":release") ) {
                baseName = "${OUTPUT_JAR_NAME}-${target}-${VERSION}"
                minimize() // FOR PRODUCTION USE MINIMIZE
            }
            else {
                baseName = "${OUTPUT_JAR_NAME}-${target}-SNAPSHOT-${VERSION}"
                minimize() // FOR PRODUCTION USE MINIMIZE
            }
        }
    }
}