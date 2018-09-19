import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileOutputStream

plugins {
    java
    kotlin("jvm") version "1.2.61"
    application
}

group = "com.goodnighttales.xcassetmaster"
version = "0.1"

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    compile(kotlin("stdlib-jdk8"))
    compile("com.beust", "klaxon", "3.0.1")
    compile("com.github.ajalt", "clikt", "1.5.0")
    compile("com.github.depsypher", "pngtastic", "1.5")
    testCompile("junit", "junit", "4.12")
}

application {
    mainClassName = "com.goodnighttales.xcassetmaster.XCAssetMasterKt"
    applicationDefaultJvmArgs = listOf(
            "-Djava.awt.headless=true"
    )
}

val fatJar = task("fatJar", type = Jar::class) {
    baseName="${project.name}-fat"
    manifest {
        attributes["Main-Class"] = "com.goodnighttales.xcassetmaster.XCAssetMasterKt"
        attributes["Implementation-Title"] = "XCAssetMaster"
        attributes["Implementation-Version"] = version
    }
    from(configurations.runtime.map { if(it.isDirectory) it else zipTree(it) })
    with(tasks["jar"] as CopySpec)
}

val createExecutable = task("createExecutable") {
    dependsOn(fatJar)
    val input = fatJar.outputs.files.singleFile
    val output = project.buildDir.resolve("libs/${project.name}-${project.version}")
    doLast {
        FileOutputStream(output).use { outputStream ->
            exec {
                standardOutput = outputStream
                commandLine("cat", "src/launcher-stub.sh", input.absolutePath)
            }
        }
        exec {
            commandLine("chmod", "+x", output.absolutePath)
        }
    }
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks["build"].dependsOn(createExecutable)
