import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)

    id("com.google.devtools.ksp")
    id("android.aop")
}

androidAopConfig {
    // enabled is false, the aspect no longer works, the default is not written as true
    enabled = true
    debug = false

    // include does not set all scans by default. After setting, only the code of the set package name will be scanned.
//    include("com.appmixer.volume")

    // exclude is the package excluded during scanning
    // Can exclude kotlin related and improve speed
    exclude(
        "kotlin.jvm",
        "kotlin.internal",
        "kotlinx.coroutines.internal",
        "kotlinx.coroutines.android"
    )
    // Exclude the entity name of the package
    excludePackaging("license/NOTICE", "license/LICENSE.dom-software.txt", "license/LICENSE")

    // verifyLeafExtends Whether to turn on verification leaf inheritance, it is turned on by default. If type = MatchType.LEAF_EXTENDS of @AndroidAopMatchClassMethod is not set, it can be turned off.
    verifyLeafExtends = true
    //Disabled by default. Enabled after Build or Packaging, a cut information file will be generated in app/build/tmp/ (cutInfo.json, cutInfo.html)
    cutInfoJson = false
}

android {
    namespace = "com.appmixer.volume"
    compileSdk = 36
    ndkVersion = "29.0.14033849"

    defaultConfig {
        applicationId = "com.appmixer.volume"
        minSdk = 33
        targetSdk = 35
        versionCode = 9
        versionName = "0.4.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing is read from environment variables rather than being
    // hardcoded, so the real keystore never has to touch the repo: CI decodes
    // the KEYSTORE_FILE secret to a file and exports these vars before
    // running `./gradlew assembleRelease`. Without them, a release build
    // still succeeds locally, just unsigned -- keeps `assembleRelease` usable
    // for anyone building from source without the signing key.
    val releaseKeystorePath = System.getenv("APPMIXER_KEYSTORE_PATH")
    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("APPMIXER_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("APPMIXER_KEY_ALIAS")
                keyPassword = System.getenv("APPMIXER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.hiddenapibypass)

    implementation(libs.androidx.datastore.core.android)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.joor)

    implementation(libs.androidaop.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.serialization.json)
    "baselineProfile"(project(":baselineprofile"))
    ksp(libs.androidaop.apt)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
