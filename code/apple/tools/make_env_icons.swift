// Íconos de la app con la etiqueta del entorno (APL-001, ADR 0021), como Chrome Beta y como Android (ADR 0020).
//
// Lee App/Assets.xcassets/AppIcon.appiconset (iPhone claro, oscuro y tintado; los diez tamaños de Mac) y escribe
// AppIcon-Dev, AppIcon-Stage y AppIcon-Beta con la etiqueta DEV, STAGE o BETA. Prod usa AppIcon sin cambios.
// El texto es Host Grotesk Bold de App/Fonts. Si cambia AppIcon, se vuelve a correr.
//
// Uso, desde code/apple:
//   swift tools/make_env_icons.swift

import AppKit
import CoreGraphics
import CoreText
import ImageIO
import UniformTypeIdentifiers

let environments = [("Dev", "DEV"), ("Stage", "STAGE"), ("Beta", "BETA")]
let assets = URL(fileURLWithPath: "App/Assets.xcassets", isDirectory: true)
let source = assets.appendingPathComponent("AppIcon.appiconset", isDirectory: true)
let fontURL = URL(fileURLWithPath: "App/Fonts/HostGrotesk.ttf")
let colorSpace = CGColorSpace(name: CGColorSpace.sRGB)!

func fail(_ message: String) -> Never {
    FileHandle.standardError.write((message + "\n").data(using: .utf8)!)
    exit(1)
}

func rgb(_ hex: UInt32) -> CGColor {
    CGColor(srgbRed: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
}

let ink = rgb(0x1C0A06)
let yellow = rgb(0xF7C34B)
let white = rgb(0xFFFFFF)
let black = rgb(0x000000)

/// Colores de la etiqueta en cada versión del ícono.
struct Style { let badge: CGColor; let text: CGColor }
let light = Style(badge: ink, text: white)
let dark = Style(badge: yellow, text: ink)
let tinted = Style(badge: white, text: black)

/// Geometría sobre el ícono de 1024 de iPhone (origen arriba a la izquierda): la misma proporción que en Android.
let badgeRect = CGRect(x: 150, y: 594, width: 724, height: 200)
let badgeRadius: CGFloat = 42
let gap: CGFloat = 22
let capHeight: CGFloat = 100
let maxTextWidth = badgeRect.width - 110

/// Mac: el ícono va dentro de la forma de macOS (make_mac_icon.swift): 824 × 824 en un lienzo de 1024.
let macBody = CGRect(x: 100, y: 94, width: 824, height: 824)
let macRadius: CGFloat = 185

let font: CTFont = {
    guard let descriptors = CTFontManagerCreateFontDescriptorsFromURL(fontURL as CFURL) as? [CTFontDescriptor],
          let base = descriptors.first else { fail("no se pudo leer \(fontURL.path)") }
    let weight = 0x7767_6874 // 'wght'
    let bold = CTFontDescriptorCreateCopyWithAttributes(
        base, [kCTFontVariationAttribute: [weight: 700]] as CFDictionary)
    return CTFontCreateWithFontDescriptor(bold, 100, nil)
}()

func load(_ url: URL) -> CGImage {
    guard let src = CGImageSourceCreateWithURL(url as CFURL, nil),
          let image = CGImageSourceCreateImageAtIndex(src, 0, nil) else { fail("no se pudo leer \(url.path)") }
    return image
}

func save(_ image: CGImage, to url: URL, opaque: Bool) {
    var output = image
    if opaque, let ctx = context(image.width, opaque: true) {
        // El ícono de iPhone no lleva transparencia.
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
        output = ctx.makeImage()!
    }
    let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(dest, output, nil)
    guard CGImageDestinationFinalize(dest) else { fail("no se pudo escribir \(url.path)") }
}

func context(_ size: Int, opaque: Bool = false) -> CGContext? {
    CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0, space: colorSpace,
              bitmapInfo: (opaque ? CGImageAlphaInfo.noneSkipLast : CGImageAlphaInfo.premultipliedLast).rawValue)
}

/// Pasa un rectángulo con origen arriba a la izquierda (como el diseño) al de CoreGraphics, que crece hacia arriba.
func flipped(_ r: CGRect, in size: CGFloat) -> CGRect {
    CGRect(x: r.minX, y: size - r.maxY, width: r.width, height: r.height)
}

/// Color del fondo del ícono: el píxel de arriba a la izquierda del área (sin los libros).
func backgroundColor(_ image: CGImage, at point: CGPoint) -> CGColor {
    let ctx = context(1)!
    ctx.draw(image, in: CGRect(x: -point.x, y: -(CGFloat(image.height) - point.y - 1),
                               width: CGFloat(image.width), height: CGFloat(image.height)))
    let p = ctx.data!.assumingMemoryBound(to: UInt8.self)
    return CGColor(srgbRed: CGFloat(p[0]) / 255, green: CGFloat(p[1]) / 255, blue: CGFloat(p[2]) / 255, alpha: 1)
}

