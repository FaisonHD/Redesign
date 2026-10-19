plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "de.simonhub.data"; compileSdk = 35; buildToolsVersion = "35.0.0"
    defaultConfig { minSdk = 28 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation(project(":core")); implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0") }
