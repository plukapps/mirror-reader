# Plan — Entornos de build en Apple (dev, stage, beta, prod)

**Objetivo:** lo mismo que en Android (plan `2026-10-16-android-environments.md`) para el target de Mac e iPhone: cuatro apps que se instalan juntas, con la configuración de cada entorno en un solo lugar de `project.yml`.

**Specs:** APL-001 y APL-002 (nuevos, `specs/platforms/apple.md`). **Decisión técnica:** ADR 0021.
**Rama:** `claude/ios-environments`, desde `claude/android-multi-environment-setup-aa0245` (PR de Android). PR contra esa rama hasta que la de Android se integre a `develop`. **Tarjetas:** K-379 a K-383 (eran K-378 a K-382: `develop` ya usaba K-370 y Android corrió las suyas).
**Pedido del usuario (2026-10-16):** rama y PR nuevos para iOS, partiendo de los entornos de Android.

## Tareas

### K-379 — ADR, requisitos y plan (S)
- ADR 0021, APL-001 y APL-002 en `apple.md`, este plan y las tarjetas.

### K-380 — Configuraciones, esquemas y Firebase por entorno en `project.yml` (M)
- `configs` `Dev`/`Stage` (debug) y `Beta`/`Prod` (release); ajustes por configuración: `APP_ENVIRONMENT`, `ENV_BUNDLE_SUFFIX`, nombre visible, ícono y `GOOGLE_REVERSED_CLIENT_ID`.
- Esquemas `Reader` (Dev), `Reader Stage`, `Reader Beta`, `Reader Prod`.
- Clave `AppEnvironment` en los dos `Info.plist`.
- Fase que copia `App/Firebase/GoogleService-Info-<Configuración>.plist`; Crashlytics usa el mismo archivo. `.gitignore` y `copy-google-client.sh` por entorno.
- **Terminado:** compilan los cuatro esquemas en el simulador y `Reader` en la Mac.

### K-381 — `AppEnvironment` en el dominio y en Perfil (S)
- `ReaderDomain`: enum `AppEnvironment` con `init?(id:)`, `isProduction` y `versionLabel(_:)`.
- `AppGraph` lo lee del `Info.plist` (falla con un valor desconocido); Perfil muestra la versión con el entorno.
- Tests: `AppEnvironmentTests` en el dominio (APL-002); en la app, `BuildEnvironmentTests` (en el esquema `Reader`, el entorno es `dev`, el bundle ID termina en `.dev` y el build es de depuración).
- **Terminado:** `swift test` y los tests de la app en iPhone en verde.

### K-382 — Ícono con la etiqueta del entorno (S)
- `tools/make_env_icons.swift`: lee `AppIcon.appiconset` y escribe `AppIcon-Dev`, `AppIcon-Stage` y `AppIcon-Beta` con la etiqueta (iPhone claro, oscuro y tintado; los diez de Mac).
- **Terminado:** las imágenes revisadas a ojo; los catálogos compilan sin avisos.

### K-383 — Apps en Firebase, docs y verificación en el simulador
- Registrar `com.pluk.reader.dev`, `.stage` y `.beta` como apps de iOS en `mirror-reading-staging` (MCP de Firebase, como en Android) y bajar los cuatro archivos a `App/Firebase/` del checkout principal.
- README, AGENTS y `apple.md`.
- En el simulador: Dev, Beta y Prod instaladas juntas, con nombre e ícono propios; Perfil muestra el entorno.

## Fuera de alcance
- Proyectos de Firebase separados por entorno.
- Firma para distribuir y TestFlight (K-080).

## Riesgos
| Riesgo | Mitigación |
|--------|------------|
| Comandos y rutas viejas (`Debug-iphonesimulator`) en docs y en la memoria de los agentes | README y AGENTS actualizados en K-383 |
| Ajustes de los paquetes de Swift esperan `Debug`/`Release` | XcodeGen marca cada configuración como debug o release; los paquetes usan ese tipo |
| Sin los plist en otra máquina la app queda solo local | La fase lo avisa en el log del build; README lo explica |
