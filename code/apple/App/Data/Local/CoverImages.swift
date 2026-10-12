import Foundation
import ImageIO
import UniformTypeIdentifiers

/// Portadas que elige el usuario (LIB-019), con el mismo resultado que las del EPUB: JPEG de hasta 600 px de alto.
/// ImageIO funciona igual en el iPhone y en la Mac y respeta la orientación de la foto.
enum CoverImages {
    static let maxHeight = 600

    /// La imagen de `data` como portada JPEG, o nil si no es una imagen.
    static func jpeg(from data: Data) -> Data? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              var width = properties[kCGImagePropertyPixelWidth] as? Int,
              var height = properties[kCGImagePropertyPixelHeight] as? Int,
              width > 0, height > 0
        else { return nil }
        // Orientaciones 5 a 8: la foto está girada 90°, el alto real es el ancho guardado.
        if let orientation = properties[kCGImagePropertyOrientation] as? UInt32, orientation >= 5 { swap(&width, &height) }
        let longest = max(width, height)
        let maxPixelSize = height > maxHeight ? Int((Double(longest) * Double(maxHeight) / Double(height)).rounded()) : longest
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixelSize,
        ]
        guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return nil }
        let output = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(output, UTType.jpeg.identifier as CFString, 1, nil) else {
            return nil
        }
        CGImageDestinationAddImage(destination, image, [kCGImageDestinationLossyCompressionQuality: 0.85] as CFDictionary)
        return CGImageDestinationFinalize(destination) ? output as Data : nil
    }
}
