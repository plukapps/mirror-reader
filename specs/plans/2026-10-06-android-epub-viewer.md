# Android — Visor de EPUB: plan de implementación

> **Para agentes:** ejecutar este plan tarea por tarea con los skills del proyecto `incremental-implementation` y `test-driven-development` (ver "Skills preferidos" en `AGENTS.md`), y `source-driven-development` para verificar la API de Readium contra su documentación. Los pasos usan casillas `- [ ]`. Además, seguir el tablero Kanban de `KANBAN.md`.

**Objetivo:** app Android que abre un EPUB (desde el selector de archivos o "Abrir con") y lo muestra con lectura paginada o scroll, tabla de contenidos, progreso, ajustes básicos y posición recordada.

**Arquitectura:** app Kotlin en `code/android`, UI con Compose para pantallas y controles, y el navegador de EPUB de Readium (un `Fragment`) alojado en una `FragmentActivity`. Esta rebanada no tiene biblioteca, cuenta ni nube: la posición y los ajustes se guardan localmente. Es la primera mitad de la rebanada 1 de `specs/roadmap.md` (el lector); la biblioteca local viene en un plan posterior.

**Tecnología:** Kotlin, Jetpack Compose (Material 3), Readium Kotlin Toolkit 3.4.0 (`readium-shared`, `readium-streamer`, `readium-navigator`), Gradle con catálogo de versiones, JUnit 4 para pruebas JVM, AndroidX Test para pruebas instrumentadas en emulador.

**Specs que implementa:** `specs/product/02-reader.md` (RDR-001, 002 parcial, 003, 004, 005, 006 local, 007), `specs/product/01-library.md` (LIB-001 parcial, LIB-002 al abrir), `specs/platforms/android.md` (AND-001, AND-003 parcial), `specs/adr/0001-native-clients.md`, `specs/adr/0004-epub-engine-readium.md`.

## Restricciones globales

