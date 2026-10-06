# 03 — Anotaciones

## Objetivo

Marcar y comentar lo leído, sin perderlo nunca.

## Requisitos

- **ANN-001** Debe permitir marcadores en un punto del libro.
- **ANN-002** Debe permitir subrayar texto seleccionado en 4 colores.
- **ANN-003** Debe permitir notas de texto libre, asociadas a un subrayado o a un punto del libro.
- **ANN-004** Debe tener un panel por libro que lista marcadores, subrayados y notas. Tocar uno lleva al lugar exacto.
- **ANN-005** Debe permitir editar y borrar anotaciones.
- **ANN-006** Cada subrayado guarda su ubicación en el EPUB y el texto citado, para poder reubicarlo.
- **ANN-007** Todo funciona sin conexión y se sincroniza después (ver SYN).

## Escenarios

- Dado que subrayo texto sin conexión, cuando me conecto, entonces el subrayado aparece en mis otros dispositivos.
- Dado un subrayado cuya ubicación ya no resuelve, cuando abro el panel, entonces se muestra con el texto citado y no se pierde.

## Fuera de alcance

Exportar anotaciones (ver open-questions #5), compartir anotaciones.
