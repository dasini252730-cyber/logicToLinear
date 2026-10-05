// app 모듈. AGP 9는 Kotlin이 내장되어 있어 org.jetbrains.kotlin.android 플러그인을 적용하지 않는다.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose) // Compose 컴파일러 플러그인. Kotlin 버전과 같아야 한다.
    alias(libs.plugins.kotlin.serialization) // @Serializable (백업 JSON, 알라딘·Anthropic DTO)
    alias(libs.plugins.ksp) // Room 어노테이션 처리
    alias(libs.plugins.room) // Room 스키마 내보내기 디렉터리 설정
}

room {
    schemaDirectory("$projectDir/schemas") // exportSchema 결과. 마이그레이션 테스트와 리뷰용으로 커밋한다.
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

    testOptions {
        unitTests.isIncludeAndroidResources = true // Robolectric이 Android 리소스·Context를 쓰기 위해
    }
}

// 의존성마다 이유를 한 줄 적는다. 핵심 라이브러리(T-102)는 뒤 단계가 쓸 것을 미리 등록하고, 기능별 라이브러리(카메라·OCR·HTTP·키 저장)는 해당 task에서 추가한다.
dependencies {
    implementation(libs.androidx.core.ktx) // enableEdgeToEdge 등 AndroidX 기본 확장
    implementation(libs.androidx.activity.compose) // ComponentActivity.setContent
    implementation(platform(libs.androidx.compose.bom)) // Compose 아티팩트 버전을 BOM 하나로 맞춘다
    implementation(libs.androidx.compose.ui) // Compose 런타임·Modifier 등 UI 기반
    implementation(libs.androidx.compose.ui.tooling.preview) // @Preview 어노테이션
    implementation(libs.androidx.compose.material3) // MaterialTheme, Surface, Text
    implementation(libs.androidx.compose.material.icons.core) // 하단 탭·FAB 아이콘(Home, Menu, Star, Settings, Add). material3는 아이콘을 포함하지 않고 BOM 관리도 끝나 버전 명시
    debugImplementation(libs.androidx.compose.ui.tooling) // Android Studio 미리보기 렌더링
    implementation(libs.androidx.lifecycle.viewmodel.compose) // viewModel() 주입 (Compose BOM 미포함, 버전 명시)
    implementation(libs.androidx.lifecycle.runtime.compose) // collectAsStateWithLifecycle
    implementation(libs.androidx.navigation.compose) // 하단 탭·화면 이동 (T-108)
    implementation(libs.androidx.room.runtime) // 로컬 DB. Room 2.7+는 room-ktx가 runtime에 합쳐짐
    ksp(libs.androidx.room.compiler) // Room 코드 생성
    implementation(libs.kotlinx.serialization.json) // 백업 JSON, API DTO 직렬화
    implementation(libs.kotlinx.coroutines.android) // Dispatchers.Main 등 Android 코루틴

    testImplementation(libs.junit) // 순수 로직 단위 테스트 (T-205부터)
    testImplementation(libs.kotlinx.coroutines.test) // suspend 함수·Flow 테스트
    testImplementation(libs.robolectric) // Room(SQLite) 테스트를 기기 없이 JVM에서 돌린다. hook 빌드 검증에 포함되기 위함
    testImplementation(libs.androidx.test.core.ktx) // ApplicationProvider.getApplicationContext()
    androidTestImplementation(libs.androidx.junit) // AndroidJUnit4 러너 어노테이션
    testImplementation(libs.androidx.room.testing) // Migration 테스트(MigrationTestHelper). Room 테스트는 Robolectric 한 경로로 통일
    androidTestImplementation(libs.androidx.test.runner) // testInstrumentationRunner가 가리키는 AndroidJUnitRunner 본체
}
