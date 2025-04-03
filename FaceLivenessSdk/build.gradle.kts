import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.pha.liveness.face.liveness.sdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 22
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        val properties = Properties().apply { rootProject.file("local.properties").reader().use(::load) }
        debug {
            isMinifyEnabled = false
            buildConfigField("String", "occlusion", properties["occlusion"] as String)
            buildConfigField("String", "liveness", properties["liveness"] as String)
            buildConfigField("String", "mask_label", properties["mask_label"] as String)
            buildConfigField("String", "mask_detector", properties["mask_detector"] as String)
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release {
            isMinifyEnabled = true
            buildConfigField("String", "occlusion", properties["occlusion"] as String)
            buildConfigField("String", "liveness", properties["liveness"] as String)
            buildConfigField("String", "mask_label", properties["mask_label"] as String)
            buildConfigField("String", "mask_detector", properties["mask_detector"] as String)
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures.buildConfig = true
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.face.detection)
    implementation(libs.onnxruntime.android)

    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.gpu)
    implementation(libs.tensorflow.lite.support)
}