- Kotlin nativo en `code/android`; namespace y applicationId `com.pluk.reader`.
- `minSdk 26` (decisión provisional que cierra la pregunta abierta #3; confirmar al revisar este plan).
- Motor de EPUB: Readium Kotlin Toolkit **3.4.0** (ADR 0004, provisional).
- Solo EPUB sin DRM. Un EPUB corrupto o con DRM debe mostrar un mensaje claro y no cerrar la app (LIB-002).
- La lectura funciona sin conexión (RDR-007). Esta rebanada no usa red.
- Texto de la interfaz en español.
- El código y los tests referencian el ID del requisito, por ejemplo `// RDR-006`.
- Cada tarea del plan es una tarjeta del `KANBAN.md`.
- La posición se guarda por libro. En esta rebanada la clave es `publication.metadata.identifier` o, si falta, la URI. La identidad por hash de contenido (LIB-003) llega con la biblioteca.

## Foco de revisión

Casos que los specs implican y es fácil romper. Cada uno tiene su prueba en la tarea indicada.

1. EPUB corrupto o archivo que no es EPUB: mensaje claro, sin crash (Tarea 2).
2. Rotación o recreación de la actividad en plena lectura: el lector sigue mostrado y no se pierde la posición (Tarea 3).
3. Libro sin tabla de contenidos: el botón no rompe y se informa que no hay (Tarea 5).
4. Cerrar el libro y reabrirlo: vuelve a la misma posición (Tarea 6).
5. URI sin permiso o permiso revocado: error comprensible (Tarea 2).

---

## Estructura de archivos

```
code/android/
├── app/src/main/java/com/pluk/reader/
│   ├── MainActivity.kt                  pantalla inicial: botón "Abrir EPUB"
│   └── reader/
│       ├── PublicationLoader.kt         URI → Publication (Readium)
│       ├── ReaderSettings.kt            ajustes + mapeo a EpubPreferences
│       ├── TocEntry.kt                  aplanado de la tabla de contenidos
│       ├── Progress.kt                  porcentaje de lectura
│       ├── ReaderStores.kt              guardado local de posición y ajustes
│       ├── ReaderViewModel.kt           estado de apertura, ajustes, controles
│       ├── ReaderActivity.kt            aloja el navegador de Readium
│       └── ReaderControls.kt            controles Compose y diálogo de TOC
├── app/src/main/res/layout/activity_reader.xml
├── app/src/test/java/com/pluk/reader/reader/        pruebas JVM
├── app/src/androidTest/java/com/pluk/reader/reader/ pruebas en emulador
├── app/src/androidTest/assets/minimal.epub           fixture generado
└── tools/make_fixture_epub.py                        genera el fixture
```

---

### Tarea 1: Proyecto base con Readium y fixture de prueba (tarjeta K-001)

**Archivos:**
- Crear: `code/android/` (proyecto generado)
- Modificar: `code/android/gradle/libs.versions.toml`, `code/android/app/build.gradle.kts`
- Crear: `code/android/tools/make_fixture_epub.py`, `code/android/app/src/androidTest/assets/minimal.epub`

**Interfaces:**
- Produce: proyecto que compila con las dependencias de Readium disponibles, y el fixture `minimal.epub` (título "Libro de prueba", 2 capítulos "Capítulo 1" y "Capítulo 2") que usan las tareas 2 a 6.

- [ ] **Paso 1: Inicializar git en la raíz del repo** (pendiente de tu aprobación al revisar el plan; hoy `/Users/santilod/dev/pluk/reader` no es un repo)

```bash
cd /Users/santilod/dev/pluk/reader
git init
printf '.DS_Store\n.idea/\ncode/android/build/\ncode/android/app/build/\ncode/android/.gradle/\ncode/android/local.properties\ncode/android/samples/\n' > .gitignore
```

- [ ] **Paso 2: Generar el proyecto con la plantilla Compose**

```bash
cd /Users/santilod/dev/pluk/reader
android create empty-activity --name "Reader" --namespace com.pluk.reader --application-id com.pluk.reader --min-sdk 26 --output code/android
```

- [ ] **Paso 3: Verificar que compila**

```bash
cd code/android && ./gradlew :app:assembleDebug :app:testDebugUnitTest
```
Esperado: `BUILD SUCCESSFUL`.

- [ ] **Paso 4: Agregar Readium al catálogo de versiones**

En `gradle/libs.versions.toml`, dentro de `[versions]`, `[libraries]`:

```toml
[versions]
readium = "3.4.0"

[libraries]
readium-shared = { module = "org.readium.kotlin-toolkit:readium-shared", version.ref = "readium" }
readium-streamer = { module = "org.readium.kotlin-toolkit:readium-streamer", version.ref = "readium" }
readium-navigator = { module = "org.readium.kotlin-toolkit:readium-navigator", version.ref = "readium" }
androidx-fragment-ktx = { module = "androidx.fragment:fragment-ktx", version = "1.8.5" }
```

En `app/build.gradle.kts`, dentro de `dependencies { ... }`:

```kotlin
implementation(libs.readium.shared)
implementation(libs.readium.streamer)
implementation(libs.readium.navigator)
implementation(libs.androidx.fragment.ktx)
androidTestImplementation("androidx.test:core-ktx:1.6.1")
androidTestImplementation("androidx.test.ext:junit:1.2.1")
androidTestImplementation("androidx.test:runner:1.6.2")
```

Si el proyecto generado ya trae alguna de esas líneas de `androidTest`, no duplicarlas. Si Readium exige `coreLibraryDesugaring` (el error de Gradle lo dice), agregarlo según su mensaje.

- [ ] **Paso 5: Escribir el generador del fixture**

`code/android/tools/make_fixture_epub.py`:

```python
#!/usr/bin/env python3
"""Genera un EPUB 3 mínimo y válido para pruebas. Uso: make_fixture_epub.py <salida.epub>"""
import sys
import zipfile

CONTAINER = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
"""

OPF = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="bookid">urn:uuid:11111111-2222-3333-4444-555555555555</dc:identifier>
    <dc:title>Libro de prueba</dc:title>
    <dc:creator>Autor de prueba</dc:creator>
    <dc:language>es</dc:language>
    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="c1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
    <item id="c2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine>
    <itemref idref="c1"/>
    <itemref idref="c2"/>
  </spine>
</package>
"""

NAV = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
  <head><title>Contenido</title></head>
  <body>
    <nav epub:type="toc">
      <ol>
        <li><a href="ch1.xhtml">Capítulo 1</a></li>
        <li><a href="ch2.xhtml">Capítulo 2</a></li>
      </ol>
    </nav>
  </body>
</html>
"""


def chapter(title: str) -> str:
    paragraphs = "\n".join(
        f"    <p>{title}: párrafo {i}. Lorem ipsum dolor sit amet, consectetur adipiscing elit, "
        f"sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.</p>"
        for i in range(1, 41)
    )
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml">
  <head><title>{title}</title></head>
  <body>
    <h1>{title}</h1>
{paragraphs}
  </body>
</html>
"""


def main(out: str) -> None:
    with zipfile.ZipFile(out, "w") as z:
        # El archivo mimetype va primero y sin comprimir, como exige el estándar.
        z.writestr("mimetype", "application/epub+zip", compress_type=zipfile.ZIP_STORED)
        z.writestr("META-INF/container.xml", CONTAINER, compress_type=zipfile.ZIP_DEFLATED)
        z.writestr("OEBPS/content.opf", OPF, compress_type=zipfile.ZIP_DEFLATED)
        z.writestr("OEBPS/nav.xhtml", NAV, compress_type=zipfile.ZIP_DEFLATED)
        z.writestr("OEBPS/ch1.xhtml", chapter("Capítulo 1"), compress_type=zipfile.ZIP_DEFLATED)
        z.writestr("OEBPS/ch2.xhtml", chapter("Capítulo 2"), compress_type=zipfile.ZIP_DEFLATED)


if __name__ == "__main__":
    main(sys.argv[1])
```

- [ ] **Paso 6: Generar el fixture**

```bash
cd /Users/santilod/dev/pluk/reader/code/android
mkdir -p app/src/androidTest/assets
python3 tools/make_fixture_epub.py app/src/androidTest/assets/minimal.epub
unzip -l app/src/androidTest/assets/minimal.epub
```
Esperado: lista con `mimetype`, `META-INF/container.xml`, `OEBPS/content.opf`, `nav.xhtml`, `ch1.xhtml`, `ch2.xhtml`.

- [ ] **Paso 7: Verificar que compila con Readium y que hay emulador**

```bash
./gradlew :app:assembleDebug
~/Library/Android/sdk/emulator/emulator -list-avds
```
Esperado: `BUILD SUCCESSFUL` y al menos un AVD en la lista. Arrancarlo para las tareas siguientes:

```bash
~/Library/Android/sdk/emulator/emulator -avd <nombre de la lista> -no-snapshot-save &
~/Library/Android/sdk/platform-tools/adb wait-for-device
```

- [ ] **Paso 8: Commit**

```bash
cd /Users/santilod/dev/pluk/reader
git add -A
git commit -m "chore: proyecto Android base con Readium y fixture EPUB" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Tarea 2: Cargar un EPUB desde una URI (tarjeta K-002)

**Archivos:**
- Crear: `app/src/main/java/com/pluk/reader/reader/PublicationLoader.kt`
- Prueba: `app/src/androidTest/java/com/pluk/reader/reader/PublicationLoaderTest.kt`

**Interfaces:**
- Produce: `class LoadException(message: String, cause: Throwable? = null) : Exception`
- Produce: `class PublicationLoader(context: Context) { suspend fun load(uri: Uri): Result<Publication> }`. En fallo, `exceptionOrNull()` es siempre una `LoadException` con mensaje en español apto para mostrar al usuario.

- [ ] **Paso 1: Escribir las pruebas que fallan**

`PublicationLoaderTest.kt`:

```kotlin
package com.pluk.reader.reader

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PublicationLoaderTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun copyAsset(name: String): Uri {
        val file = File(context.cacheDir, name)
        instrumentation.context.assets.open(name).use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        return Uri.fromFile(file)
    }

    // LIB-002, RDR-007
    @Test
    fun opensValidEpub() = runBlocking {
        val result = PublicationLoader(context).load(copyAsset("minimal.epub"))
        assertTrue(result.isSuccess)
        assertEquals("Libro de prueba", result.getOrThrow().metadata.title)
    }

    // LIB-002: archivo que no es EPUB
    @Test
    fun failsWithClearMessageOnNonEpubFile() = runBlocking {
        val file = File(context.cacheDir, "basura.epub").apply { writeText("esto no es un epub") }
        val result = PublicationLoader(context).load(Uri.fromFile(file))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is LoadException)
    }

    // Foco de revisión 5: archivo inaccesible
    @Test
    fun failsWithClearMessageOnMissingFile() = runBlocking {
        val missing = Uri.fromFile(File(context.cacheDir, "no-existe.epub"))
        val result = PublicationLoader(context).load(missing)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is LoadException)
    }
}
```

- [ ] **Paso 2: Ejecutar y ver que falla**

```bash
cd code/android && ./gradlew :app:connectedDebugAndroidTest --tests "com.pluk.reader.reader.PublicationLoaderTest"
```
Esperado: error de compilación, `Unresolved reference: PublicationLoader`.

- [ ] **Paso 3: Implementar**

`PublicationLoader.kt`:

```kotlin
package com.pluk.reader.reader

import android.content.Context
import android.net.Uri
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

class LoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

