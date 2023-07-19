@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android") version "1.8.21"
}

val pkg: String = "edu.cmu.cs.sinfonia"

android {
    namespace = pkg
    compileSdk = 33

    defaultConfig {
        minSdk = 23

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {

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
    implementation(project(":tunnel"))
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    implementation(platform("org.http4k:http4k-bom:4.48.0.0"))
    implementation("org.http4k:http4k-core:4.48.0.0")
    implementation("org.http4k:http4k-client-okhttp:4.48.0.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.14.1")
    implementation("androidx.security:security-crypto:1.0.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}