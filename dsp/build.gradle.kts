plugins {
    kotlin("jvm")
    id("maven-publish")
}
group = "es.sonda"
version = "0.2.0"
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}
dependencies { testImplementation("junit:junit:4.13.2") }
tasks.test { testLogging { events("passed", "skipped", "failed") } }

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["java"])
            groupId = "es.sonda"
            artifactId = "dsp"
            version = "0.2.0"
            pom {
                name.set("SONDA DSP")
                description.set("Generación de señales y procesamiento DSP determinista compartido por SONDA.")
                url.set("https://github.com/papaluis-b/Codex-gptpublico")
            }
        }
    }
    repositories {
        maven {
            name = "Kit"
            url = rootProject.layout.buildDirectory.dir("generator-kit-repository").get().asFile.toURI()
        }
    }
}
