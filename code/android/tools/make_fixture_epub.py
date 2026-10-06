#!/usr/bin/env python3
"""Genera un EPUB 3 mínimo y válido para pruebas. Uso: make_fixture_epub.py <salida.epub>"""
import sys
import zipfile

CONTAINER = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
"""

OPF = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="bookid">urn:uuid:11111111-2222-3333-4444-555555555555</dc:identifier>
    <dc:title>Libro de prueba</dc:title>
    <dc:creator>Autor de prueba</dc:creator>
    <dc:language>es</dc:language>
    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="c1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
    <item id="c2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine>
    <itemref idref="c1"/>
    <itemref idref="c2"/>
  </spine>
</package>
"""

NAV = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
  <head><title>Contenido</title></head>
  <body>
    <nav epub:type="toc">
      <ol>
        <li><a href="ch1.xhtml">Capítulo 1</a></li>
        <li><a href="ch2.xhtml">Capítulo 2</a></li>
      </ol>
    </nav>
  </body>
</html>
"""


def chapter(title: str) -> str:
    paragraphs = "\n".join(
        f"    <p>{title}: párrafo {i}. Lorem ipsum dolor sit amet, consectetur adipiscing elit, "
        f"sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.</p>"
        for i in range(1, 41)
    )
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml">
  <head><title>{title}</title></head>
  <body>
    <h1>{title}</h1>
{paragraphs}
  </body>
</html>
"""


def main(out: str) -> None:
    with zipfile.ZipFile(out, "w") as z:
        # El archivo mimetype va primero y sin comprimir, como exige el estándar.
        z.writestr("mimetype", "application/epub+zip", compress_type=zipfile.ZIP_STORED)
        z.writestr("META-INF/container.xml", CONTAINER, compress_type=zipfile.ZIP_DEFLATED)
        z.writestr("OEBPS/content.opf", OPF, compress_type=zipfile.ZIP_DEFLATED)
        z.writestr("OEBPS/nav.xhtml", NAV, compress_type=zipfile.ZIP_DEFLATED)
        z.writestr("OEBPS/ch1.xhtml", chapter("Capítulo 1"), compress_type=zipfile.ZIP_DEFLATED)
        z.writestr("OEBPS/ch2.xhtml", chapter("Capítulo 2"), compress_type=zipfile.ZIP_DEFLATED)


if __name__ == "__main__":
    main(sys.argv[1])
