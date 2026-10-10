import Testing
@testable import ReaderDomain

// Mismos casos que `ReaderSettingsTest`, `ChapterTitleTest`, `BackMatterTest` y `PageLabelTest` de Android:
// el spec es el contrato común (ADR 0010).

struct ReaderSettingsTests {
    // RDR-002
    @Test func fontScaleStepsByTenth() {
        #expect(ReaderSettings().biggerFont().fontScale == 1.1)
        #expect(ReaderSettings().smallerFont().fontScale == 0.9)
    }

    // RDR-002: límites
    @Test func fontScaleIsClamped() {
        #expect(ReaderSettings(fontScale: 2.5).biggerFont().fontScale == 2.5)
        #expect(ReaderSettings(fontScale: 0.5).smallerFont().fontScale == 0.5)
    }

    // RDR-002: varios pasos seguidos no acumulan error de coma flotante
    @Test func repeatedStepsStayOnTenths() {
        var settings = ReaderSettings()
        for _ in 0..<7 { settings = settings.biggerFont() }
        #expect(settings.fontScale == 1.7)
        #expect(settings.fontTenths == 17)
    }

    // RDR-014: tipo de letra con serifa por defecto, como el diseño; cambiar uno no toca los demás
    @Test func fontDefaultsToSerifAndChangesOnlyTheFont() {
        #expect(ReaderSettings().font == .serif)
        let base = ReaderSettings(theme: .dark, fontScale: 1.3)
        var expected = base
        expected.font = .mono
        #expect(base.withFont(.mono) == expected)
    }

    // RDR-014: interlineado normal por defecto
    @Test func lineSpacingDefaultsToNormalAndChangesOnlyTheSpacing() {
        #expect(ReaderSettings().lineSpacing == .normal)
        let base = ReaderSettings(theme: .sepia, font: .sans)
        var expected = base
        expected.lineSpacing = .wide
        #expect(base.withLineSpacing(.wide) == expected)
    }

    // RDR-014: el tema se elige directamente (Clásico, Sepia, Noche)
    @Test func themeCanBePickedDirectly() {
        #expect(ReaderSettings().theme == .light)
        #expect(ReaderSettings().withTheme(.sepia).theme == .sepia)
        let base = ReaderSettings(fontScale: 1.2, font: .mono)
        var expected = base
        expected.theme = .dark
        #expect(base.withTheme(.dark) == expected)
    }

    // ADR 0009: cada opción apunta a una familia distinta, con el mismo nombre que en Android
    @Test func eachFontHasItsOwnFamilyName() {
        #expect(ReaderFont.serif.familyName == "Newsreader")
        #expect(ReaderFont.sans.familyName == "Host Grotesk")
        #expect(ReaderFont.mono.familyName == "JetBrains Mono")
        #expect(Set(ReaderFont.allCases.map(\.familyName)).count == ReaderFont.allCases.count)
    }

    // RDR-014: amplio separa más las líneas que normal
    @Test func wideSpacingIsLooserThanNormal() {
        #expect(LineSpacing.normal.lineHeight == 1.4)
        #expect(LineSpacing.wide.lineHeight == 1.7)
    }

    // Los valores guardados son los de Android (nombres de enum en mayúsculas)
    @Test func storedNamesMatchAndroid() {
        #expect(ReadingTheme.allCases.map(\.rawValue) == ["LIGHT", "DARK", "SEPIA"])
        #expect(ReaderFont.allCases.map(\.rawValue) == ["SERIF", "SANS", "MONO"])
        #expect(LineSpacing.allCases.map(\.rawValue) == ["NORMAL", "WIDE"])
    }
}

/// RDR-013: la barra superior muestra el título del capítulo donde está el lector.
struct ChapterTitleTests {
    private let order = ["cover.xhtml", "c1.xhtml", "c1b.xhtml", "c2.xhtml", "c3.xhtml", "notes.xhtml"]
    private let toc = [
        ChapterEntry(title: "Capítulo 1", href: "c1.xhtml"),
        ChapterEntry(title: "Capítulo 2", href: "c2.xhtml"),
        ChapterEntry(title: "Capítulo 3", href: "c3.xhtml"),
    ]

