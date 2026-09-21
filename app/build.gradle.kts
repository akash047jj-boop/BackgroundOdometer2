plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {

    namespace =
        "com.example.backgroundodometer"

    compileSdk =
        35

    defaultConfig {

        applicationId =
            "com.example.backgroundodometer"

        minSdk =
            26

        targetSdk =
            35

        versionCode =
            17

        versionName =
            "17.0"

        buildConfigField(
            "String",
            "CARTO_API_KEY",
            "\"${System.getenv("CARTO_API_KEY") ?: ""}\""
        )
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {

        sourceCompatibility =
            JavaVersion.VERSION_17

        targetCompatibility =
            JavaVersion.VERSION_17
    }

    kotlinOptions {

        jvmTarget =
            "17"
    }

    signingConfigs {

        create("release") {

            val keystoreFile =
                rootProject.file(
                    "background-odometer-release.jks"
                )

            if (
                keystoreFile.exists()
            ) {

                storeFile =
                    keystoreFile

                storePassword =
                    System.getenv(
                        "KEYSTORE_PASSWORD"
                    )

                keyAlias =
                    System.getenv(
                        "KEY_ALIAS"
                    )

                keyPassword =
                    System.getenv(
                        "KEY_PASSWORD"
                    )
            }
        }
    }

    buildTypes {

        release {

            isMinifyEnabled =
                false

            signingConfig =
                signingConfigs.getByName(
                    "release"
                )
        }
    }
}

dependencies {

    implementation(
        "androidx.core:core-ktx:1.15.0"
    )

    implementation(
        "androidx.appcompat:appcompat:1.7.0"
    )

    implementation(
        "androidx.drawerlayout:drawerlayout:1.2.0"
    )

    implementation(
        "com.google.android.material:material:1.12.0"
    )

    implementation(
        "com.google.android.gms:play-services-location:21.3.0"
    )

    implementation(
        "org.osmdroid:osmdroid-android:6.1.20"
    )
}
