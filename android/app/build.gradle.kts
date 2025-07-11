plugins {
    id("com.android.application")
    id("kotlin-android")
    id("com.google.gms.google-services") // Firebase Plugin
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.techfifo.crm"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = "27.0.12077973"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }

    defaultConfig {
        applicationId = "com.techfifo.crm"
        minSdk = 23
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

flutter {
    source = "../.."
}

dependencies {
    implementation("com.google.firebase:firebase-storage-ktx:20.3.0")
    implementation("com.google.firebase:firebase-auth-ktx:22.3.1")
    implementation("com.google.firebase:firebase-common-ktx:20.4.2")
    implementation("com.google.firebase:firebase-firestore-ktx:24.7.1") // ✅ Firestore required
}

