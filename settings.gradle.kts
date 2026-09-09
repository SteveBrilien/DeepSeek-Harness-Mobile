pluginManagement {
    val localMavenProxy = (System.getProperty("DSHM_MAVEN_PROXY_URL")
        ?: System.getenv("DSHM_MAVEN_PROXY_URL"))
        ?.trimEnd('/')
        ?.takeIf { it.isNotBlank() }
    println("[settings] plugin proxy=${localMavenProxy ?: "direct"}")
    repositories {
        if (localMavenProxy != null) {
            maven("$localMavenProxy/google") {
                isAllowInsecureProtocol = true
                content {
                    includeGroupByRegex("androidx\\..*")
                    includeGroupByRegex("com\\.android.*")
                    includeGroupByRegex("com\\.google\\..*")
                }
            }
            maven("$localMavenProxy/maven") { isAllowInsecureProtocol = true }
            maven("$localMavenProxy/plugins") { isAllowInsecureProtocol = true }
        } else {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
}

dependencyResolutionManagement {
    val localMavenProxy = (System.getProperty("DSHM_MAVEN_PROXY_URL")
        ?: System.getenv("DSHM_MAVEN_PROXY_URL"))
        ?.trimEnd('/')
        ?.takeIf { it.isNotBlank() }
    println("[settings] dependency proxy=${localMavenProxy ?: "direct"}")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (localMavenProxy != null) {
            maven("$localMavenProxy/google") {
                isAllowInsecureProtocol = true
                content {
                    includeGroupByRegex("androidx\\..*")
                    includeGroupByRegex("com\\.android.*")
                    includeGroupByRegex("com\\.google\\..*")
                }
            }
            maven("$localMavenProxy/maven") { isAllowInsecureProtocol = true }
        } else {
            google()
            mavenCentral()
        }
    }
}

rootProject.name = "DeepSeekHarnessMobile"

include(
    ":app",
    ":core:model",
    ":core:recovery",
    ":core:runtime-api",
    ":core:runtime-android",
    ":core:privilege-api",
    ":core:dsh-api",
    ":core:plugin-api",
)
