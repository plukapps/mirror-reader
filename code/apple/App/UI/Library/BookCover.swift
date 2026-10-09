import ReaderDomain
import SwiftUI

/// Portada 2:3 (LIB-011). Sin portada en el EPUB se genera una con color y título.
struct BookCover: View {
    let book: LibraryBook

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 6)
        Group {
            if let path = book.coverPath, let image = PlatformImage(contentsOfFile: path) {
                Image(platformImage: image).resizable().scaledToFill()
            } else {
                generated
            }
        }
        .aspectRatio(2 / 3, contentMode: .fit)
        .clipShape(shape)
        .overlay(shape.strokeBorder(MarginColors.coverBorder, lineWidth: 3))
    }

    private var generated: some View {
        let style = MarginColors.covers[stableIndex(book.id, MarginColors.covers.count)]
        return style.color.overlay(alignment: .topLeading) {
            Text(book.title)
                .font(.app(13, .heavy))
                .tracking(-0.04 * 13)
                .lineSpacing(0)
                .lineLimit(5)
                .foregroundStyle(style.isLight ? MarginColors.ink : .white)
                .padding(7)
        }
    }

    /// `hashValue` cambia en cada arranque; esto da el mismo color siempre para el mismo libro.
    private func stableIndex(_ id: String, _ count: Int) -> Int {
        id.unicodeScalars.reduce(0) { ($0 &* 31 &+ Int($1.value)) & 0x7FFF_FFFF } % count
    }
}

#if os(macOS)
typealias PlatformImage = NSImage
extension Image {
    init(platformImage: NSImage) { self.init(nsImage: platformImage) }
}
#else
typealias PlatformImage = UIImage
extension Image {
    init(platformImage: UIImage) { self.init(uiImage: platformImage) }
}
#endif
