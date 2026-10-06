plugins { id("com.android.application") }
android {
    namespace = "com.zeyol.hideeverything"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.zeyol.hideeverything"
        minSdk = 29; targetSdk = 35
        versionCode = 1; versionName = "0.1"
    }
    buildTypes { release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("debug") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { compileOnly("de.robv.android.xposed:api:82") }
