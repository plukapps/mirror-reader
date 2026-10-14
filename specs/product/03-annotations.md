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
- **ANN-007** Todo funciona sin conexión y se sincroniza después (ver SYN; las notas, SYN-016 y SYN-017).
- **ANN-008** Al seleccionar texto en el lector, el menú de selección debe ofrecer "Nota". Elegirla abre un editor; al guardar, el texto queda subrayado y la nota guardada. Cancelar no guarda nada. Diseño: "06 — Reader · Classic + highlight".
- **ANN-009** Tocar un subrayado con nota en el lector debe mostrar la nota con el texto citado, y permitir editarla o borrarla. Borrar pide confirmación y quita también el subrayado.
- **ANN-010** Las notas de un libro se ven en su detalle (LIB-022), ordenadas por su lugar en el libro. Cada una muestra el texto citado, la nota y el porcentaje del libro donde está. Tocar una abre el libro en ese lugar (ANN-004). Diseño: "T04 — Reader · Night + notes panel" (la lista).
- **ANN-011** Quitar un libro de la biblioteca (LIB-020) borra también sus notas en este dispositivo.

## Escenarios

- Dado que subrayo texto sin conexión, cuando me conecto, entonces el subrayado aparece en mis otros dispositivos.
- Dado un subrayado cuya ubicación ya no resuelve, cuando abro el panel, entonces se muestra con el texto citado y no se pierde.

- Dado que selecciono una frase y elijo "Nota", cuando escribo y guardo, entonces la frase queda subrayada y la nota aparece en el detalle del libro.
- Dado una nota en el detalle del libro, cuando la toco, entonces el lector abre en la página de esa frase, también si la última posición de lectura es otra.
- Dado una nota, cuando la borro desde el lector, entonces desaparece el subrayado y ya no está en el detalle.

## Fuera de alcance

Exportar anotaciones (ver open-questions #5), compartir anotaciones.
