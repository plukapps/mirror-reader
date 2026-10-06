# 02 — Lector

## Objetivo

Leer cómodo, sin que la interfaz estorbe.

## Requisitos

- **RDR-001** Debe ofrecer modo paginado y modo scroll.
- **RDR-002** Debe permitir ajustar tamaño y tipo de letra (conjunto corto), interlineado y márgenes.
- **RDR-003** Debe ofrecer temas claro, oscuro y sepia.
- **RDR-004** Debe mostrar tabla de contenidos y permitir saltar a un capítulo o a un porcentaje.
- **RDR-005** Debe mostrar el progreso de lectura.
- **RDR-006** Debe guardar la posición automáticamente (ubicación exacta en el EPUB y porcentaje).
- **RDR-007** Debe funcionar sin conexión para libros descargados.
- **RDR-008** Las preferencias de lectura (letra, tema) se sincronizan con la cuenta.

## Escenarios

- Dado que cierro un libro a mitad, cuando lo reabro, entonces vuelvo a la misma posición.
- Dado que cambio el tema a oscuro, cuando abro la app en otro dispositivo, entonces también está oscuro.

## Fuera de alcance

Diccionario, text-to-speech, brillo propio, EPUB de maquetación fija.
