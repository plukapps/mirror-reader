import ReaderDomain
import SwiftUI

@main
struct ReaderApp: App {
    var body: some Scene {
        WindowGroup {
            // Provisorio (K-074): confirma que la app enlaza el paquete de dominio. Inicio llega en K-076.
            Text(verbatim: "Reader · \(greeting(forHour: Calendar.current.component(.hour, from: .now)))")
                .frame(minWidth: 480, minHeight: 320)
        }
    }
}
