#!/bin/sh
# Copia el REVERSED_CLIENT_ID de cada App/Firebase/GoogleService-Info-<Entorno>.plist a Signing.local.xcconfig como
# GOOGLE_REVERSED_CLIENT_ID_<ENTORNO>: es el esquema de URL con el que Google vuelve a la app al iniciar sesión
# (ADR 0015), distinto en cada entorno porque cada uno es otra app (ADR 0021). Ninguno de los archivos va al repo.
# Correr desde code/apple después de bajar los GoogleService-Info, y luego `xcodegen`.
set -eu

target="Signing.local.xcconfig"
touch "$target"
found=0
for env in Dev Stage Beta Prod; do
  plist="App/Firebase/GoogleService-Info-$env.plist"
  key="GOOGLE_REVERSED_CLIENT_ID_$(echo "$env" | tr '[:lower:]' '[:upper:]')"
  [ -f "$plist" ] || { echo "Sin $plist: se saltea $key"; continue; }
  reversed=$(/usr/libexec/PlistBuddy -c "Print :REVERSED_CLIENT_ID" "$plist" 2>/dev/null) || {
    echo "$plist no tiene REVERSED_CLIENT_ID: activá Google en Firebase Authentication y bajalo de nuevo" >&2
    exit 1
  }
  grep -v "^$key *=" "$target" > "$target.tmp" || true
  echo "$key = $reversed" >> "$target.tmp"
  mv "$target.tmp" "$target"
  echo "Listo: $key"
  found=1
done
[ "$found" = 1 ] || { echo "No hay ningún App/Firebase/GoogleService-Info-*.plist (ver README.md)" >&2; exit 1; }
