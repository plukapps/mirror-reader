plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt.android)
  alias(libs.plugins.google.services)
  alias(libs.plugins.firebase.crashlytics)
}

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

    // Entornos (AND-007, AND-008, ADR 0020). Lo que cambia por entorno se declara solo aquí; cada uno se
    // construye con un solo tipo de build (ENVIRONMENT_BUILD_TYPE).
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            environment(name = "dev", authLinkHost = "mirror-reading-staging.firebaseapp.com")
        }
        create("stage") {
            dimension = "environment"
            applicationIdSuffix = ".stage"
            environment(name = "stage", authLinkHost = "mirror-reading-staging.firebaseapp.com")
        }
        create("beta") {
            dimension = "environment"
            applicationIdSuffix = ".beta"
            environment(name = "beta", authLinkHost = "mirror-reading-staging.firebaseapp.com")
            // Sin clave de release todavía: se firma con la de debug para poder instalarla (ADR 0020).
            signingConfig = signingConfigs.getByName("debug")
        }
        create("prod") {
            dimension = "environment"
            environment(name = "prod", authLinkHost = "mirror-reading-staging.firebaseapp.com")
        }
    }

    buildTypes {
        release {
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
      resValues = true
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

/** Tipo de build de cada entorno (AND-007, ADR 0020): las demás combinaciones no se generan. */
val ENVIRONMENT_BUILD_TYPE = mapOf("dev" to "debug", "stage" to "debug", "beta" to "release", "prod" to "release")

androidComponents {
    beforeVariants { variant ->
        val environment = variant.productFlavors.single { it.first == "environment" }.second
        variant.enable = ENVIRONMENT_BUILD_TYPE.getValue(environment) == variant.buildType
    }
}

/**
 * Valores de un entorno (ADR 0020). `authLinkHost`: página de acción de Firebase de los emails de la cuenta
 * (ONB-007, ONB-018, ADR 0014).
 */
fun com.android.build.api.dsl.ApplicationProductFlavor.environment(name: String, authLinkHost: String) {
    buildConfigField("String", "ENVIRONMENT", "\"$name\"")
    manifestPlaceholders["authLinkHost"] = authLinkHost
    resValue("string", "auth_link_host", authLinkHost)
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
  // `org.json` de verdad en los tests JVM: el de Android es un stub fuera del dispositivo (catálogo de planes, K-302).
  testImplementation(libs.org.json)

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
  // ACC-011, ADR 0016: funciones que se prenden y apagan con Remote Config.
  implementation(libs.firebase.config)
  // CMP-010, ADR 0018: cierres inesperados.
  implementation(libs.firebase.crashlytics)
  implementation(libs.kotlinx.coroutines.play.services)

  // Ingreso con Google (ADR 0014)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services.auth)
  implementation(libs.googleid)

  // Hilt en pruebas de emulador
  androidTestImplementation(libs.hilt.android.testing)
  kspAndroidTest(libs.hilt.compiler)
}

// El esquema de Room se versiona para poder revisar migraciones.
ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}
