# 06 — Cumplimiento con tiendas

## Objetivo

Poder publicar y mantener la app en Google Play y App Store.

## Requisitos

- **CMP-001** Debe permitir eliminar la cuenta desde dentro de la app, con borrado real de datos y archivos.
- **CMP-002** Debe tener política de privacidad pública y declarar los datos recolectados.
- **CMP-003** Debe tener términos de uso que indiquen que el usuario es responsable del contenido que sube.
- **CMP-004** Debe tener un canal de contacto para reclamos de derechos de autor.
- **CMP-005** No debe incluir ni vender contenido propio.
- **CMP-006** Debe rechazar EPUB con DRM y no intentar eliminarlo.
- **CMP-007** Datos cifrados en tránsito y en reposo, con aislamiento entre usuarios.
- **CMP-008** Debe permitir exportar los datos del usuario (libros y anotaciones).
- **CMP-009** No debe analizar el contenido de libros ni notas.
- **CMP-010** Debe registrar los cierres inesperados de la app para poder corregirlos. El registro identifica la cuenta solo por su identificador interno (nunca el email ni el nombre), se deja de asociar a la cuenta al cerrar sesión, no incluye contenido de libros ni notas (CMP-009) y se declara en la política de privacidad (CMP-002).

## Escenarios

- Dado que elimino mi cuenta, cuando confirmo, entonces mis datos y archivos se borran y no pueden recuperarse.

- Dado que la app se cierra de forma inesperada, cuando vuelve a abrirse con conexión, entonces el cierre queda registrado con el identificador interno de la cuenta, sin el email ni el contenido del libro.
- Dado que cierro sesión, cuando la app se cierra de forma inesperada, entonces el registro no queda asociado a mi cuenta.

## Nota

Las políticas de las tiendas cambian. Revisarlas antes de cada publicación.
