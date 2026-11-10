    plugins {
        alias(libs.plugins.android.application)
        alias(libs.plugins.kotlin.compose)
        id("com.google.gms.google-services")
    }

    android {
        namespace = "com.registrohorario_mfm"
        compileSdk = 37

        defaultConfig {
            applicationId = "com.registrohorario_mfm"
            minSdk = 29
            targetSdk = 37
            versionCode = 8
            versionName = "1.7"

            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        buildTypes {
            release {
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
                signingConfig = signingConfigs.getByName("debug") // Fuerza la firma local compatible
            }
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }

        buildFeatures {
            compose = true
        }
    }

    dependencies {
        // Cambiamos la versión 35.1.0 (que trae dependencias con Kotlin 2.4.0) a la versión 33.9.0 (compatible con Kotlin 2.2.0)
        implementation(platform("com.google.firebase:firebase-bom:33.9.0"))
        implementation("com.google.firebase:firebase-analytics")
        implementation("com.google.firebase:firebase-auth")
        implementation("com.google.firebase:firebase-firestore")

        // El resto de tus dependencias se quedan igual...
        implementation("androidx.core:core-ktx:1.12.0")
        implementation("androidx.appcompat:appcompat:1.6.1")
        implementation("com.google.android.material:material:1.11.0")
        implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
        implementation("androidx.compose.material:material-icons-extended")

        implementation(platform(libs.androidx.compose.bom))
        implementation(libs.androidx.activity.compose)
        implementation(libs.androidx.compose.material3)
        implementation(libs.androidx.compose.ui)
        implementation(libs.androidx.compose.ui.graphics)
        implementation(libs.androidx.compose.ui.tooling.preview)

        testImplementation(libs.junit)
        androidTestImplementation(platform(libs.androidx.compose.bom))
        androidTestImplementation(libs.androidx.compose.ui.test.junit4)
        androidTestImplementation(libs.androidx.espresso.core)
        androidTestImplementation(libs.androidx.junit)
        debugImplementation(libs.androidx.compose.ui.test.manifest)
        debugImplementation(libs.androidx.compose.ui.tooling)
    }