plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android") version "1.9.22"
}

android {
    namespace = "com.xppen.inputmapper"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.xppen.inputmapper"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // libadb-android - Wireless Debugging (adb pair + connect) in pure JVM
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")

    // Custom Conscrypt provider so libadb can do TLSv1.3/pairing without hidden-API bypass
    implementation("org.conscrypt:conscrypt-android:2.5.3")

    // sun.security.x509 classes to generate the X.509 client certificate ADB pairing requires
    implementation("com.github.MuntashirAkon:sun-security-android:1.1")

    // Shizuku API (kept as secondary fallback)
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

