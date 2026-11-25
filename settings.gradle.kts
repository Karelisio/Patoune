pluginManagement {
    repositories {
        google()
        // Miroir Google de Maven Central : évite les 429 de repo.maven.apache.org sur les runners CI.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Miroir Google de Maven Central : évite les 429 de repo.maven.apache.org sur les runners CI.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}
rootProject.name = "Patoune"
include(":app")
