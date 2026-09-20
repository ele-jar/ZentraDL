pluginManagement {
    repositories {
        google { content { includeGroupAndSubgroups("androidx") ; includeGroupAndSubgroups("com.android") ; includeGroup("com.google.dagger") } }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "ZentraDL"
include(":android:app", ":android:engine", ":android:designsystem")
project(":android:app").name = "app"
project(":android:engine").name = "engine"
project(":android:designsystem").name = "designsystem"
