#!/usr/bin/env python3
"""Crea Tests/Fixtures/fixture-cover.epub: el fixture.epub de siempre con una portada PNG (LIB-012).

Uso: python3 tools/make_cover_fixture.py Tests/Fixtures/fixture.epub Tests/Fixtures/fixture-cover.epub
"""
import struct
import sys
import zipfile
import zlib


def png(width: int, height: int) -> bytes:
    raw = b"".join(b"\x00" + bytes([139, 30, 30]) * width for _ in range(height))

    def chunk(kind: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")


def main(source: str, out: str) -> None:
    with zipfile.ZipFile(source) as src, zipfile.ZipFile(out, "w") as dst:
        # El mimetype va primero y sin comprimir (OCF).
        dst.writestr(zipfile.ZipInfo("mimetype"), src.read("mimetype"), compress_type=zipfile.ZIP_STORED)
        for name in src.namelist():
            if name == "mimetype":
                continue
            data = src.read(name)
            if name == "OEBPS/content.opf":
                data = data.replace(
                    b"<manifest>",
                    b'<manifest>\n    <item id="cover" href="cover.png" media-type="image/png" properties="cover-image"/>',
                )
            dst.writestr(name, data, compress_type=zipfile.ZIP_DEFLATED)
        dst.writestr("OEBPS/cover.png", png(400, 600), compress_type=zipfile.ZIP_DEFLATED)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
