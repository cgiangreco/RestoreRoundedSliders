plugins { id("com.android.application") }

android {
    namespace = "io.github.cgiangreco.restoreroundedsliders"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.cgiangreco.restoreroundedsliders"
        minSdk = 35
        targetSdk = 36
        versionCode = 6
        versionName = "1.0"
    }
}

dependencies {
    compileOnly(files("libs/api-82.jar"))
}