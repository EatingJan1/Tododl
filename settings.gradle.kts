rootProject.name = "Tododl"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":shared")
include(":desktopApp")
// include(":androidApp")   // später aktivieren
// include(":iosApp")       // iOS-App wird via Xcode-Projekt eingebunden, kein Gradle-Modul nötig