class PublicationLoader(context: Context) {
    private val appContext = context.applicationContext
    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(appContext.contentResolver, httpClient)
    private val opener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            appContext,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            pdfFactory = null,
        ),
    )

    suspend fun load(uri: Uri): Result<Publication> {
        val url = uri.toUrl()
            ?: return Result.failure(LoadException("No se pudo acceder al archivo."))
        val asset = assetRetriever.retrieve(url).getOrElse {
            return Result.failure(LoadException("No se pudo leer el archivo.", Exception(it.message)))
        }
        val publication = opener.open(asset, allowUserInteraction = false).getOrElse {
            return Result.failure(
                LoadException("No se pudo abrir el libro. El archivo puede estar dañado o protegido con DRM.", Exception(it.message)),
            )
        }
        return Result.success(publication)
    }
}
```

Los nombres de Readium vienen de la guía oficial de la versión 3.4.0. Si el compilador marca algún import o parámetro distinto, corregirlo según la documentación de esa versión sin cambiar el diseño ni las firmas de arriba.

- [ ] **Paso 4: Ejecutar y ver que pasa**

```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.pluk.reader.reader.PublicationLoaderTest"
```
Esperado: 3 tests pasan.

- [ ] **Paso 5: Commit**

```bash
git add -A
git commit -m "feat: cargar EPUB desde URI con Readium (LIB-002)" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Tarea 3: Mostrar el libro en pantalla (tarjeta K-003)

**Archivos:**
- Crear: `reader/ReaderViewModel.kt`, `reader/ReaderActivity.kt`, `res/layout/activity_reader.xml`
- Modificar: `MainActivity.kt`, `AndroidManifest.xml`
- Prueba: `androidTest/.../reader/ReaderActivityTest.kt`

**Interfaces:**
- Consume: `PublicationLoader.load(uri)` y `LoadException` (Tarea 2).
- Produce:
  - `data class ReaderSession(val publication: Publication, val factory: EpubNavigatorFactory, val bookId: String, val initialLocator: Locator? = null)`
  - `sealed interface ReaderState { object Loading; data class Ready(val session: ReaderSession); data class Failed(val message: String) }`
  - `class ReaderViewModel(app: Application) : AndroidViewModel` con `val state: StateFlow<ReaderState>`, `val session: ReaderSession?` y `fun open(uri: Uri)` (idempotente).
  - `ReaderActivity.NAVIGATOR_TAG = "navigator"` (el fragmento de Readium) y vistas `R.id.loading`, `R.id.error`, `R.id.reader_container`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

`ReaderActivityTest.kt`:

```kotlin
package com.pluk.reader.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReaderActivityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @Before
    fun cleanState() {
        context.getSharedPreferences("reader_locators", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("reader_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun copyAsset(name: String): Uri {
        val file = File(context.cacheDir, name)
        instrumentation.context.assets.open(name).use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        return Uri.fromFile(file)
    }

    private fun readerIntent(uri: Uri) =
        Intent(context, ReaderActivity::class.java).setData(uri)

    private fun waitUntil(timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        throw AssertionError("Condición no cumplida en ${timeoutMs}ms")
    }

    private fun hasNavigator(scenario: ActivityScenario<ReaderActivity>): Boolean {
        var found = false
        scenario.onActivity {
            found = it.supportFragmentManager.findFragmentByTag(ReaderActivity.NAVIGATOR_TAG) != null
        }
        return found
    }

    // RDR-007
    @Test
    fun showsNavigatorForValidEpub() {
        ActivityScenario.launch<ReaderActivity>(readerIntent(copyAsset("minimal.epub"))).use { scenario ->
            waitUntil { hasNavigator(scenario) }
        }
    }

    // Foco de revisión 2: rotación o recreación en plena lectura
    @Test
    fun keepsNavigatorAfterRecreation() {
        ActivityScenario.launch<ReaderActivity>(readerIntent(copyAsset("minimal.epub"))).use { scenario ->
            waitUntil { hasNavigator(scenario) }
            scenario.recreate()
            waitUntil { hasNavigator(scenario) }
        }
    }

    // LIB-002, foco de revisión 1
    @Test
    fun showsErrorMessageForCorruptFile() {
        val file = File(context.cacheDir, "corrupto.epub").apply { writeText("esto no es un epub") }
        ActivityScenario.launch<ReaderActivity>(readerIntent(Uri.fromFile(file))).use { scenario ->
            waitUntil {
                var visible = false
                scenario.onActivity {
                    val error = it.findViewById<TextView>(R.id.error)
                    visible = error.isVisible && error.text.isNotBlank()
                }
                visible
            }
            assertTrue(!hasNavigator(scenario))
        }
    }
}
```

- [ ] **Paso 2: Ejecutar y ver que falla**

```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.pluk.reader.reader.ReaderActivityTest"
```
Esperado: error de compilación, `Unresolved reference: ReaderActivity`.

- [ ] **Paso 3: Implementar el ViewModel**

`ReaderViewModel.kt`:

```kotlin
package com.pluk.reader.reader

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

data class ReaderSession @OptIn(ExperimentalReadiumApi::class) constructor(
    val publication: Publication,
    val factory: EpubNavigatorFactory,
    val bookId: String,
    val initialLocator: Locator? = null,
)

sealed interface ReaderState {
    data object Loading : ReaderState
    data class Ready(val session: ReaderSession) : ReaderState
    data class Failed(val message: String) : ReaderState
}

@OptIn(ExperimentalReadiumApi::class)
class ReaderViewModel(app: Application) : AndroidViewModel(app) {
    private val loader = PublicationLoader(app)
    private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading)
    val state: StateFlow<ReaderState> = _state.asStateFlow()
    val session: ReaderSession? get() = (_state.value as? ReaderState.Ready)?.session
    private var started = false

    fun open(uri: Uri) {
        if (started) return
        started = true
        viewModelScope.launch {
            loader.load(uri).fold(
                onSuccess = { publication ->
                    val bookId = publication.metadata.identifier ?: uri.toString()
                    _state.value = ReaderState.Ready(
                        ReaderSession(publication, EpubNavigatorFactory(publication), bookId),
                    )
                },
                onFailure = { _state.value = ReaderState.Failed(it.message ?: "No se pudo abrir el libro.") },
            )
        }
    }
}
```

- [ ] **Paso 4: Layout y actividad**

`res/layout/activity_reader.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.fragment.app.FragmentContainerView
        android:id="@+id/reader_container"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <ProgressBar
        android:id="@+id/loading"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center" />

    <TextView
        android:id="@+id/error"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:gravity="center"
        android:padding="24dp"
        android:textSize="16sp"
        android:visibility="gone" />
</FrameLayout>
```

`ReaderActivity.kt`:

