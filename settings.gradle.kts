pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven {
            setUrl("https://a8c-libs.s3.amazonaws.com/android")
            content {
                includeGroup("com.automattic.android")
                includeGroup("com.automattic.android.measure-builds")
            }
        }
    }
}

plugins {
    id("com.gradle.develocity").version("3.19")
}
apply(from = File("./config/gradle/gradle_build_cache.gradle.kts"))
apply(from = File("./config/gradle/gradle_build_scan.gradle"))

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            setUrl("https://a8c-libs.s3.amazonaws.com/android")
            content {
                includeGroup("com.automattic")
                includeGroup("com.automattic.tracks")
                includeGroupByRegex("org.wordpress.*")
            }
        }
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "pocket-casts-android"

include(":app")
include(":automotive")
include(":wear")

// features
include(":modules:features:account")
include(":modules:features:cartheme")
include(":modules:features:discover")
include(":modules:features:endofyear")
include(":modules:features:engage")
include(":modules:features:filters")
include(":modules:features:navigation")
include(":modules:features:nova")
include(":modules:features:player")
include(":modules:features:podcasts")
include(":modules:features:profile")
include(":modules:features:search")
include(":modules:features:settings")
include(":modules:features:shared")
include(":modules:features:reimagine")
include(":modules:features:referrals")
include(":modules:features:taskerplugin")
include(":modules:features:widgets")

// services
include(":modules:services:analytics")
include(":modules:services:compose")
include(":modules:services:crashlogging")
include(":modules:services:deeplink")
include(":modules:services:images")
include(":modules:services:localization")
include(":modules:services:media-noop")
include(":modules:services:model")
include(":modules:services:preferences")
include(":modules:services:protobuf")
include(":modules:services:repositories")
include(":modules:services:sharing")
include(":modules:services:servers")
include(":modules:services:ui")
include(":modules:services:utils")
include(":modules:services:views")
include(":modules:services:sharedtest")
