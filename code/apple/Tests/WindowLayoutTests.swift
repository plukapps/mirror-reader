import CoreGraphics
import Testing
@testable import Reader

struct WindowLayoutTests {
    #if os(iOS)
    @Test("IOS-001: en iPhone no hay ancho mínimo; el contenido toma el ancho de la pantalla")
    func iPhoneHasNoMinimumSize() {
        #expect(WindowLayout.minimumSize == nil)
    }

    @Test("IOS-001: los márgenes laterales entran en un iPhone de 375 pt")
    func horizontalPaddingFitsNarrowestPhone() {
        // La tarjeta de Continuar: portada de 96 pt, separación y relleno interno, más el título.
        let card = 2 * WindowLayout.horizontalPadding + 2 * 16 + 96 + 16
        #expect(375 - card >= 150)
    }
    #else
    @Test("MAC-001: la ventana de la Mac se puede achicar hasta 480 pt")
    func macKeepsMinimumWidth() {
        #expect(WindowLayout.minimumSize?.width == 480)
    }
    #endif
}