    @Test func resourceWithItsOwnEntryGivesThatTitle() {
        #expect(currentChapterTitle(toc: toc, readingOrder: order, href: "c2.xhtml") == "Capítulo 2")
    }

    // Un recurso sin entrada propia (continuación del capítulo) sigue en el capítulo anterior
    @Test func resourceWithoutEntryBelongsToThePreviousChapter() {
        #expect(currentChapterTitle(toc: toc, readingOrder: order, href: "c1b.xhtml") == "Capítulo 1")
        #expect(currentChapterTitle(toc: toc, readingOrder: order, href: "notes.xhtml") == "Capítulo 3")
    }

    // Antes del primer capítulo (portada) no hay título
    @Test func beforeTheFirstEntryThereIsNoTitle() {
        #expect(currentChapterTitle(toc: toc, readingOrder: order, href: "cover.xhtml") == "")
    }

    @Test func bookWithoutTableOfContentsHasNoTitle() {
        #expect(currentChapterTitle(toc: [], readingOrder: order, href: "c1.xhtml") == "")
    }

    @Test func unknownResourceHasNoTitle() {
        #expect(currentChapterTitle(toc: toc, readingOrder: order, href: "otro.xhtml") == "")
    }

    // La posición del lector trae fragmento ("c2.xhtml#p5"): se compara por recurso
    @Test func fragmentInThePositionIsIgnoredWhenTheEntryHasNone() {
        #expect(currentChapterTitle(toc: toc, readingOrder: order, href: "c2.xhtml#p5") == "Capítulo 2")
    }

    // Varias entradas en un mismo recurso: con fragmento exacto gana esa; si no, la primera (el capítulo)
    @Test func sectionsInTheSameResourcePreferTheExactFragmentThenTheFirst() {
        let withSections = [
            ChapterEntry(title: "Libro II", href: "c2.xhtml"),
            ChapterEntry(title: "La mañana", href: "c2.xhtml#manana"),
            ChapterEntry(title: "La tarde", href: "c2.xhtml#tarde"),
        ]
        #expect(currentChapterTitle(toc: withSections, readingOrder: order, href: "c2.xhtml#tarde") == "La tarde")
        #expect(currentChapterTitle(toc: withSections, readingOrder: order, href: "c2.xhtml") == "Libro II")
        #expect(currentChapterTitle(toc: withSections, readingOrder: order, href: "c2.xhtml#otro") == "Libro II")
    }

    // Entradas que apuntan fuera del orden de lectura no cuentan
    @Test func entriesOutsideTheReadingOrderAreIgnored() {
        let odd = [ChapterEntry(title: "Fantasma", href: "no-esta.xhtml"), ChapterEntry(title: "Capítulo 1", href: "c1.xhtml")]
        #expect(currentChapterTitle(toc: odd, readingOrder: order, href: "c1b.xhtml") == "Capítulo 1")
        #expect(currentChapterTitle(toc: odd, readingOrder: order, href: "cover.xhtml") == "")
    }
}

struct BackMatterTests {
    private let order = ["cover", "c1", "c2", "c3", "notes", "index"]

    private func toc(_ entries: (String, String)...) -> [BodyEntry] {
        entries.map { BodyEntry(title: $0.0, href: $0.1) }
    }

    private func starts(_ titles: [String]) -> Int? {
        let hrefs = titles.indices.map { "r\($0)" }
        return backMatterStart(toc: zip(titles, hrefs).map { BodyEntry(title: $0, href: $1) }, readingOrder: hrefs)
    }

    // RDR-012: el bloque final de entradas de páginas finales marca el fin del cuerpo
    @Test func startsAtFirstEntryOfTrailingBlock() {
        let entries = toc(("Capítulo 1", "c1"), ("Capítulo 2", "c2"), ("Notas", "notes"), ("Índice", "index"))
        #expect(backMatterStart(toc: entries, readingOrder: order) == 4)
    }

