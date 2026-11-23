pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        ivy {
            url = uri("https://github.com")
            patternLayout {
                artifact("/[organisation]/[module]/releases/download/v[revision]/[artifact]-[revision].[ext]")
            }
            metadataSources { artifact() }
            content { includeGroup("k2-fsa") }
        }
    }
}
rootProject.name = "PocketAgent"
include(":app")
