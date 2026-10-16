# Plan — Entornos de build en Android (dev, stage, prod)

**Objetivo:** tres variantes de la app Android que se instalan juntas (`com.pluk.reader.dev`, `.stage` y `com.pluk.reader`), con la configuración de cada entorno en un solo lugar del build, lista para apuntar a servidores distintos más adelante.

**Specs:** AND-007 y AND-008 (nuevos, `specs/platforms/android.md`). **Decisión técnica:** ADR 0020.
**Rama:** `claude/android-multi-environment-setup-aa0245`, desde `develop`. PR contra `develop`. **Tarjetas:** K-370 a K-376.
**Pedido del usuario (2026-10-16):** entornos debug (dev), stage y producción con paquetes distintos; primero Android, después iOS. Decisiones: flavors de entorno cruzados con debug/release; los tres contra `mirror-reading-staging` por ahora; sufijos `.dev` y `.stage`. Después: que registre yo los paquetes con el MCP de Firebase (K-373), el PR, y un ícono con etiqueta por entorno (K-375).

## Tareas

### K-370 — ADR, requisitos y plan (S)
- ADR 0020, AND-007 y AND-008 en `android.md`, este plan y las tarjetas.
- **Terminado:** el usuario aprueba el plan.

### K-371 — Flavors en Gradle (S)
- `flavorDimensions += "environment"` y `productFlavors` `dev` (`applicationIdSuffix = ".dev"`), `stage` (`".stage"`) y `prod` en `app/build.gradle.kts`.
- `buildConfigField("String", "ENVIRONMENT", ...)` y `authLinkHost` (placeholder del manifiesto y `resValue`) por flavor; hoy los tres usan `mirror-reading-staging.firebaseapp.com`.
- `app_name` en `src/dev/res/values/strings.xml` ("Tuck Dev") y `src/stage/res/values/strings.xml` ("Tuck Stage").
- **Terminado:** `./gradlew :app:assembleDevDebug :app:assembleStageDebug :app:assembleProdDebug` compila (con el `google-services.json` nuevo de K-373; hasta tenerlo, solo `prodDebug`). `aapt dump badging` muestra el paquete y el nombre de cada APK.
- **Depende de:** K-370. Para `dev` y `stage`, de K-373.

### K-372 — `AppEnvironment` en el dominio y en Perfil (S)
- `domain/environment/AppEnvironment.kt`: enum `DEV`, `STAGE`, `PROD` con `fromName(String)` (falla con un nombre desconocido) y `isProduction`.
- `di`: `EnvironmentModule` provee `AppEnvironment.fromName(BuildConfig.ENVIRONMENT)`.
- Perfil: la versión muestra `1.0 · dev` o `1.0 · stage`; en `prod` solo `1.0`.
- Tests JVM (AND-008): `AppEnvironmentTest` (nombres válidos, desconocido falla, `isProduction`), `BuildEnvironmentTest` (en `devDebug`, `BuildConfig.ENVIRONMENT` es `dev` y `APPLICATION_ID` termina en `.dev`), y el texto de la versión de Perfil.
- **Terminado:** `./gradlew :app:testDevDebugUnitTest` en verde.
- **Depende de:** K-371.

### K-373 — Registrar los paquetes nuevos en Firebase (manual, con el usuario)
- En `mirror-reading-staging`: apps Android `com.pluk.reader.dev` y `com.pluk.reader.stage`, con el SHA-1 de debug de la máquina (para Google, ADR 0014).
- Bajar el `google-services.json` nuevo (con los tres clientes) a `code/android/app/`. No va al repo.
- Es una acción sobre el proyecto real: la hace el usuario, o yo con su permiso explícito.

### K-374 — Docs y verificación a mano en el teléfono
- AGENTS.md (comandos de Gradle y de `adb` con `.dev`), `android.md` (decisión), `code/android` README si aplica, comentario de `block_external_links` en `ReaderScreen.kt`.
- En el teléfono: `devDebug` y `prodDebug` instalados juntos, con nombres distintos en el lanzador; Perfil muestra `· dev` solo en la primera; ingreso con Google en `dev`.
- `connectedDevDebugAndroidTest` en el emulador si hay uno; si no, se avisa y queda pendiente (no se corre en el teléfono sin preguntar).

