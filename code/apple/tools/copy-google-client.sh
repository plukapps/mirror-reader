#!/bin/sh
# Copia el REVERSED_CLIENT_ID de App/GoogleService-Info.plist a Signing.local.xcconfig: es el esquema de URL con el
# que Google vuelve a la app al iniciar sesión (ADR 0015). Ninguno de los dos archivos va al repo. Correr desde
# code/apple después de bajar GoogleService-Info.plist, y luego `xcodegen`.
set -eu

plist="App/GoogleService-Info.plist"
target="Signing.local.xcconfig"

[ -f "$plist" ] || { echo "No existe $plist (ver README.md)" >&2; exit 1; }
reversed=$(/usr/libexec/PlistBuddy -c "Print :REVERSED_CLIENT_ID" "$plist" 2>/dev/null) || {
  echo "$plist no tiene REVERSED_CLIENT_ID: activá Google en Firebase Authentication y bajalo de nuevo" >&2
  exit 1
}

touch "$target"
grep -v '^GOOGLE_REVERSED_CLIENT_ID *=' "$target" > "$target.tmp" || true
echo "GOOGLE_REVERSED_CLIENT_ID = $reversed" >> "$target.tmp"
mv "$target.tmp" "$target"
echo "Listo: GOOGLE_REVERSED_CLIENT_ID en $target"