    // RDR-012: títulos en inglés, con mayúsculas o acentos distintos
    @Test func matchesEnglishAndIgnoresCaseAndAccents() {
        let entries = toc(("Chapter 1", "c1"), ("ACKNOWLEDGMENTS", "notes"), ("Index", "index"))
        #expect(backMatterStart(toc: entries, readingOrder: order) == 4)
        #expect(backMatterStart(toc: toc(("Capitulo", "c1"), ("indice", "index")), readingOrder: order) == 5)
    }

    // RDR-012: una entrada "Notas" en medio del libro no es el fin del cuerpo
    @Test func ignoresMatchesThatAreNotTrailing() {
        let entries = toc(("Notas", "c1"), ("Capítulo 2", "c2"), ("Capítulo 3", "c3"))
        #expect(backMatterStart(toc: entries, readingOrder: order) == nil)
    }

    @Test func noBackMatterGivesNil() {
        #expect(backMatterStart(toc: toc(("Capítulo 1", "c1"), ("Capítulo 2", "c2")), readingOrder: order) == nil)
    }

    // RDR-012: si todo el TOC son páginas finales, no hay cuerpo que terminar
    @Test func allBackMatterGivesNil() {
        #expect(backMatterStart(toc: toc(("Notas", "notes"), ("Índice", "index")), readingOrder: order) == nil)
    }

    @Test func hrefWithFragmentIsResolvedToItsResource() {
        #expect(backMatterStart(toc: toc(("Capítulo 1", "c1"), ("Notas", "notes#inicio")), readingOrder: order) == 4)
    }

    @Test func unknownResourceGivesNil() {
        #expect(backMatterStart(toc: toc(("Capítulo 1", "c1"), ("Notas", "otro")), readingOrder: order) == nil)
    }

    // RDR-012: libro real, termina con una promoción tras "Notas" y "Créditos"
    @Test func promoAtTheEndDoesNotBreakTheBlock() {
        let titles = ["Epílogo", "Agradecimientos", "Láminas", "Notas", "Créditos", "¡Encuentra aquí tu próxima lectura!"]
        #expect(starts(titles) == 1)
    }

    // RDR-012: libro real en inglés, con plurales y "Illustration Credits"
    @Test func pluralsAndCompoundTitlesMatch() {
        #expect(starts(["Conclusion", "Acknowledgments", "Illustration Credits", "Index", "About the Authors"]) == 1)
    }

    @Test func singularNoteMatches() {
        #expect(starts(["Capítulo 20", "Apéndice B", "Nota"]) == 2)
    }

    @Test func keywordInsideBodyTitleIsIgnored() {
        #expect(starts(["Notas sobre el método", "Capítulo 2", "Capítulo 3"]) == nil)
    }

    // RDR-012: al pasar del cuerpo a las notas avisa una sola vez
    @Test func announcesOnceWhenEnteringBackMatter() {
        let detector = BodyEndDetector(readingOrder: order, backMatterStart: 4)
        #expect(!detector.onResource("c2"))
        #expect(detector.onResource("notes"))
        #expect(!detector.onResource("index"))
        #expect(!detector.onResource("c3"))
        #expect(!detector.onResource("notes"))
    }

    // RDR-012: reabrir el libro ya dentro de las páginas finales no avisa
    @Test func doesNotAnnounceWhenFirstPositionIsInBackMatter() {
        let detector = BodyEndDetector(readingOrder: order, backMatterStart: 4)
        #expect(!detector.onResource("index"))
        #expect(!detector.onResource("notes"))
    }

    @Test func neverAnnouncesWithoutBackMatter() {
        let detector = BodyEndDetector(readingOrder: order, backMatterStart: nil)
        #expect(!detector.onResource("c1"))
        #expect(!detector.onResource("index"))
    }
}

// RDR-010: texto del pie (una página: en el iPhone no hay doble página, RDR-016)
struct PageLabelTests {
    @Test func singlePageShowsPosition() { #expect(pageLabel(position: 107) == "107") }
    @Test func noPositionShowsNothing() { #expect(pageLabel(position: nil) == nil) }
}