```kotlin
package com.pluk.reader.reader

import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pluk.reader.R
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
class ReaderActivity : FragmentActivity(), EpubNavigatorFragment.Listener {
    private val viewModel: ReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // El factory debe estar instalado antes de super.onCreate para que Android
        // pueda restaurar el fragmento tras una recreación. Sin sesión no hay nada que restaurar.
        val existing = viewModel.session
        existing?.let(::installFragmentFactory)
        super.onCreate(if (existing != null) savedInstanceState else null)
        setContentView(R.layout.activity_reader)

        intent.data?.let(viewModel::open)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun installFragmentFactory(session: ReaderSession) {
        supportFragmentManager.fragmentFactory = session.factory.createFragmentFactory(
            initialLocator = session.initialLocator,
            listener = this,
        )
    }

    private fun render(state: ReaderState) {
        findViewById<ProgressBar>(R.id.loading).isVisible = state is ReaderState.Loading
        findViewById<TextView>(R.id.error).apply {
            isVisible = state is ReaderState.Failed
            if (state is ReaderState.Failed) text = state.message
        }
        if (state is ReaderState.Ready &&
            supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) == null
        ) {
            installFragmentFactory(state.session)
            supportFragmentManager.beginTransaction()
                .replace(R.id.reader_container, EpubNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
                .commitNow()
        }
    }

    companion object {
        const val NAVIGATOR_TAG = "navigator"
    }
}
```

- [ ] **Paso 5: Manifest**

En `AndroidManifest.xml`, dentro de `<application>`, junto a `MainActivity`:

```xml
<activity
    android:name=".reader.ReaderActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="content" />
        <data android:scheme="file" />
        <data android:mimeType="application/epub+zip" />
    </intent-filter>
</activity>
```

- [ ] **Paso 6: Pantalla inicial con selector de archivos**

Reemplazar el contenido de `MainActivity.kt`. Usar como tema el que generó la plantilla (archivo `ui/theme/Theme.kt`; aquí `ReaderTheme`):

```kotlin
package com.pluk.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pluk.reader.reader.ReaderActivity
import com.pluk.reader.ui.theme.ReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ReaderTheme { OpenScreen(onPicked = ::openBook) } }
    }

    private fun openBook(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Algunos proveedores no permiten permiso persistente. Se abre igual esta vez.
        }
        startActivity(
            Intent(this, ReaderActivity::class.java)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }
}

@Composable
private fun OpenScreen(onPicked: (Uri) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPicked(uri)
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(onClick = { launcher.launch(arrayOf("application/epub+zip")) }) {
            Text("Abrir EPUB")
        }
    }
}
```

- [ ] **Paso 7: Ejecutar y ver que pasa**

```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.pluk.reader.reader.ReaderActivityTest"
```
Esperado: 3 tests pasan. Si el navegador no aparece, revisar `adb logcat -s AndroidRuntime` y corregir según la documentación de Readium 3.4.0.

- [ ] **Paso 8: Commit**

```bash
git add -A
git commit -m "feat: mostrar EPUB con el navegador de Readium (RDR-007, AND-003)" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Tarea 4: Ajustes de lectura: paginado o scroll, tema y tamaño de letra (tarjeta K-004)

**Archivos:**
- Crear: `reader/ReaderSettings.kt`, `reader/ReaderControls.kt`
- Modificar: `reader/ReaderViewModel.kt`, `reader/ReaderActivity.kt`, `res/layout/activity_reader.xml`
- Prueba: `app/src/test/.../reader/ReaderSettingsTest.kt`, `ReaderActivityTest.kt`

**Interfaces:**
- Consume: `ReaderViewModel`, `ReaderActivity.NAVIGATOR_TAG` (Tarea 3).
- Produce:
  - `enum class ReaderTheme { LIGHT, DARK, SEPIA }`
  - `data class ReaderSettings(val scroll: Boolean = false, val theme: ReaderTheme = LIGHT, val fontScale: Double = 1.0)` con `toggleScroll()`, `nextTheme()`, `biggerFont()`, `smallerFont()` (cada uno devuelve un `ReaderSettings`). La escala va de 0.5 a 2.5 en pasos de 0.1.
  - `fun ReaderSettings.toEpubPreferences(): EpubPreferences`
  - En `ReaderViewModel`: `val settings: StateFlow<ReaderSettings>`, `val controlsVisible: StateFlow<Boolean>`, `fun toggleScroll()`, `fun nextTheme()`, `fun biggerFont()`, `fun smallerFont()`, `fun toggleControls()`.

- [ ] **Paso 1: Escribir las pruebas JVM que fallan**

`ReaderSettingsTest.kt`:

```kotlin
package com.pluk.reader.reader

import org.junit.Assert.assertEquals
import org.junit.Test
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
class ReaderSettingsTest {
    // RDR-001
    @Test
    fun toggleScrollSwitchesModeAndMapsToPreferences() {
        val settings = ReaderSettings().toggleScroll()
        assertEquals(true, settings.scroll)
        assertEquals(true, settings.toEpubPreferences().scroll)
        assertEquals(false, settings.toggleScroll().scroll)
    }

    // RDR-003
    @Test
    fun themeCyclesLightDarkSepiaLight() {
        var s = ReaderSettings()
        assertEquals(ReaderTheme.LIGHT, s.theme)
        s = s.nextTheme(); assertEquals(ReaderTheme.DARK, s.theme)
        s = s.nextTheme(); assertEquals(ReaderTheme.SEPIA, s.theme)
        s = s.nextTheme(); assertEquals(ReaderTheme.LIGHT, s.theme)
    }

    // RDR-003
    @Test
    fun themeMapsToReadiumTheme() {
        assertEquals(Theme.DARK, ReaderSettings(theme = ReaderTheme.DARK).toEpubPreferences().theme)
        assertEquals(Theme.SEPIA, ReaderSettings(theme = ReaderTheme.SEPIA).toEpubPreferences().theme)
        assertEquals(Theme.LIGHT, ReaderSettings(theme = ReaderTheme.LIGHT).toEpubPreferences().theme)
    }

    // RDR-002
    @Test
    fun fontScaleStepsByTenthAndMapsToFontSize() {
        assertEquals(1.1, ReaderSettings().biggerFont().fontScale, 0.0001)
        assertEquals(0.9, ReaderSettings().smallerFont().fontScale, 0.0001)
        assertEquals(1.2, ReaderSettings(fontScale = 1.2).toEpubPreferences().fontSize ?: 0.0, 0.0001)
    }

    // RDR-002: límites
    @Test
    fun fontScaleIsClamped() {
        assertEquals(2.5, ReaderSettings(fontScale = 2.5).biggerFont().fontScale, 0.0001)
        assertEquals(0.5, ReaderSettings(fontScale = 0.5).smallerFont().fontScale, 0.0001)
    }
}
```

- [ ] **Paso 2: Ejecutar y ver que falla**

```bash
./gradlew :app:testDebugUnitTest --tests "com.pluk.reader.reader.ReaderSettingsTest"
```
Esperado: error de compilación, `Unresolved reference: ReaderSettings`.

- [ ] **Paso 3: Implementar los ajustes**

`ReaderSettings.kt`:

```kotlin
package com.pluk.reader.reader

