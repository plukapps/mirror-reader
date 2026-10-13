import ReaderDomain
import SwiftUI

/// Textos de las preguntas frecuentes (PRF-017, PRF-018), los mismos que `strings.xml` de Android.
extension FaqId {
    var question: LocalizedStringKey {
        switch self {
        case .epub: "¿Por qué no abre este EPUB?"
        case .books: "¿Dónde están mis libros?"
        case .space: "¿Cuánto espacio tengo?"
        case .add: "¿Cómo agrego libros?"
        case .sync: "¿Cómo se sincroniza mi lectura?"
        case .remove: "¿Cómo quito un libro?"
        }
    }

    var answer: LocalizedStringKey {
        switch self {
        case .epub: "margin. abre libros EPUB sin protección. Los que vienen con DRM (la protección que ponen algunas tiendas) no se pueden abrir: hay que leerlos en la app de esa tienda.\n\nSi el archivo no tiene DRM y aun así no abre, puede estar dañado o incompleto. Probá descargarlo otra vez desde donde lo conseguiste y volvé a importarlo."
        case .books: "Tus libros están en tu cuenta, en la nube, y en cada dispositivo donde entrás con ella. Un libro que agregás en un dispositivo aparece solo en los demás.\n\nSi un libro todavía no está en este dispositivo, se descarga cuando lo abrís. Para eso hace falta conexión; después se lee sin ella."
        case .space: "Tu plan define cuánto podés guardar en la nube. Lo ves arriba de todo en Perfil, en Espacio: cuánto usás y cuánto te queda.\n\nCuentan los archivos de los libros; las portadas no. Si llegás al límite, los libros nuevos quedan solo en este dispositivo y podés seguir leyendo todo. Para liberar espacio, quitá libros que ya no quieras."
        case .add: "Desde Inicio o Estantes, con Importar, elegí uno o varios archivos EPUB del teléfono. También podés abrir un EPUB desde otra app (por ejemplo, el correo o Archivos) y elegir margin.\n\nLos libros nuevos se suben a tu cuenta cuando hay conexión."
        case .sync: "Cada vez que leés, margin. guarda en qué página quedaste y la sube a tu cuenta cuando hay conexión. Al abrir el mismo libro en otro dispositivo, sigue desde ahí.\n\nSi leíste sin conexión, la posición se sube sola la próxima vez que la app tenga red."
        case .remove: "Mantené presionada la portada del libro y elegí Quitar de la biblioteca. Se borra de tu cuenta y de todos tus dispositivos, con su progreso.\n\nSi lo querés de vuelta, importá el archivo otra vez."
        }
    }
}

/// Pantallas dentro del tab Perfil: ayuda y cambiar contraseña.
enum ProfileRoute: Hashable {
    case faq(FaqId)
    case allFaq
    /// PRF-020.
    case changePassword
}

/// Filas de preguntas sobre la tarjeta tinta de ayuda (PRF-017): 52 de alto, separador, flecha.
struct FaqRows: View {
    let questions: [FaqId]
    let onOpen: (FaqId) -> Void

    var body: some View {
        ForEach(Array(questions.enumerated()), id: \.element) { index, id in
            if index > 0 { CardDivider(color: MarginColors.inkLine) }
            ProfileRow(minHeight: 52, action: { onOpen(id) }) {
                Text(id.question).font(.app(15, .medium)).foregroundStyle(MarginColors.field)
                Spacer(minLength: 0)
                ProfileIcon(name: "ChevronRight", color: MarginColors.inkMuted)
            }
        }
    }
}

/// PRF-018: todas las preguntas, en la misma tarjeta tinta de Perfil.
struct FaqListView: View {
    let onBack: () -> Void
    let onOpen: (FaqId) -> Void

    var body: some View {
        FaqPage(title: "Preguntas frecuentes", onBack: onBack) {
            ProfileCard(background: MarginColors.ink) {
                FaqRows(questions: Faq.all, onOpen: onOpen)
            }
        }
    }
}

/// PRF-017: la respuesta de una pregunta.
struct FaqAnswerView: View {
    let id: FaqId
    let onBack: () -> Void

    var body: some View {
        FaqPage(title: id.question, onBack: onBack) {
            ProfileCard {
                Text(id.answer)
                    .font(.app(15))
                    .lineSpacing(15 * 0.3)
                    .foregroundStyle(MarginColors.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16)
            }
        }
    }
}

/// Pantalla de ayuda: fondo papel, flecha atrás, título de 22 bold y el contenido debajo, como Android.
private struct FaqPage<Content: View>: View {
    let title: LocalizedStringKey
    let onBack: () -> Void
    @ViewBuilder let content: Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Button(action: onBack) {
                    ProfileIcon(name: "ArrowBack", size: 24)
                        .frame(width: 44, height: 44)
                        .contentShape(Circle())
                }
                .buttonStyle(.plain)
                .padding(.leading, 12)
                .padding(.top, 8)
                .accessibilityLabel("Volver")
                Text(title)
                    .font(.app(22, .bold))
                    .tracking(-22 * 0.02)
                    .foregroundStyle(MarginColors.ink)
                    .padding(EdgeInsets(top: 12, leading: 24, bottom: 20, trailing: 24))
                    .accessibilityAddTraits(.isHeader)
                content
            }
            .padding(.bottom, 24)
            .frame(maxWidth: WindowLayout.contentMaxWidth)
            .frame(maxWidth: .infinity)
        }
        .background(MarginColors.paper)
        .toolbar(.hidden)
    }
}
