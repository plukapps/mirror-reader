import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt.android)
  alias(libs.plugins.google.services)
}

// Cuenta de desarrollo (K-052): se lee de local.properties, que git ignora. Solo llega al build debug.
val localProperties = Properties().apply {
    val text = providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText.orNull
    if (text != null) load(text.reader())
}
fun String.asBuildConfigString() = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.pluk.reader"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.pluk.reader"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "com.pluk.reader.HiltTestRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "DEV_ACCOUNT_EMAIL", localProperties.getProperty("dev.account.email", "").asBuildConfigString())
            buildConfigField("String", "DEV_ACCOUNT_PASSWORD", localProperties.getProperty("dev.account.password", "").asBuildConfigString())
        }
        release {
            // Sin cuenta de desarrollo en release.
            buildConfigField("String", "DEV_ACCOUNT_EMAIL", "\"\"")
            buildConfigField("String", "DEV_ACCOUNT_PASSWORD", "\"\"")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    // Los esquemas de Room alimentan las pruebas de migración.
    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Readium exige core library desugaring
  coreLibraryDesugaring(libs.desugar.jdk.libs)

  // EPUB (ADR 0004)
  implementation(libs.readium.shared)
  implementation(libs.readium.streamer)
  implementation(libs.readium.navigator)
  implementation(libs.androidx.fragment.ktx)

  // Navegación y DI (ADR 0005)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.fragment.compose)
  implementation(libs.hilt.android)
  implementation(libs.androidx.hilt.navigation.compose)
  ksp(libs.hilt.compiler)

  // Persistencia local (ADR 0005)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  implementation(libs.androidx.datastore.preferences)
  androidTestImplementation(libs.androidx.room.testing)

  // Backend (ADR 0005, ADR 0007): Auth, Firestore y Storage. Sin artefactos -ktx: el BoM 35 los incluye.
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.auth)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.storage)
  implementation(libs.kotlinx.coroutines.play.services)

  // Hilt en pruebas de emulador
  androidTestImplementation(libs.hilt.android.testing)
  kspAndroidTest(libs.hilt.compiler)
}

// El esquema de Room se versiona para poder revisar migraciones.
ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}
