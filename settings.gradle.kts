pluginManagement {
    repositories {
        google() // 這樣保證包含所有 Google Maven 提供的插件
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SmartBikeSystem"
include(":app")
