import java.util.Properties
import org.gradle.kotlin.dsl.annotationProcessor
import org.gradle.kotlin.dsl.implementation
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
}

kotlin {
	compilerOptions {
		jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
	}
}
val localAdvProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun Project.advStringProperty(name: String, fallback: String): String {
    val fromGradle = providers.gradleProperty(name).orNull
    if (!fromGradle.isNullOrBlank()) return fromGradle
    val fromEnv = providers.environmentVariable(name).orNull
    if (!fromEnv.isNullOrBlank()) return fromEnv
    val fromLocal = localAdvProperties.getProperty(name)
    return fromLocal?.takeIf { it.isNotBlank() } ?: fallback
}

fun Project.advStringProperty(propertyName: String, envName: String, fallback: String): String {
    val fromGradle = providers.gradleProperty(propertyName).orNull
    if (!fromGradle.isNullOrBlank()) return fromGradle
    val fromLocal = localAdvProperties.getProperty(propertyName)
    if (!fromLocal.isNullOrBlank()) return fromLocal
    val fromEnv = providers.environmentVariable(envName).orNull
    if (!fromEnv.isNullOrBlank()) return fromEnv
    val fromLegacyLocal = localAdvProperties.getProperty(envName)
    return fromLegacyLocal?.takeIf { it.isNotBlank() } ?: fallback
}

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n")}\""



android {
    namespace = "com.tidyclean.storagecleaner"
    compileSdk = 37
    packagingOptions {
        merge ("META-INF/DEPENDENCIES")
    }
    defaultConfig {
        applicationId = "com.tidyclean.storagecleaner"
        minSdk = 29
        targetSdk = 36
        versionCode = 5
        versionName = "1.0.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val advAdmobAppId = advStringProperty("ADV_ADMOB_APP_ID", "ca-app-pub-8648689202972940~2236822030")
        buildConfigField("String", "ADV_ADMOB_APP_ID", advAdmobAppId.asBuildConfigString())
        buildConfigField("String", "ADV_ADMOB_BANNER_ID", advStringProperty("ADV_ADMOB_BANNER_ID", "").asBuildConfigString())
        buildConfigField("String", "ADV_ADMOB_INTERSTITIAL_ID", advStringProperty("ADV_ADMOB_INTERSTITIAL_ID", "ca-app-pub-8648689202972940/2537958955").asBuildConfigString())
        buildConfigField("String", "ADV_ADMOB_OPEN_ID", advStringProperty("ADV_ADMOB_OPEN_ID", "ca-app-pub-8648689202972940/7928132416").asBuildConfigString())
        buildConfigField("String", "ADV_ADMOB_NATIVE_ID", advStringProperty("ADV_ADMOB_NATIVE_ID", "").asBuildConfigString())
        buildConfigField("String", "ADV_ADMOB_REWARDED_ID", advStringProperty("ADV_ADMOB_REWARDED_ID", "").asBuildConfigString())
        buildConfigField("String", "ADV_DEFAULT_TOPIC", advStringProperty("ADV_DEFAULT_TOPIC", "tidyclean-phone-debug").asBuildConfigString())
        buildConfigField("String", "ADV_AD_DEBUG_OVERRIDE_MODE", advStringProperty("adv.adDebugOverride.mode", "ADV_AD_DEBUG_OVERRIDE_MODE", "POLICY").asBuildConfigString())
        buildConfigField(
            "String",
            "ADV_REMOTE_CONFIG_ENCRYPTION_KEY",
            advStringProperty("ADV_REMOTE_CONFIG_ENCRYPTION_KEY", "w+exfdwsvVMuktvx2yao4AvAcWSgtgB4iRn5Xv8bZOw=").asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "ADV_REMOTE_CONFIG_ENCRYPTION_KEY_ID",
            advStringProperty("ADV_REMOTE_CONFIG_ENCRYPTION_KEY_ID", "rc_2026_10_09_17_38_35").asBuildConfigString(),
        )
        val advFacebookAppId = advStringProperty("ADV_FACEBOOK_APP_ID", "")
        val advFacebookClientToken = advStringProperty("ADV_FACEBOOK_CLIENT_TOKEN", "")
        buildConfigField("String", "ADV_FACEBOOK_APP_ID", advFacebookAppId.asBuildConfigString())
        buildConfigField("String", "ADV_FACEBOOK_CLIENT_TOKEN", advFacebookClientToken.asBuildConfigString())

        manifestPlaceholders["advAdmobAppId"] = advAdmobAppId
        manifestPlaceholders["advFacebookAppId"] = advFacebookAppId
        manifestPlaceholders["advFacebookClientToken"] = advFacebookClientToken
    }
    signingConfigs {
        create("release") {
            storeFile = file("tidyclean.jks")
            storePassword ="tidyclean2026"
            keyAlias = "key0"
            keyPassword = "key02026"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isDebuggable=false
            isMinifyEnabled = true
            isShrinkResources=true
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
        viewBinding = true
        buildConfig = true
    }
}
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.glide)
    annotationProcessor(libs.compiler)
    implementation(libs.baserecyclerviewadapterhelper)
    api(libs.androidx.lifecycle.extensions)
    api(libs.androidx.lifecycle.viewmodel)
    api(libs.androidx.lifecycle.livedata)
    implementation(libs.eventbus)
    implementation(libs.androidx.databinding.runtime)
    implementation(libs.app.update)
    implementation(libs.app.update.ktx)
    implementation(libs.roundedimageview)
    implementation(libs.photoview)
    implementation(libs.gson)
    implementation(libs.lottie)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.okhttp)
    testImplementation(libs.okhttp.mockwebserver)
    implementation(project(":impl"))
    implementation(project(":core"))
    debugImplementation(project(":debug"))
    implementation(platform("com.google.firebase:firebase-bom:34.1.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics-ndk")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    implementation("com.google.android.play:integrity:1.6.0")
}
