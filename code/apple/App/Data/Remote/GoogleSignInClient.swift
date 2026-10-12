import FirebaseCore
import Foundation
import GoogleSignIn
import OSLog
import ReaderDomain
#if os(iOS)
import UIKit
#else
import AppKit
#endif

/// Resultado de pedirle a Google una cuenta (ONB-020).
enum GoogleSignInResult: Equatable {
    case tokens(GoogleTokens)
    case cancelled
    case failed
}

/// Pide una cuenta de Google con el SDK GoogleSignIn (ADR 0015, firebase.google.com/docs/auth/ios/google-signin).
/// El selector se muestra sobre la ventana activa.
@MainActor
enum GoogleSignInClient {
    static func signIn() async -> GoogleSignInResult {
        guard let clientID = FirebaseApp.app()?.options.clientID else { return .failed }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        #if os(iOS)
        guard let presenter = topViewController() else { return .failed }
        #else
        guard let presenter = NSApplication.shared.keyWindow ?? NSApplication.shared.windows.first else { return .failed }
        #endif
        // Con la versión de completion: el resultado del SDK no es `Sendable` y no puede cruzar el `await`.
        return await withCheckedContinuation { continuation in
            // En iPhone recibe la vista y en la Mac la ventana; el SDK llama de vuelta en el hilo principal.
            GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
                continuation.resume(returning: outcome(result, error))
            }
        }
    }

    private static func outcome(_ result: GIDSignInResult?, _ error: Error?) -> GoogleSignInResult {
        if let error {
            let nsError = error as NSError
            if nsError.domain == kGIDSignInErrorDomain, nsError.code == GIDSignInError.canceled.rawValue {
                return .cancelled
            }
            Logger(subsystem: "com.pluk.reader", category: "Account")
                .error("Google no entregó una cuenta: \(String(describing: error), privacy: .public)")
            return .failed
        }
        guard let user = result?.user, let idToken = user.idToken?.tokenString else { return .failed }
        return .tokens(GoogleTokens(idToken: idToken, accessToken: user.accessToken.tokenString))
    }

    /// Vuelta del navegador al terminar el ingreso con Google. Devuelve si la URL era suya.
    static func handle(_ url: URL) -> Bool {
        GIDSignIn.sharedInstance.handle(url)
    }

    #if os(iOS)
    private static func topViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let window = scenes.flatMap(\.windows).first { $0.isKeyWindow } ?? scenes.first?.windows.first
        var top = window?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
    #endif
}
