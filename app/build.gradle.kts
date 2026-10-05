// app 모듈. AGP 9는 Kotlin이 내장되어 있어 org.jetbrains.kotlin.android 플러그인을 적용하지 않는다.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose) // Compose 컴파일러 플러그인. Kotlin 버전과 같아야 한다.
}

android {
    namespace = "com.logiclinear.reading"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.logiclinear.reading"
        minSdk = 26 // 요구사항: minSdk 26 이상. T-101에서 26으로 확정.
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// 의존성마다 이유를 한 줄 적는다. 이 task 범위(빈 Compose 화면)에서 쓰이는 것만 넣는다.
dependencies {
    implementation(libs.androidx.core.ktx) // enableEdgeToEdge 등 AndroidX 기본 확장
    implementation(libs.androidx.activity.compose) // ComponentActivity.setContent
    implementation(platform(libs.androidx.compose.bom)) // Compose 아티팩트 버전을 BOM 하나로 맞춘다
    implementation(libs.androidx.compose.ui) // Compose 런타임·Modifier 등 UI 기반
    implementation(libs.androidx.compose.ui.tooling.preview) // @Preview 어노테이션
    implementation(libs.androidx.compose.material3) // MaterialTheme, Surface, Text
    debugImplementation(libs.androidx.compose.ui.tooling) // Android Studio 미리보기 렌더링

    testImplementation(libs.junit) // 순수 로직 단위 테스트 (T-205부터)
    androidTestImplementation(libs.androidx.junit) // AndroidJUnit4 러너 어노테이션
    androidTestImplementation(libs.androidx.test.runner) // testInstrumentationRunner가 가리키는 AndroidJUnitRunner 본체
}
