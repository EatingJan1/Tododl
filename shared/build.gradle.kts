plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("app.cash.sqldelight")
}

kotlin {
    jvm("desktop")

    // Später einfach auskommentieren, sobald Xcode/iOS-Targets gebraucht werden:
    // iosX64()
    // iosArm64()
    // iosSimulatorArm64()

    // androidTarget() // sobald androidApp-Modul aktiviert wird

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("app.cash.sqldelight:coroutines-extensions:2.0.2")
                implementation("io.insert-koin:koin-core:4.0.0")
                implementation("io.ktor:ktor-client-core:2.3.12")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
                implementation("io.ktor:ktor-client-logging:2.3.12")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation("app.cash.sqldelight:sqlite-driver:2.0.2")
                implementation("io.ktor:ktor-client-cio:2.3.12")
            }
        }
    }
}

sqldelight {
    databases {
        create("TododlDatabase") {
            packageName.set("de.tododl.shared.db")
        }
    }
}
