plugins { id("com.android.application") }

val signingEnvironment = listOf("ANDROID_KEYSTORE_PATH", "ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD")
val signingValues = signingEnvironment.associateWith { providers.environmentVariable(it).orNull }
val hasReleaseSigning = signingValues.values.all { !it.isNullOrBlank() }

android {
    namespace = "dev.learnthenwatch.tv"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "dev.learnthenwatch.tv"
        minSdk = 26
        targetSdk = 28
        versionCode = providers.gradleProperty("appVersionCode").get().toInt()
        versionName = providers.gradleProperty("appVersionName").get()
        testInstrumentationRunner = "dev.learnthenwatch.tv.LearningTestRunner"
    }
    buildFeatures { buildConfig = true }
    // Distributed directly as a TV APK. Preserve the tested target during this refactor;
    // the Google Play target deadline does not apply to this distribution channel.
    lint { disable += "ExpiredTargetSdkVersion" }
    if (hasReleaseSigning) {
        signingConfigs.create("release") {
            storeFile = file(signingValues.getValue("ANDROID_KEYSTORE_PATH")!!)
            storePassword = signingValues.getValue("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = signingValues.getValue("ANDROID_KEY_ALIAS")
            keyPassword = signingValues.getValue("ANDROID_KEY_PASSWORD")
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    testOptions.unitTests.all {
        it.systemProperty("learnthenwatch.assetsDir", file("src/main/assets").absolutePath)
        it.testLogging { events("passed", "skipped", "failed"); showStandardStreams = true }
    }
}

dependencies { testImplementation("junit:junit:4.13.2") }

// Release packaging must never silently fall back to a debug or unsigned APK.
tasks.matching { it.name == "validateSigningRelease" || it.name == "packageRelease" }.configureEach {
    doFirst {
        check(hasReleaseSigning) { "Release signing requires: ${signingEnvironment.joinToString()}. See docs/BUILDING.md." }
    }
}
