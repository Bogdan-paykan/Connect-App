plugins {
    alias(libs.plugins.android.application)
    // Якщо ви використовуєте Kotlin для Android, додайте:
    // alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.bogdan_paykan.lab9_"
    compileSdk = 35 // Ви вказали 35, переконайтеся, що це остання стабільна версія або RC

    defaultConfig {
        applicationId = "com.bogdan_paykan.lab9_"
        minSdk = 25
        targetSdk = 34 // Рекомендовано оновити до compileSdk або останньої стабільної
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // Якщо ви використовуєте Kotlin, додайте:
    // kotlinOptions {
    //     jvmTarget = "11"
    // }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity) // Ймовірно, це androidx.activity:activity-ktx або androidx.activity:activity
    implementation(libs.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout) // Ймовірно, це androidx.swiperefreshlayout:swiperefreshlayout

    // Додана залежність Gson
    implementation("com.google.code.gson:gson:2.9.0") // Ви можете використовувати новішу версію, якщо доступна
    implementation("com.vanniktech:android-image-cropper:4.6.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit) // Ймовірно, це androidx.test.ext:junit
    androidTestImplementation(libs.espresso.core)
}