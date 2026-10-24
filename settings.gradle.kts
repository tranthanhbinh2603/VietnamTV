// Auto-configure Android SDK if local.properties is missing
val localPropertiesFile = file("local.properties")
if (!localPropertiesFile.exists()) {
    val envSdk = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    val candidatePaths = listOfNotNull(
        envSdk,
        "C:/Android/sdk",
        "${System.getProperty("user.home")}/AppData/Local/Android/Sdk"
    )
    for (path in candidatePaths) {
        val sdkDir = file(path)
        if (sdkDir.exists()) {
            localPropertiesFile.writeText("sdk.dir=${sdkDir.absolutePath.replace('\\', '/')}\n")
            break
        }
    }
}

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "TVLive"
include(":app")