import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import kotlin.math.roundToInt

enum class ReaderTheme {
    LIGHT, DARK, SEPIA;

    fun next(): ReaderTheme = entries[(ordinal + 1) % entries.size]
}

data class ReaderSettings(
    val scroll: Boolean = false,
    val theme: ReaderTheme = ReaderTheme.LIGHT,
    val fontScale: Double = 1.0,
) {
    fun toggleScroll() = copy(scroll = !scroll)
    fun nextTheme() = copy(theme = theme.next())
    fun biggerFont() = copy(fontScale = step(+1))
    fun smallerFont() = copy(fontScale = step(-1))

    private fun step(direction: Int): Double =
        ((fontScale * 10).roundToInt() + direction).coerceIn(MIN_TENTHS, MAX_TENTHS) / 10.0

    private companion object {
        const val MIN_TENTHS = 5
        const val MAX_TENTHS = 25
    }
}

@OptIn(ExperimentalReadiumApi::class)
fun ReaderSettings.toEpubPreferences(): EpubPreferences = EpubPreferences(
    scroll = scroll,
    theme = when (theme) {
        ReaderTheme.LIGHT -> Theme.LIGHT
        ReaderTheme.DARK -> Theme.DARK
        ReaderTheme.SEPIA -> Theme.SEPIA
    },
    fontSize = fontScale,
)
```

- [ ] **Paso 4: Ejecutar y ver que pasa**

```bash
./gradlew :app:testDebugUnitTest --tests "com.pluk.reader.reader.ReaderSettingsTest"
```
Esperado: 5 tests pasan. Si las clases de Readium no cargan en la JVM, mover este test a `androidTest` sin cambiar su contenido.

- [ ] **Paso 5: Agregar el test instrumentado que falla**

En `ReaderActivityTest.kt`, agregar `@OptIn(ExperimentalReadiumApi::class)` a la clase, estos imports (`androidx.lifecycle.ViewModelProvider`, `org.readium.r2.navigator.epub.EpubNavigatorFragment`, `org.readium.r2.shared.ExperimentalReadiumApi`) y el test:

```kotlin
    // RDR-001: el ajuste llega al navegador
    @Test
    fun scrollSettingIsAppliedToNavigator() {
        ActivityScenario.launch<ReaderActivity>(readerIntent(copyAsset("minimal.epub"))).use { scenario ->
            waitUntil { hasNavigator(scenario) }
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[ReaderViewModel::class.java].toggleScroll()
            }
            waitUntil {
                var scroll = false
                scenario.onActivity { activity ->
                    val navigator = activity.supportFragmentManager
                        .findFragmentByTag(ReaderActivity.NAVIGATOR_TAG) as EpubNavigatorFragment
                    scroll = navigator.settings.value.scroll
                }
                scroll
            }
        }
    }
```

Ejecutar y ver que falla (`Unresolved reference: toggleScroll`).

- [ ] **Paso 6: Implementar ViewModel, controles y actividad**

En `ReaderViewModel.kt`, agregar dentro de la clase:

```kotlin
    private val _settings = MutableStateFlow(ReaderSettings())
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()

    private val _controlsVisible = MutableStateFlow(true)
    val controlsVisible: StateFlow<Boolean> = _controlsVisible.asStateFlow()

    fun toggleScroll() = update { it.toggleScroll() }
    fun nextTheme() = update { it.nextTheme() }
    fun biggerFont() = update { it.biggerFont() }
    fun smallerFont() = update { it.smallerFont() }
    fun toggleControls() { _controlsVisible.value = !_controlsVisible.value }

    private fun update(transform: (ReaderSettings) -> ReaderSettings) {
        _settings.value = transform(_settings.value)
    }
```

`ReaderControls.kt`:

```kotlin
package com.pluk.reader.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ReaderControls(
    visible: Boolean,
    settings: ReaderSettings,
    onToggleScroll: () -> Unit,
    onNextTheme: () -> Unit,
    onSmallerFont: () -> Unit,
    onBiggerFont: () -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onToggleScroll) { Text(if (settings.scroll) "Scroll" else "Páginas") }
                TextButton(onClick = onNextTheme) {
                    Text(
                        when (settings.theme) {
                            ReaderTheme.LIGHT -> "Claro"
                            ReaderTheme.DARK -> "Oscuro"
                            ReaderTheme.SEPIA -> "Sepia"
                        },
                    )
                }
                TextButton(onClick = onSmallerFont) { Text("A−") }
                TextButton(onClick = onBiggerFont) { Text("A+") }
            }
        }
    }
}
```

En `activity_reader.xml`, antes de `</FrameLayout>`, agregar la barra superpuesta (solo ocupa su altura, el resto de la pantalla sigue recibiendo toques del lector):

```xml
    <androidx.compose.ui.platform.ComposeView
        android:id="@+id/controls"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="top" />
