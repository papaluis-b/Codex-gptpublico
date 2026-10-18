plugins {
    id("com.android.library")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("maven-publish")
}

group = "es.sonda"
version = "0.2.0"

android {
    namespace = "es.sonda.generador.kit"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    lint { abortOnError = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    publishing { singleVariant("release") { withSourcesJar() } }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    api(project(":dsp"))
    api(platform("androidx.compose:compose-bom:2025.10.01"))
    api("androidx.compose.runtime:runtime")
    api("androidx.compose.ui:ui")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation(platform("androidx.compose:compose-bom:2025.10.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
}

tasks.withType<Test>().configureEach {
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
    testLogging { events("passed", "skipped", "failed") }
}

publishing {
    repositories {
        maven {
            name = "Kit"
            url = rootProject.layout.buildDirectory.dir("generator-kit-repository").get().asFile.toURI()
        }
    }
}

afterEvaluate {
    publishing.publications.create<MavenPublication>("release") {
        from(components["release"])
        groupId = "es.sonda"
        artifactId = "generator-kit"
        version = "0.2.0"
        pom {
            name.set("SONDA Generator Kit")
            description.set("Biblioteca Android con el generador de audio y los controles Compose de SONDA.")
            url.set("https://github.com/papaluis-b/Codex-gptpublico")
        }
    }
}