### K-375 — Ícono con la etiqueta del entorno (S)
- Pedido del usuario (2026-10-16), con el ícono de Chrome Beta como ejemplo: etiqueta oscura con texto blanco sobre el ícono de cada variante que no se publica. `dev` → DEV, `stage` → STAGE, `prodDebug` → DEBUG; `prodRelease` sin etiqueta.
- `tools/make_env_icons.py` toma `ic_launcher_foreground.xml` e `ic_launcher_monochrome.xml` de `main` y escribe la copia con la etiqueta en `src/<variante>/res/drawable/`. El texto son trazados de Host Grotesk Bold (sin PNG). La etiqueta entra en la zona segura de 66 dp; los libros se recortan alrededor de ella. En el ícono temático las letras quedan caladas.
- **Terminado:** las cuatro variantes compilan; vista previa revisada (máscara redonda, temático y tamaño chico); `devDebug` y `stageDebug` instalados en el SM-S711B.

### K-376 — Beta y un tipo de build por entorno (S)
- Pedido del usuario (2026-10-16): dev y stage en debug; un entorno nuevo, beta, en release; prod en release. Nada más por ahora; más adelante dev, stage o beta pueden apuntar a otro servidor.
- Flavor `beta` (`com.pluk.reader.beta`, "Tuck Beta", etiqueta BETA, firmado con la clave de debug), mapa `ENVIRONMENT_BUILD_TYPE` y `beforeVariants` que apaga el resto. `prodDebug` y su ícono DEBUG se van.
- `AppEnvironment.BETA`; `BuildEnvironmentTest` comprueba que dev y stage son depurables. Beta y prod se revisan en el APK (AGP 9 no arma tests JVM de release).
- App `com.pluk.reader.beta` en `mirror-reading-staging` con el SHA-1 de debug (MCP de Firebase).
- **Terminado:** solo existen `devDebug`, `stageDebug`, `betaRelease` y `prodRelease`; tests JVM en verde en dev y stage; Tuck Beta instalada en el teléfono.

## Fuera de alcance
- Proyecto de Firebase de producción separado (tarjeta nueva en Backlog cuando se decida).
- Clave de firma de release y publicación (beta usa la de debug mientras tanto).
- iOS (plan propio después).

## Riesgos
| Riesgo | Mitigación |
|--------|------------|
| El `google-services.json` actual (un solo cliente) no compila `dev` ni `stage` | K-373 antes de dar por cerrada K-371; mientras tanto se verifica con `prodDebug` |
| Al pasar a `devDebug` la app arranca vacía (otro paquete) | Esperado; documentado en el ADR. La variante vieja se puede desinstalar o queda como `prod` |
| Google sin el SHA-1 en las apps nuevas | Parte de K-373; se prueba en K-374 |

## Resultado
- 2026-10-16: `com.pluk.reader.dev` y `com.pluk.reader.stage` registradas en `mirror-reading-staging` (MCP de Firebase) con el SHA-1 de debug; el `google-services.json` nuevo trae los tres clientes con su cliente OAuth. Las cuatro variantes compilan; 542 tests JVM en verde en `devDebug`, `stageDebug` y `prodDebug`. `devDebug` y `stageDebug` instalados en el SM-S711B junto a la app que ya estaba; `devDebug` arranca sin cierres.
- Pendiente (K-374): mirar el lanzador, Perfil (`· dev`) e ingreso con Google en dev; `connectedDevDebugAndroidTest` en un emulador.
- 2026-10-16 (K-376): `beta` agregado; solo existen `devDebug`, `stageDebug`, `betaRelease` (no depurable, firmada con el SHA-1 de debug) y `prodRelease` (sin firmar). 543 tests JVM en verde en dev y stage. Tuck Beta, Dev y Stage instaladas en el SM-S711B; Beta abre sin cierres.