```

En `ReaderActivity.kt`:

1. Imports nuevos: `android.graphics.PointF`, `androidx.compose.runtime.collectAsState`, `androidx.compose.runtime.getValue`, `androidx.compose.ui.platform.ComposeView`, `com.pluk.reader.ui.theme.ReaderTheme`.
2. En `installFragmentFactory`, agregar el argumento `initialPreferences = viewModel.settings.value.toEpubPreferences(),`.
3. Al final de `onCreate`, agregar:

```kotlin
        findViewById<ComposeView>(R.id.controls).setContent {
            val settings by viewModel.settings.collectAsState()
            val visible by viewModel.controlsVisible.collectAsState()
            ReaderTheme {
                ReaderControls(
                    visible = visible,
                    settings = settings,
                    onToggleScroll = viewModel::toggleScroll,
                    onNextTheme = viewModel::nextTheme,
                    onSmallerFont = viewModel::smallerFont,
                    onBiggerFont = viewModel::biggerFont,
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect { navigator()?.submitPreferences(it.toEpubPreferences()) }
            }
        }
```
4. Métodos nuevos en la clase:

```kotlin
    private fun navigator(): EpubNavigatorFragment? =
        supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? EpubNavigatorFragment

    // Un toque en el centro muestra u oculta los controles.
    override fun onTap(point: PointF): Boolean {
        viewModel.toggleControls()
        return true
    }
```

- [ ] **Paso 7: Ejecutar todo y ver que pasa**

```bash
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest
```
Esperado: todo en verde, incluido `scrollSettingIsAppliedToNavigator`.

- [ ] **Paso 8: Commit**

```bash
git add -A
git commit -m "feat: ajustes de lectura de modo, tema y tamaño (RDR-001, RDR-002, RDR-003)" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Tarea 5: Tabla de contenidos y progreso (tarjeta K-005)

**Archivos:**
- Crear: `reader/TocEntry.kt`, `reader/Progress.kt`
- Modificar: `reader/ReaderViewModel.kt`, `reader/ReaderActivity.kt`, `reader/ReaderControls.kt`
- Prueba: `app/src/test/.../reader/TocAndProgressTest.kt`, `androidTest/.../reader/TocLoadTest.kt`

**Interfaces:**
- Consume: `PublicationLoader` (Tarea 2), `ReaderViewModel`, `ReaderControls`, `ReaderActivity.navigator()` (Tareas 3 y 4).
- Produce:
  - `data class TocEntry(val title: String, val link: Link, val depth: Int)`
  - `fun flattenToc(links: List<Link>, depth: Int = 0): List<TocEntry>`
  - `fun progressPercent(totalProgression: Double?): Int?`
  - En `ReaderViewModel`: `val progress: StateFlow<Int?>`, `val toc: List<TocEntry>`, `fun onLocatorChanged(locator: Locator)`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

`TocAndProgressTest.kt` (JVM):

```kotlin
package com.pluk.reader.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TocAndProgressTest {
    // RDR-005
    @Test
    fun progressIsRoundedPercent() {
        assertEquals(42, progressPercent(0.4249))
        assertEquals(0, progressPercent(0.0))
        assertEquals(100, progressPercent(1.0))
    }

    // RDR-005: sin dato todavía
    @Test
    fun progressIsNullWhenUnknown() {
        assertNull(progressPercent(null))
    }

    // RDR-005: valores fuera de rango
    @Test
    fun progressIsClamped() {
        assertEquals(100, progressPercent(1.7))
        assertEquals(0, progressPercent(-0.2))
    }

    // Foco de revisión 3: libro sin tabla de contenidos
    @Test
    fun emptyTocFlattensToEmptyList() {
        assertTrue(flattenToc(emptyList()).isEmpty())
    }
}
```

`TocLoadTest.kt` (instrumentada):

```kotlin
package com.pluk.reader.reader

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TocLoadTest {
    // RDR-004
    @Test
    fun readsChaptersFromRealEpub() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "minimal.epub")
        instrumentation.context.assets.open("minimal.epub").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        val publication = PublicationLoader(context).load(Uri.fromFile(file)).getOrThrow()

        val entries = flattenToc(publication.tableOfContents)

        assertEquals(listOf("Capítulo 1", "Capítulo 2"), entries.map { it.title })
        assertEquals(listOf(0, 0), entries.map { it.depth })
    }
}
```

- [ ] **Paso 2: Ejecutar y ver que falla**

```bash
./gradlew :app:testDebugUnitTest --tests "com.pluk.reader.reader.TocAndProgressTest"
```
Esperado: `Unresolved reference: progressPercent`.

- [ ] **Paso 3: Implementar**

`Progress.kt`:

```kotlin
package com.pluk.reader.reader

import kotlin.math.roundToInt

/** Porcentaje 0..100 del libro leído, o null si todavía no hay dato (RDR-005). */
fun progressPercent(totalProgression: Double?): Int? =
    totalProgression?.let { (it * 100).roundToInt().coerceIn(0, 100) }
```

`TocEntry.kt`:

```kotlin
package com.pluk.reader.reader

import org.readium.r2.shared.publication.Link

data class TocEntry(val title: String, val link: Link, val depth: Int)

/** Aplana la tabla de contenidos conservando el nivel de cada entrada (RDR-004). */
fun flattenToc(links: List<Link>, depth: Int = 0): List<TocEntry> =
    links.flatMap { link ->
        listOf(TocEntry(link.title ?: link.href.toString(), link, depth)) +
            flattenToc(link.children, depth + 1)
    }
```

- [ ] **Paso 4: Ejecutar y ver que pasa**

```bash
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest --tests "com.pluk.reader.reader.TocLoadTest"
```
Esperado: pasan los tests JVM y `TocLoadTest`.

- [ ] **Paso 5: Conectar ViewModel, controles y actividad**

En `ReaderViewModel.kt`, agregar import `org.readium.r2.shared.publication.Locator` (ya está) y dentro de la clase:

```kotlin
    private val _progress = MutableStateFlow<Int?>(null)
    val progress: StateFlow<Int?> = _progress.asStateFlow()

    val toc: List<TocEntry>
        get() = session?.publication?.let { flattenToc(it.tableOfContents) }.orEmpty()

    fun onLocatorChanged(locator: Locator) {
        _progress.value = progressPercent(locator.locations.totalProgression)
    }
```

Reemplazar `ReaderControls.kt` completo (agrega botón "Índice", porcentaje y diálogo de tabla de contenidos):

```kotlin
package com.pluk.reader.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ReaderControls(
    visible: Boolean,
    settings: ReaderSettings,
    progressPercent: Int?,
    toc: List<TocEntry>,
    onToggleScroll: () -> Unit,
    onNextTheme: () -> Unit,
    onSmallerFont: () -> Unit,
    onBiggerFont: () -> Unit,
    onTocSelected: (TocEntry) -> Unit,
) {
    var showToc by remember { mutableStateOf(false) }

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
            Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showToc = true }) { Text("Índice") }
                    TextButton(onClick = onToggleScroll) { Text(if (settings.scroll) "Scroll" else "Páginas") }
                    TextButton(onClick = onNextTheme) {
                        Text(
                            when (settings.theme) {
                                ReaderTheme.LIGHT -> "Claro"
                                ReaderTheme.DARK -> "Oscuro"
                                ReaderTheme.SEPIA -> "Sepia"
                            },
                        )
                    }
                    TextButton(onClick = onSmallerFont) { Text("A−") }
                    TextButton(onClick = onBiggerFont) { Text("A+") }
                }
                Text(
                    text = progressPercent?.let { "$it %" } ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
                )
            }
        }
    }

    if (showToc) {
        AlertDialog(
            onDismissRequest = { showToc = false },
            confirmButton = { TextButton(onClick = { showToc = false }) { Text("Cerrar") } },
            title = { Text("Índice") },
            text = {
                if (toc.isEmpty()) {
                    Text("Este libro no tiene tabla de contenidos.")
                } else {
                    LazyColumn {
                        items(toc) { entry ->
                            Text(
                                text = entry.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showToc = false
                                        onTocSelected(entry)
                                    }
                                    .padding(start = (entry.depth * 16).dp, top = 10.dp, bottom = 10.dp),
                            )
                        }
                    }
                }
            },
        )
    }
}
```

En `ReaderActivity.kt`: dentro de `setContent { ... }` ajustar la llamada a `ReaderControls`:

```kotlin
            val progress by viewModel.progress.collectAsState()
            ReaderTheme {
                ReaderControls(
                    visible = visible,
                    settings = settings,
                    progressPercent = progress,
                    toc = viewModel.toc,
                    onToggleScroll = viewModel::toggleScroll,
                    onNextTheme = viewModel::nextTheme,
                    onSmallerFont = viewModel::smallerFont,
                    onBiggerFont = viewModel::biggerFont,
                    onTocSelected = { entry -> lifecycleScope.launch { navigator()?.go(entry.link) } },
                )
            }
```

Y en `render`, justo después de `commitNow()`, enganchar el progreso del navegador:

```kotlin
            navigator()?.let { nav ->
                lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        nav.currentLocator.collect(viewModel::onLocatorChanged)
                    }
                }
            }
```

Si tras una recreación de la actividad el fragmento se restaura sin pasar por `render`, mover ese bloque a un método `observeLocator()` que se llame también desde `onCreate` cuando `existing != null`.

- [ ] **Paso 6: Ejecutar todo**

```bash
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest
```
Esperado: todo en verde.

- [ ] **Paso 7: Commit**

```bash
git add -A
git commit -m "feat: tabla de contenidos y progreso de lectura (RDR-004, RDR-005)" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Tarea 6: Recordar la posición y los ajustes (tarjeta K-006)

**Archivos:**
- Crear: `reader/ReaderStores.kt`
- Modificar: `reader/ReaderViewModel.kt`
- Prueba: `androidTest/.../reader/ReaderStoresTest.kt`, `ReaderActivityTest.kt`

**Interfaces:**
- Consume: `ReaderViewModel.update`, `ReaderViewModel.onLocatorChanged`, `ReaderSession.initialLocator` (Tareas 3 a 5).
- Produce:
  - `interface LocatorStore { fun load(bookId: String): Locator?; fun save(bookId: String, locator: Locator) }`
  - `interface SettingsStore { fun load(): ReaderSettings; fun save(settings: ReaderSettings) }`
  - `class PrefsLocatorStore(context: Context) : LocatorStore` (archivo de preferencias `reader_locators`)
  - `class PrefsSettingsStore(context: Context) : SettingsStore` (archivo de preferencias `reader_settings`)

- [ ] **Paso 1: Escribir las pruebas que fallan**

`ReaderStoresTest.kt`:

```kotlin
package com.pluk.reader.reader

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url

@RunWith(AndroidJUnit4::class)
class ReaderStoresTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clean() {
        context.getSharedPreferences("reader_locators", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("reader_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    // RDR-006
    @Test
    fun locatorRoundTrips() {
        val store = PrefsLocatorStore(context)
        val locator = Locator(
            href = Url("OEBPS/ch2.xhtml")!!,
            mediaType = org.readium.r2.shared.util.mediatype.MediaType.XHTML,
            locations = Locator.Locations(totalProgression = 0.75),
        )
        store.save("libro-1", locator)
        val loaded = store.load("libro-1")
        assertEquals(locator.href, loaded?.href)
        assertEquals(0.75, loaded?.locations?.totalProgression ?: -1.0, 0.0001)
    }

    // RDR-006: libro desconocido
    @Test
    fun unknownBookHasNoLocator() {
        assertNull(PrefsLocatorStore(context).load("no-existe"))
    }

    // RDR-006: cada libro guarda la suya
    @Test
    fun locatorsAreIndependentPerBook() {
        val store = PrefsLocatorStore(context)
        val a = Locator(Url("a.xhtml")!!, org.readium.r2.shared.util.mediatype.MediaType.XHTML)
        val b = Locator(Url("b.xhtml")!!, org.readium.r2.shared.util.mediatype.MediaType.XHTML)
        store.save("A", a)
        store.save("B", b)
        assertEquals(a.href, store.load("A")?.href)
        assertEquals(b.href, store.load("B")?.href)
    }

    // RDR-002, RDR-003
    @Test
    fun settingsRoundTrip() {
        val store = PrefsSettingsStore(context)
        val settings = ReaderSettings(scroll = true, theme = ReaderTheme.SEPIA, fontScale = 1.4)
        store.save(settings)
        assertEquals(settings, store.load())
    }

    // Primera ejecución: valores por defecto
    @Test
    fun settingsDefaultWhenNothingSaved() {
        assertEquals(ReaderSettings(), PrefsSettingsStore(context).load())
    }
}
```

En `ReaderActivityTest.kt`, agregar (con imports `androidx.lifecycle.lifecycleScope`, `kotlinx.coroutines.launch`):

```kotlin
    // Foco de revisión 4: cerrar y reabrir vuelve a la misma posición (RDR-006)
    @Test
    fun reopeningBookRestoresPosition() {
        val uri = copyAsset("minimal.epub")
        val bookId = "urn:uuid:11111111-2222-3333-4444-555555555555"

        ActivityScenario.launch<ReaderActivity>(readerIntent(uri)).use { scenario ->
            waitUntil { hasNavigator(scenario) }
            scenario.onActivity { activity ->
                val vm = ViewModelProvider(activity)[ReaderViewModel::class.java]
                val navigator = activity.supportFragmentManager
                    .findFragmentByTag(ReaderActivity.NAVIGATOR_TAG) as EpubNavigatorFragment
                activity.lifecycleScope.launch { navigator.go(vm.session!!.publication.readingOrder[1]) }
            }
            waitUntil { PrefsLocatorStore(context).load(bookId)?.href.toString().endsWith("ch2.xhtml") }
        }

        ActivityScenario.launch<ReaderActivity>(readerIntent(uri)).use { scenario ->
            waitUntil { hasNavigator(scenario) }
            waitUntil {
                var href = ""
                scenario.onActivity { activity ->
                    val navigator = activity.supportFragmentManager
                        .findFragmentByTag(ReaderActivity.NAVIGATOR_TAG) as EpubNavigatorFragment
                    href = navigator.currentLocator.value.href.toString()
                }
                href.endsWith("ch2.xhtml")
            }
        }
    }
```

- [ ] **Paso 2: Ejecutar y ver que falla**

```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.pluk.reader.reader.ReaderStoresTest"
```
Esperado: `Unresolved reference: PrefsLocatorStore`.

- [ ] **Paso 3: Implementar los almacenes**

`ReaderStores.kt`:

```kotlin
package com.pluk.reader.reader

import android.content.Context
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

interface LocatorStore {
    fun load(bookId: String): Locator?
    fun save(bookId: String, locator: Locator)
}

interface SettingsStore {
    fun load(): ReaderSettings
    fun save(settings: ReaderSettings)
}

/** Posición de lectura por libro, en el dispositivo (RDR-006). La sincronización llega con la rebanada de sync. */
class PrefsLocatorStore(context: Context) : LocatorStore {
    private val prefs = context.applicationContext.getSharedPreferences("reader_locators", Context.MODE_PRIVATE)

    override fun load(bookId: String): Locator? =
        prefs.getString(bookId, null)?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }

    override fun save(bookId: String, locator: Locator) {
        prefs.edit().putString(bookId, locator.toJSON().toString()).apply()
    }
}

class PrefsSettingsStore(context: Context) : SettingsStore {
    private val prefs = context.applicationContext.getSharedPreferences("reader_settings", Context.MODE_PRIVATE)

    override fun load(): ReaderSettings {
        val defaults = ReaderSettings()
        return ReaderSettings(
            scroll = prefs.getBoolean("scroll", defaults.scroll),
            theme = runCatching { ReaderTheme.valueOf(prefs.getString("theme", null) ?: "") }
                .getOrDefault(defaults.theme),
            fontScale = prefs.getFloat("fontScale", defaults.fontScale.toFloat()).toDouble(),
        )
    }

    override fun save(settings: ReaderSettings) {
        prefs.edit()
            .putBoolean("scroll", settings.scroll)
            .putString("theme", settings.theme.name)
            .putFloat("fontScale", settings.fontScale.toFloat())
            .apply()
    }
}
```

- [ ] **Paso 4: Conectar el ViewModel**

En `ReaderViewModel.kt`:

1. Campos nuevos al inicio de la clase:

```kotlin
    private val locatorStore: LocatorStore = PrefsLocatorStore(app)
    private val settingsStore: SettingsStore = PrefsSettingsStore(app)
```

2. Inicializar los ajustes desde disco: cambiar `MutableStateFlow(ReaderSettings())` por `MutableStateFlow(settingsStore.load())`. Como `_settings` se declara después de `settingsStore`, respetar ese orden en el archivo.
3. En `update`, guardar:

```kotlin
    private fun update(transform: (ReaderSettings) -> ReaderSettings) {
        _settings.value = transform(_settings.value).also(settingsStore::save)
    }
```

4. En `open`, al construir la sesión, cargar la posición guardada:

```kotlin
                    val bookId = publication.metadata.identifier ?: uri.toString()
                    _state.value = ReaderState.Ready(
                        ReaderSession(
                            publication,
                            EpubNavigatorFactory(publication),
                            bookId,
                            initialLocator = locatorStore.load(bookId),
                        ),
                    )
```

5. En `onLocatorChanged`, guardar:

```kotlin
    fun onLocatorChanged(locator: Locator) {
        _progress.value = progressPercent(locator.locations.totalProgression)
        session?.let { locatorStore.save(it.bookId, locator) }
    }
```

- [ ] **Paso 5: Ejecutar todo**

```bash
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest
```
Esperado: todo en verde, incluido `reopeningBookRestoresPosition`.

- [ ] **Paso 6: Commit**

```bash
git add -A
git commit -m "feat: recordar posición de lectura y ajustes en el dispositivo (RDR-006)" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Tarea 7: Verificación manual con tu EPUB y cierre de docs (tarjeta K-007)

**Archivos:**
- Modificar: `specs/open-questions.md`, `specs/platforms/android.md`, `KANBAN.md`
- Colocar: tu EPUB de ejemplo en `code/android/samples/` (la carpeta está en `.gitignore`: el libro no se sube al repo)

- [ ] **Paso 1: Instalar en el emulador y copiar el EPUB**

```bash
cd code/android
./gradlew :app:installDebug
~/Library/Android/sdk/platform-tools/adb push samples/<tu-libro>.epub /sdcard/Download/
```

- [ ] **Paso 2: Recorrer esta lista en el emulador** (marcar cada punto)

- [ ] Abrir el libro con el botón "Abrir EPUB". Se ve la portada o el primer capítulo, sin errores.
- [ ] Paginado: pasar de página tocando los bordes. Scroll: cambiar con el botón y desplazar.
- [ ] Temas claro, oscuro y sepia: cambian el fondo y el texto.
- [ ] A− y A+: cambian el tamaño; no pasan de los límites.
- [ ] Índice: abre, lleva al capítulo elegido. Si el libro no tiene, muestra el mensaje.
- [ ] El porcentaje avanza al leer.
- [ ] Rotar la pantalla en medio de la lectura: sigue en el mismo lugar.
- [ ] Cerrar con "atrás", reabrir el libro: vuelve a la misma posición, con los mismos ajustes.
- [ ] Modo avión activado: el libro abre y se lee igual.
- [ ] Abrir un archivo que no sea EPUB (renombrar un `.txt` a `.epub`): mensaje claro, sin crash.
- [ ] Abrir el EPUB desde el administrador de archivos con "Abrir con": funciona.

Anotar en `KANBAN.md` (sección Notas) cualquier fallo encontrado como tarjeta nueva en Backlog.

- [ ] **Paso 3: Actualizar los specs con lo decidido**

En `specs/open-questions.md`, quitar la fila 3 (versión mínima de Android) si se confirmó `minSdk 26`. En `specs/platforms/android.md`, reemplazar la sección "Pendiente" por:

```markdown
## Decisiones

- Versión mínima de Android: 26 (Android 8.0). Idiomas de la interfaz pendientes (open-questions #4).
```

- [ ] **Paso 4: Mover las tarjetas K-001 a K-007 a "Hecho" y commit**

```bash
cd /Users/santilod/dev/pluk/reader
git add -A
git commit -m "docs: cerrar rebanada del visor de EPUB en Android" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

## Auto-revisión

- **Cobertura del spec:** RDR-001 (T4), RDR-002 parcial (T4: tamaño; tipo de letra, interlineado y márgenes quedan para después), RDR-003 (T4), RDR-004 (T5), RDR-005 (T5), RDR-006 local (T6), RDR-007 (T3 y T7), RDR-008 (los ajustes se guardan en el dispositivo en T6, la sincronización con la cuenta llega con la rebanada de sync), LIB-001 parcial (T3: selector y "Abrir con"), LIB-002 (T2 y T3), AND-001 y AND-003 parcial (T3).
- **Fuera de este plan:** biblioteca y colecciones (LIB-003 a 009), anotaciones, cuenta, sincronización, cumplimiento con la tienda. Cada una tendrá su propio plan.
- **Riesgo conocido:** los nombres de la API de Readium 3.4.0 salen de su documentación oficial y de memoria del autor del plan. Los pasos de compilación y prueba lo detectan. Si un nombre difiere, corregir según la documentación de esa versión sin cambiar el diseño ni las interfaces del plan.
- **Consistencia de tipos:** `ReaderSession`, `ReaderState`, `ReaderSettings`, `TocEntry`, `LocatorStore` y `SettingsStore` se definen una sola vez y se usan con las mismas firmas en las tareas siguientes. Archivos nuevos respecto a la estructura inicial: `reader/Progress.kt` (Tarea 5).
