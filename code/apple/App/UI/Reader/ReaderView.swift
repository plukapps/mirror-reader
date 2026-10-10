#if os(iOS)
@preconcurrency import ReadiumShared
import ReaderDomain
import SwiftUI

/// Pantalla del lector (RDR-001 a RDR-015, sin la animación de RDR-009), con los controles de las pantallas 06 a 08
/// del diseño, como `ReaderScreen` de Android.
struct ReaderView: View {
    @State var viewModel: ReaderViewModel
    let onClose: () -> Void

    var body: some View {
        let colors = ReaderColors(viewModel.settings.theme)
        ZStack {
            colors.page.ignoresSafeArea()
            switch viewModel.phase {
            case .loading:
                ProgressView().tint(colors.muted)
            case let .failed(message):
                FailedReader(message: message, colors: colors, onClose: close)
            case let .ready(book):
                if let publication = book.publication?.value as? Publication {
                    ReadyReader(viewModel: viewModel, book: book, publication: publication, colors: colors, onClose: close)
                }
            }
        }
        .task { await viewModel.load() }
        // RDR-015: con los controles ocultos se oculta también la barra de estado.
        .statusBarHidden(!viewModel.controlsVisible)
        .animation(.easeOut(duration: 0.2), value: viewModel.controlsVisible)
        .preferredColorScheme(viewModel.settings.theme == .dark ? .dark : .light)
    }

    private func close() {
        Task {
            await viewModel.close()
            onClose()
        }
    }
}

private struct ReadyReader: View {
    let viewModel: ReaderViewModel
    let book: OpenedBook
    let publication: Publication
    let colors: ReaderColors
    let onClose: () -> Void
    /// El índice se presenta cuando el panel de ajustes terminó de cerrarse: dos hojas a la vez no se pueden.
    @State private var tocPresented = false

    var body: some View {
        ReaderNavigator(publication: publication, initialLocatorJson: book.initialLocatorJson, viewModel: viewModel)
            .ignoresSafeArea()
            .overlay(alignment: .top) {
                if viewModel.controlsVisible {
                    ReaderTopBar(
                        chapterTitle: viewModel.chapterTitle,
                        settingsOpen: viewModel.settingsOpen,
                        colors: colors,
                        onBack: onClose,
                        onSettings: viewModel.toggleSettings
                    )
                    .transition(.opacity)
                }
            }
            .overlay(alignment: .bottom) {
                PageFooter(label: viewModel.pageLabel, colors: colors)
            }
            .overlay(alignment: .bottom) {
                if let notice = viewModel.notice {
                    NoticeToast(text: notice, onTimeout: viewModel.dismissNotice)
                        .padding(.bottom, 56)
                }
            }
            .sheet(isPresented: settingsBinding, onDismiss: {
                if viewModel.tocOpen { tocPresented = true }
            }) {
                ReaderSettingsSheet(viewModel: viewModel)
                    .presentationDetents([.height(340)])
                    .presentationCornerRadius(28)
                    .presentationBackground(SheetColors.background)
                    .presentationDragIndicator(.hidden)
            }
            .sheet(isPresented: tocBinding) {
                TableOfContentsSheet(items: book.toc, onSelect: viewModel.select)
            }
            .onChange(of: viewModel.tocOpen) { _, open in
                if !open { tocPresented = false }
            }
    }

    /// RDR-014: el panel se cierra tocando el libro, arrastrándolo hacia abajo o con "Aa".
    private var settingsBinding: Binding<Bool> {
        Binding(get: { viewModel.settingsOpen }, set: { if !$0 { viewModel.closeSettings() } })
    }

    private var tocBinding: Binding<Bool> {
        Binding(
            get: { tocPresented },
            set: {
                tocPresented = $0
                if !$0 { viewModel.tocOpen = false }
            }
        )
    }
}

/// RDR-010: número de página en el pie, visible aunque los controles estén ocultos.
private struct PageFooter: View {
    let label: String?
    let colors: ReaderColors

    var body: some View {
        Text(label ?? " ")
            .font(.app(12, .medium))
            .monospacedDigit()
            .foregroundStyle(colors.text.opacity(0.6))
            .frame(height: 32)
            .frame(maxWidth: .infinity)
            .accessibilityLabel(label.map { "Página \($0)" } ?? "")
    }
}

/// RDR-012: aviso breve, como un Toast largo de Android (unos 3,5 s).
private struct NoticeToast: View {
    let text: String
    let onTimeout: () -> Void

    var body: some View {
        Text(text)
            .font(.app(14, .medium))
            .foregroundStyle(.white)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(Color.black.opacity(0.8), in: Capsule())
            .task {
                AccessibilityNotification.Announcement(text).post()
                try? await Task.sleep(for: .seconds(3.5))
                onTimeout()
            }
            .transition(.opacity)
    }
}

private struct FailedReader: View {
    let message: String
    let colors: ReaderColors
    let onClose: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Text(message)
                .font(.app(16))
                .foregroundStyle(colors.text)
                .multilineTextAlignment(.center)
            Button("Volver", action: onClose)
                .font(.app(15, .bold))
                .foregroundStyle(MarginColors.yellow)
                .padding(.horizontal, 20)
                .frame(height: 44)
                .background(MarginColors.ink, in: Capsule())
        }
        .padding(24)
    }
}
#endif
