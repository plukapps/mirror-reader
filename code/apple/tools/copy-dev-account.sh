#!/bin/sh
# Copia la cuenta de desarrollo de Android (code/android/local.properties) a Signing.local.xcconfig,
# sin mostrarla. Solo se usa en builds Debug (ADR 0012). Correr desde code/apple.
set -eu

props="../android/local.properties"
target="Signing.local.xcconfig"

[ -f "$props" ] || { echo "No existe $props" >&2; exit 1; }
email=$(sed -n 's/^dev\.account\.email=//p' "$props")
password=$(sed -n 's/^dev\.account\.password=//p' "$props")
[ -n "$email" ] && [ -n "$password" ] || { echo "Faltan dev.account.email o dev.account.password en $props" >&2; exit 1; }

# En un xcconfig "//" empieza un comentario y "$(" una variable: esos valores no se pueden copiar tal cual.
case "$email$password" in
  *//*|*'$('*) echo "La cuenta tiene \"//\" o \"\$(\"; cargala a mano en $target" >&2; exit 1 ;;
esac

touch "$target"
grep -v '^DEV_ACCOUNT_\(EMAIL\|PASSWORD\) *=' "$target" > "$target.tmp" || true
printf 'DEV_ACCOUNT_EMAIL = %s\nDEV_ACCOUNT_PASSWORD = %s\n' "$email" "$password" >> "$target.tmp"
mv "$target.tmp" "$target"
chmod 600 "$target"
echo "Cuenta de desarrollo copiada a $target."
