plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.rayhan099.droidlink.audio"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.rayhan099.droidlink.audio"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core:1.13.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("commons-validator:commons-validator:1.9.0")
    implementation("androidx.media3:media3-session:1.4.1")
}

configurations {
    all {
        exclude(module = "commons-logging")
    }
}
