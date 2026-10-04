import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// Single config point for the Meta App ID. Falls back to a placeholder so the
// project still configures/syncs before the user pastes their real ID.
val metaAppId: String =
    (localProps.getProperty("META_APP_ID") ?: System.getenv("META_APP_ID") ?: "REPLACE_WITH_YOUR_META_APP_ID").trim()

android {
    namespace = "com.teamc.fbbmmanager"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.teamc.fbbmmanager"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.0.0"

        manifestPlaceholders["metaAppId"] = metaAppId
        buildConfigField("String", "META_APP_ID", "\"$metaAppId\"")
        buildConfigField("String", "GRAPH_API_VERSION", "\"v26.0\"")

        multiDexEnabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        kotlinCompilerExtensionVersion = "2.0.21"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.facebook.login)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    implementation(libs.workmanager)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
}
