import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.vrgames.clockshop"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vrgames.clockshop"
        minSdk = 35
        targetSdk = 35
        versionCode = 3
        versionName = "0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters.add("arm64-v8a") }
    }

    // The shared VRGames release key lives outside the repo (see signing/README.md in the workspace).
    // Without it, release builds are unsigned, so the open-source code still builds anywhere.
    val signingProps = file(System.getenv("VRGAMES_SIGNING") ?: "../../../../signing/keystore.properties")
    val releaseSigning = if (signingProps.isFile) {
        val p = Properties().apply { signingProps.inputStream().use { load(it) } }
        signingConfigs.create("release") {
            storeFile = signingProps.resolveSibling(p.getProperty("storeFile"))
            storePassword = p.getProperty("storePassword")
            keyAlias = p.getProperty("keyAlias")
            keyPassword = p.getProperty("keyPassword")
        }
    } else null

    buildTypes {
        release {
            signingConfig = releaseSigning
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.spatial.bom))
    implementation(libs.spatial.core)
    implementation(libs.spatial.ui.platform)
    implementation(libs.spatial.ui.foundation)
    implementation(libs.spatial.ui.design)
    implementation(libs.spatial.ui.sense)
    implementation(libs.spatial.ui.tracking)
    implementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.ui.tooling.preview)
}

configurations.all {
    resolutionStrategy {
        exclude("androidx.compose.ui", "ui")
        exclude("androidx.compose.ui", "ui-graphics")
        exclude("androidx.compose.ui", "ui-text")
        exclude("androidx.compose.foundation", "foundation")
    }
}

tasks.withType<Test>().configureEach {
    // Pass -Dlab=true to run the design labs (puzzle searches and reports).
    systemProperty("lab", System.getProperty("lab") ?: "false")
    for (k in listOf("seed", "runs", "key")) System.getProperty(k)?.let { systemProperty(k, it) }
    maxHeapSize = "512m"
    testLogging { showStandardStreams = true }
}
