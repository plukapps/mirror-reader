// Arma el ícono de Mac desde el ícono cuadrado de 1024 (WEL-009, K-341).
//
// macOS 14 no recorta el ícono como iOS: hay que entregarlo ya con la forma redondeada y el
// margen de la grilla de macOS (824 × 824 sobre un lienzo de 1024, esquinas de 185, sombra suave).
//
// Uso, desde la raíz del repo:
//   swift design/icons/mac/make_mac_icon.swift design/icons/originales/libros-amarillo-1024.png design/icons/mac
// Escribe icon_16x16.png … icon_512x512@2x.png, los nombres que usa AppIcon.appiconset.

import AppKit
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

let args = CommandLine.arguments
guard args.count == 3 else {
    FileHandle.standardError.write("uso: make_mac_icon.swift <ícono-1024.png> <carpeta-de-salida>\n".data(using: .utf8)!)
    exit(1)
}
let inputURL = URL(fileURLWithPath: args[1])
let outputDir = URL(fileURLWithPath: args[2], isDirectory: true)

guard let source = CGImageSourceCreateWithURL(inputURL as CFURL, nil),
      let square = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
    FileHandle.standardError.write("no se pudo leer \(inputURL.path)\n".data(using: .utf8)!)
    exit(1)
}

let canvas: CGFloat = 1024
let body: CGFloat = 824
let radius: CGFloat = 185
let colorSpace = CGColorSpace(name: CGColorSpace.sRGB)!

/// El ícono de 1024 con la forma de macOS, sobre fondo transparente.
func masterIcon() -> CGImage {
    let ctx = CGContext(data: nil, width: Int(canvas), height: Int(canvas), bitsPerComponent: 8, bytesPerRow: 0,
                        space: colorSpace, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    ctx.interpolationQuality = .high
    let inset = (canvas - body) / 2
    // La sombra de la grilla de macOS cae hacia abajo; en CoreGraphics el eje y crece hacia arriba.
    let rect = CGRect(x: inset, y: inset + 6, width: body, height: body)
    let shape = CGPath(roundedRect: rect, cornerWidth: radius, cornerHeight: radius, transform: nil)

    ctx.saveGState()
    ctx.setShadow(offset: CGSize(width: 0, height: -10), blur: 24, color: CGColor(gray: 0, alpha: 0.3))
    ctx.addPath(shape)
    ctx.setFillColor(CGColor(gray: 0, alpha: 1))
    ctx.fillPath()
    ctx.restoreGState()

    ctx.addPath(shape)
    ctx.clip()
    ctx.draw(square, in: rect)
    return ctx.makeImage()!
}

func write(_ image: CGImage, size: Int, to url: URL) {
    let ctx = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0,
                        space: colorSpace, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    ctx.interpolationQuality = .high
    ctx.draw(image, in: CGRect(x: 0, y: 0, width: size, height: size))
    let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(dest, ctx.makeImage()!, nil)
    guard CGImageDestinationFinalize(dest) else {
        FileHandle.standardError.write("no se pudo escribir \(url.path)\n".data(using: .utf8)!)
        exit(1)
    }
}

try FileManager.default.createDirectory(at: outputDir, withIntermediateDirectories: true)
let master = masterIcon()
for points in [16, 32, 128, 256, 512] {
    for scale in [1, 2] {
        let name = scale == 1 ? "icon_\(points)x\(points).png" : "icon_\(points)x\(points)@2x.png"
        write(master, size: points * scale, to: outputDir.appendingPathComponent(name))
        print(name)
    }
}
