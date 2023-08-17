@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android") version "1.8.21"
    `maven-publish`
    signing
}

val pkg: String = "edu.cmu.cs.sinfonia"
version = "1.0.20230818"

android {
    namespace = pkg
    compileSdk = 33

    defaultConfig {
        minSdk = 23

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        aidl = true
    }

    sourceSets["main"].aidl {
        srcDir("src/main/java")
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
    implementation("com.wireguard.android:tunnel:1.0.20230427")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.test:monitor:1.6.1")
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

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = pkg
            artifactId = "sinfonia-tier3"
            version = version
            afterEvaluate {
                from(components["release"])
            }
            pom {
                name.set("Sinfonia Tier 3 Android Library")
                description.set("Sinfonia tier 3 library for edge-native Android applications")
                developers {
                    organization {
                        name.set("Carnegie Mellon University")
                    }
                    developer {
                        name.set("Jeffrey Kuo")
                        email.set("luyok@andrew.cmu.edu")
                    }
                    developer {
                        name.set("Jan Harkes")
                        email.set("jaharkes@cs.cmu.edu")
                    }
                }
            }
        }
    }
//    repositories {
//        maven {
//            name = "sonatype"
//        }
//    }
}

signing {
    useGpgCmd()
    sign(publishing.publications)
}