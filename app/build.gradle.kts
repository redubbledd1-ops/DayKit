plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp") version "1.9.24-1.0.20"
}



android {
    namespace = "com.dd.daykit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dd.daykit"
        minSdk = 26
        targetSdk = 35

        versionCode = 20
        versionName = "20"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Temporary v1 portrait-only gate. Change this single value to "unspecified"
        // when landscape support is ready to be re-enabled across the app.
        manifestPlaceholders["appScreenOrientation"] = "portrait"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))

    implementation("androidx.activity:activity-compose:1.9.2")

    // Niet direct gebruikt (de app is volledig Compose/ComponentActivity), maar
    // play-services sleept androidx.fragment:fragment:1.0.0 mee. Die versie
    // laat lint de release-build afkeuren met InvalidFragmentVersionForActivityResult,
    // omdat de ActivityResult-API's fragment >= 1.3.0 vereisen.
    implementation("androidx.fragment:fragment:1.8.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material-ripple")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Coil
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-gif:2.5.0")

    // Retrofit / OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0") // Changed from debugImplementation to implementation

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Lifecycle
    val lifecycleVersion = "2.6.2"
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-process:$lifecycleVersion")

    // WorkManager — periodic background calendar sync
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // NanoHTTPD
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // QR-scanner voor HA-koppeling (setup-code/QR pairing flow)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // GPS locatie voor Weer-module
    implementation("com.google.android.gms:play-services-location:21.0.1")

    testImplementation("junit:junit:4.13.2")
}
