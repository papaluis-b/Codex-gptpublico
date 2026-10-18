val kitRepository = providers.gradleProperty("sondaRepository").orNull
    ?.let { file(it) } ?: file("../maven-repository")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri(kitRepository)
            content { includeGroup("es.sonda") }
        }
        mavenCentral()
    }
}
rootProject.name = "SondaDspHost"