/// Dibuja la etiqueta sobre `image` (1024). `area` es la zona del ícono en la que se ubica (todo el lienzo en iPhone,
/// el cuerpo redondeado en Mac). Los libros se tapan con el fondo alrededor de la etiqueta y hasta el borde de abajo.
func badged(_ image: CGImage, label: String, style: Style, area: CGRect, clip: CGPath?) -> CGImage {
    let size = CGFloat(image.width)
    let ctx = context(image.width)!
    ctx.interpolationQuality = .high
    ctx.draw(image, in: CGRect(x: 0, y: 0, width: size, height: size))

    let scale = area.width / 1024
    func place(_ r: CGRect) -> CGRect {
        flipped(CGRect(x: area.minX + r.minX * scale, y: area.minY + r.minY * scale,
                       width: r.width * scale, height: r.height * scale), in: size)
    }
    let badge = place(badgeRect)
    let hole = place(CGRect(x: badgeRect.minX - gap, y: badgeRect.minY - gap,
                            width: badgeRect.width + 2 * gap, height: 1024 - badgeRect.minY + gap + 200))
    let background = backgroundColor(image, at: CGPoint(x: area.minX + 40 * scale, y: area.maxY - 200 * scale))

    ctx.saveGState()
    if let clip { ctx.addPath(clip); ctx.clip() }
    let holeRadius = (badgeRadius + gap) * scale
    ctx.addPath(CGPath(roundedRect: hole, cornerWidth: holeRadius, cornerHeight: holeRadius, transform: nil))
    ctx.setFillColor(background)
    ctx.fillPath()
    ctx.restoreGState()

    ctx.addPath(CGPath(roundedRect: badge, cornerWidth: badgeRadius * scale, cornerHeight: badgeRadius * scale,
                       transform: nil))
    ctx.setFillColor(style.badge)
    ctx.fillPath()

    // Tamaño de letra para la altura de mayúscula pedida, sin pasar el ancho de la etiqueta.
    var pointSize = capHeight * scale / (CTFontGetCapHeight(font) / CTFontGetSize(font))
    func line(_ points: CGFloat) -> CTLine {
        let sized = CTFontCreateCopyWithAttributes(font, points, nil, nil)
        let text = NSAttributedString(string: label, attributes: [
            NSAttributedString.Key(kCTFontAttributeName as String): sized,
            NSAttributedString.Key(kCTForegroundColorAttributeName as String): style.text,
            NSAttributedString.Key(kCTKernAttributeName as String): points * 0.04,
        ])
        return CTLineCreateWithAttributedString(text)
    }
    var textLine = line(pointSize)
    let width = CTLineGetTypographicBounds(textLine, nil, nil, nil)
    if width > maxTextWidth * scale {
        pointSize *= maxTextWidth * scale / width
        textLine = line(pointSize)
    }
    let textWidth = CTLineGetTypographicBounds(textLine, nil, nil, nil)
    let cap = CTFontGetCapHeight(CTFontCreateCopyWithAttributes(font, pointSize, nil, nil))
    ctx.textPosition = CGPoint(x: badge.midX - textWidth / 2, y: badge.midY - cap / 2)
    CTLineDraw(textLine, ctx)
    return ctx.makeImage()!
}

func resized(_ image: CGImage, to size: Int) -> CGImage {
    let ctx = context(size)!
    ctx.interpolationQuality = .high
    ctx.draw(image, in: CGRect(x: 0, y: 0, width: size, height: size))
    return ctx.makeImage()!
}

let contents = source.appendingPathComponent("Contents.json")
let iphone: [(String, Style)] = [("AppIcon-1024.png", light), ("AppIcon-1024-dark.png", dark),
                                 ("AppIcon-1024-tinted.png", tinted)]
let macMaster = load(source.appendingPathComponent("icon_512x512@2x.png"))
let macShape = CGPath(roundedRect: flipped(macBody, in: 1024), cornerWidth: macRadius, cornerHeight: macRadius,
                      transform: nil)

for (name, label) in environments {
    let out = assets.appendingPathComponent("AppIcon-\(name).appiconset", isDirectory: true)
    try? FileManager.default.removeItem(at: out)
    try FileManager.default.createDirectory(at: out, withIntermediateDirectories: true)
    try FileManager.default.copyItem(at: contents, to: out.appendingPathComponent("Contents.json"))
    for (file, style) in iphone {
        let image = badged(load(source.appendingPathComponent(file)), label: label, style: style,
                           area: CGRect(x: 0, y: 0, width: 1024, height: 1024), clip: nil)
        save(image, to: out.appendingPathComponent(file), opaque: true)
    }
    let mac = badged(macMaster, label: label, style: light, area: macBody, clip: macShape)
    for points in [16, 32, 128, 256, 512] {
        for scale in [1, 2] {
            let file = scale == 1 ? "icon_\(points)x\(points).png" : "icon_\(points)x\(points)@2x.png"
            save(resized(mac, to: points * scale), to: out.appendingPathComponent(file), opaque: false)
        }
    }
    print("AppIcon-\(name): \(label)")
}
