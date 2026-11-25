plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val numeroCompilacion = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.ismael.despierta"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ismael.despierta"
        minSdk = 26
        targetSdk = 34
        versionCode = numeroCompilacion
        versionName = "1.0.$numeroCompilacion"
    }

    // Llave fija para que cada nueva versión se instale encima de la anterior
    signingConfigs {
        create("despierta") {
            storeFile = file("despierta.keystore")
            storePassword = "despierta"
            keyAlias = "despierta"
            keyPassword = "despierta"
        }
    }

    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("despierta") }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("despierta")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("com.google.ar:core:1.41.0")
}
