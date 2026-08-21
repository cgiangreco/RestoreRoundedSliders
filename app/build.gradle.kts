plugins { id("com.android.application") }

android {
    namespace = "dev.restoreroundedsliders"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.restoreroundedsliders"
        minSdk = 35
        targetSdk = 36
        versionCode = 5
        versionName = "0.3.0-alpha"
    }
}

dependencies {
    compileOnly(files("libs/api-82.jar"))
}