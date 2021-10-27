import java.util.*

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.1.1")
    }
}

plugins {
    id("com.github.johnrengelman.shadow") version "7.1.0"
    id("java")
}

val platformGroup: String by project
val probeVersion: String by project
val skywalkingVersion: String by project
val jacksonVersion: String by project
val vertxVersion: String by project
val jupiterVersion: String by project
val logbackVersion: String by project

group = platformGroup
version = probeVersion

tasks.getByName<JavaCompile>("compileJava") {
    options.release.set(8)
    sourceCompatibility = "1.8"
}

dependencies {
    compileOnly(files("$projectDir/../.ext/skywalking-agent-$skywalkingVersion.jar"))
    implementation("io.vertx:vertx-tcp-eventbus-bridge:$vertxVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")
    implementation("com.github.sourceplusplus.protocol:protocol:0.1.23") {
        isTransitive = false
    }

    testImplementation("org.junit.jupiter:junit-jupiter-engine:$jupiterVersion")
    testImplementation("io.vertx:vertx-junit5:$vertxVersion")
    testImplementation("io.vertx:vertx-web-client:$vertxVersion")
    testImplementation("ch.qos.logback:logback-classic:$logbackVersion")
}

tasks.getByName<Test>("test") {
    failFast = true
    useJUnitPlatform()
    if (System.getProperty("test.profile") != "integration") {
        exclude("integration/**")
    } else {
        jvmArgs = listOf("-javaagent:../e2e/spp-probe-${project.version}.jar")
    }

    testLogging {
        events("passed", "skipped", "failed")
        setExceptionFormat("full")

        outputs.upToDateWhen { false }
        showStandardStreams = true
    }
}

//todo: shouldn't need to put in src (github actions needs for some reason)
tasks.create("createProperties") {
    if (System.getProperty("build.profile") == "release") {
        val buildBuildFile = File(projectDir, "src/main/resources/build.properties")
        if (buildBuildFile.exists()) {
            buildBuildFile.delete()
        } else {
            buildBuildFile.parentFile.mkdirs()
        }

        buildBuildFile.writer().use {
            val p = Properties()
            p["build_id"] = UUID.randomUUID().toString()
            p["build_date"] = Date().toInstant().toString()
            p["build_version"] = project.version.toString()
            p["apache_skywalking_version"] = skywalkingVersion
            p.store(it, null)
        }
    }
}
tasks["processResources"].dependsOn("createProperties")

tasks.register<Copy>("untarSkywalking") {
    dependsOn(":downloadSkywalking")
    from(tarTree(resources.gzip(File(project.rootDir, "e2e/apache-skywalking-apm-es7-$skywalkingVersion.tar.gz"))))
    into(File(project.rootDir, "e2e"))
}

tasks.register<Copy>("updateSkywalkingConfiguration") {
    dependsOn("untarSkywalking")
    from(File(projectDir, "agent.config"))
    into(File(project.rootDir, "e2e/apache-skywalking-apm-bin-es7/agent/config"))
}

tasks.register<Copy>("updateSkywalkingToolkit") {
    dependsOn("updateSkywalkingConfiguration")
    from(
        File(projectDir, "../.ext/toolkit/apm-toolkit-log4j-1.x-activation-$skywalkingVersion.jar"),
        File(projectDir, "../.ext/toolkit/apm-toolkit-log4j-2.x-activation-$skywalkingVersion.jar"),
        File(projectDir, "../.ext/toolkit/apm-toolkit-logback-1.x-activation-$skywalkingVersion.jar"),
        File(projectDir, "../.ext/toolkit/apm-toolkit-logging-common-$skywalkingVersion.jar")
    )
    into(File(project.rootDir, "e2e/apache-skywalking-apm-bin-es7/agent/activations"))
}

tasks.register<Zip>("zipSppSkywalking") {
    dependsOn("untarSkywalking", ":services:proguard", "updateSkywalkingToolkit")
    mustRunAfter(":services:proguard")

    archiveFileName.set("skywalking-agent-$skywalkingVersion.zip")
    val resourcesDir = File("$buildDir/resources/main")
    resourcesDir.mkdirs()
    destinationDirectory.set(resourcesDir)

    from(
        File(project.rootDir, "e2e/apache-skywalking-apm-bin-es7/agent"),
        File(project.rootDir, "e2e/apache-skywalking-apm-bin-es7/LICENSE"),
        File(project.rootDir, "e2e/apache-skywalking-apm-bin-es7/NOTICE")
    )

    into("plugins") {
        doFirst {
            if (!File(projectDir, "../services/build/libs/spp-skywalking-services-$version.jar").exists()) {
                throw GradleException("Missing spp-skywalking-services")
            }
        }
        from(File(projectDir, "../services/build/libs/spp-skywalking-services-$version.jar"))
    }
}
tasks["classes"].dependsOn("zipSppSkywalking")

tasks.getByName<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    onlyIf { project.tasks.getByName("build").enabled }
    dependsOn(":downloadSkywalking")

    archiveBaseName.set("spp-probe")
    archiveClassifier.set("shadow")
    exclude("module-info.class")
    exclude("META-INF/**")
    manifest {
        attributes(
            mapOf(
                "Premain-Class" to "spp.probe.SourceProbe",
                "Can-Redefine-Classes" to "true",
                "Can-Retransform-Classes" to "true"
            )
        )
    }
}
tasks.getByName("build") {
    dependsOn("shadowJar", "proguard")

    doLast {
        File("$buildDir/libs/control-$version.jar").delete()
    }
}

tasks.create<com.github.jengelman.gradle.plugins.shadow.tasks.ConfigureShadowRelocation>("relocateShadowJar") {
    target = tasks.getByName("shadowJar") as com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
    prefix = "spp.probe.common"
}

tasks.getByName("shadowJar").dependsOn("relocateShadowJar")

tasks {
    create<proguard.gradle.ProGuardTask>("proguard") {
        onlyIf { project.tasks.getByName("build").enabled }
        dependsOn("shadowJar")
        configuration("proguard.conf")
        injars(File("$buildDir/libs/spp-probe-$version-shadow.jar"))
        outjars(File("$buildDir/libs/spp-probe-$version.jar"))
        libraryjars("${org.gradle.internal.jvm.Jvm.current().javaHome}/jmods")
        libraryjars(files("$projectDir/../.ext/skywalking-agent-$skywalkingVersion.jar"))

        doLast {
            File("$buildDir/libs/spp-probe-$version-shadow.jar").delete()
        }
    }
}
