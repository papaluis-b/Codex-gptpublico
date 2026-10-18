pluginManagement {
    repositories {
        maven { url = uri("https://dl.google.com/dl/android/maven2/") }
        mavenCentral()
        gradlePluginPortal()
    }
}

val kitRepository = providers.gradleProperty("sondaRepository").orNull
    ?.let { file(it) } ?: file("../maven-repository")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            name = "SondaKit"
            url = uri(kitRepository)
            content { includeGroup("es.sonda") }
        }
        maven { url = uri("https://dl.google.com/dl/android/maven2/") }
        mavenCentral()
    }
}
rootProject.name = "SondaGeneratorHost"
include(":app")
