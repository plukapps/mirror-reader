# Íconos "libros parados" — iOS y Android

Colores: amarillo `#F7C34B`, tinta `#1C0A06`, fondo oscuro `#1A1714`.

## Qué versión va dónde

| Versión | Uso |
|---|---|
| Amarillo con libros negros | Ícono principal en iOS y Android, App Store y Play Store |
| Modo oscuro | Ícono de iOS en modo oscuro |
| Solo libros, fondo blanco | Material de marketing y web (no se usa en las apps) |
| Transparente | Capa del ícono adaptable de Android, ícono monocromo, notificaciones y pantalla de inicio |

## iOS

**`ios/AppIcon.appiconset`** (Xcode 16 o posterior)
1. En Xcode, abrí `Assets.xcassets`, borrá el `AppIcon` actual y arrastrá esta carpeta.
2. Trae las tres apariencias de iOS 18 o posterior: clara, oscura y tintada. El sistema elige la que corresponde.

**`ios/AppIcon-todos-los-tamanos.appiconset`**: usala en su lugar si el proyecto está en Xcode 14 o anterior, o si una herramienta pide todos los tamaños sueltos (20 a 180 px, más 1024).

**`ios/LaunchLogo.imageset`**: los libros sin fondo en @1x, @2x y @3x para la pantalla de inicio (Launch Screen).

Los PNG de 1024 no tienen canal alfa, como exige App Store Connect. iOS redondea las esquinas solo, por eso los íconos son cuadrados.

**Mac (`mac/`)**: macOS 14 no recorta el ícono, así que el de Mac va ya redondeado (824 × 824 sobre 1024, con sombra). `make_mac_icon.swift` arma los 10 tamaños desde `originales/libros-amarillo-1024.png`:

```
swift design/icons/mac/make_mac_icon.swift design/icons/originales/libros-amarillo-1024.png design/icons/mac
```

## Android

**`android/app/src/main/res/`**: copiá su contenido sobre el `res/` de tu app (reemplazá los `ic_launcher` existentes).

- `mipmap-anydpi-v26/ic_launcher.xml` y `ic_launcher_round.xml`: ícono adaptable (Android 8 o posterior). Fondo amarillo y libros como vector, dentro de la zona segura de 66 dp.
- `drawable/ic_launcher_monochrome.xml`: ícono temático de Android 13 o posterior. El sistema lo tiñe con los colores del fondo de pantalla y cubre también el modo oscuro.
- `mipmap-*dpi/ic_launcher.png` y `ic_launcher_round.png`: íconos clásicos para Android 7.1 o anterior (48 a 192 px).
- `drawable/ic_stat_libros.xml`: ícono de notificación, blanco sobre transparente. Se usa con `setSmallIcon(R.drawable.ic_stat_libros)`.
- `values/ic_launcher_background.xml`: el color amarillo del fondo.

En el `AndroidManifest.xml`, la app debe tener:

```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```

**Pantalla de inicio (Android 12 o posterior)**: podés usar `@drawable/ic_launcher_foreground` como `windowSplashScreenAnimatedIcon` y `@color/ic_launcher_background` como `windowSplashScreenBackground`.

**`android/capas-png/`**: la capa de los libros en PNG por densidad, por si algún plugin la pide como imagen en vez de vector.

**`android/play-store-512.png`**: ícono para la ficha de Google Play (512×512, sin alfa).

## Flutter / React Native / Expo

Si usás un generador (como `flutter_launcher_icons` o el `icon` de Expo), alimentalo con:
- Ícono: `originales/libros-amarillo-1024.png`
- Capa adaptable de Android: `originales/android-capa-adaptable-1024.png` con fondo `#F7C34B` (ya trae el margen de la zona segura)
- Monocromo de Android: `originales/android-monocromo-1024.png`
- iOS oscuro: `originales/libros-modo-oscuro-1024.png`

Los SVG en `originales/` sirven para escalar a cualquier tamaño.
