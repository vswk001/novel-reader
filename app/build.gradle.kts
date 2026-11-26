import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// 检查更新源：发布构建时通过 -PupdateFeedUrl=... 或环境变量 UPDATE_FEED_URL 注入
// （不写死在代码/资源中）；未提供时回退到内置默认。
val updateFeedUrl: String =
    (project.findProperty("updateFeedUrl") as String?)
        ?: System.getenv("UPDATE_FEED_URL")
        ?: "https://api.github.com/repos/vswk001/novel-reader/releases/latest"

// 签名配置：根目录存在 keystore.properties 时启用（文件不入库）
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.moyue.reader"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.moyue.reader"
        minSdk = 24
        targetSdk = 36
        versionCode = 45
        versionName = "1.12.14"

        // 检查更新源（构建期注入，运行时经 BuildConfig.UPDATE_FEED_URL 读取）
        buildConfigField("String", "UPDATE_FEED_URL", "\"$updateFeedUrl\"")

        // 更新镜像前缀（逗号分隔，直连失败时依次回退；-PupdateMirrors 或 UPDATE_MIRRORS 覆盖）
        val updateMirrors: String =
            (project.findProperty("updateMirrors") as String?)
                ?: System.getenv("UPDATE_MIRRORS")
                ?: "https://ghproxy.net/,https://ghfast.top/"
        buildConfigField("String", "UPDATE_MIRRORS", "\"$updateMirrors\"")
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// ---- 按 @FlakyOnCi 分组的单元测试任务 ----
// 背景：带该注解的 Robolectric + Compose 全链路测试在 CI 的慢 runner 上会因测试框架的
// 空闲等待时序缺陷而超时（见 FlakyOnCi），与被测业务逻辑无关。
//
//   testStableDebugUnitTest   稳定集 —— 失败即失败；CI 与发版流水线用它把关
//   testFlakyDebugUnitTest    不稳定集 —— 仍然运行并产出报告，但失败不阻断
//
// 分组完全由源码里的注解决定（自动扫描），无需维护类名清单：新增测试默认属于稳定集，
// 要放进不稳定集必须在类上显式加 @FlakyOnCi。
// 默认任务 testDebugUnitTest 的行为不变：本地运行全部测试并正常阻塞。
//
// 两个任务只改【测试过滤】，其余（类目录、classpath、JVM 参数等）全部沿用默认测试任务，
// 避免与 AGP 的默认行为产生偏差。

val flakyTestClasses: List<String> by lazy {
    val root = layout.projectDirectory.dir("src/test/java").asFile
    if (!root.isDirectory) emptyList() else root.walkTopDown()
        .filter { it.isFile && it.name.endsWith(".kt") && it.readText().contains("@FlakyOnCi") }
        .map { f -> f.relativeTo(root).invariantSeparatorsPath.removeSuffix(".kt").replace('/', '.') }
        .sorted()
        .toList()
}

fun Test.onlyRunFlaky(flaky: Boolean) {
    val defaultTest = tasks.named<Test>("testDebugUnitTest")
    // 惰性取值：等默认测试任务被 AGP 配置完成后再读，避免拿到空的类目录
    testClassesDirs = files(provider { defaultTest.get().testClassesDirs })
    classpath = files(provider { defaultTest.get().classpath })
    val classes = flakyTestClasses
    filter {
        classes.forEach { if (flaky) includeTestsMatching(it) else excludeTestsMatching(it) }
        // 稳定集必须真的有测试在跑：一个都没匹配到视为配置错误，而不是“全绿”。
        // 不稳定集在没有任何 @FlakyOnCi 类时允许为空（空跑即通过）。
        setFailOnNoMatchingTests(!flaky)
    }
    if (flaky) ignoreFailures = true
    doFirst {
        logger.lifecycle("test groups (@FlakyOnCi): {}", classes.joinToString())
    }
}

tasks.register<Test>("testStableDebugUnitTest") {
    group = "verification"
    description = "稳定集单元测试（不含 @FlakyOnCi 类）：失败即失败"
    onlyRunFlaky(flaky = false)
}

tasks.register<Test>("testFlakyDebugUnitTest") {
    group = "verification"
    description = "不稳定集单元测试（@FlakyOnCi 类）：运行并报告，但失败不阻断"
    onlyRunFlaky(flaky = true)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
