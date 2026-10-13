import Foundation

/// Cierra la sesión de la cuenta en este dispositivo, también la de Google (PRF-009).
public protocol SessionRepository: Sendable {
    func signOut() async throws
}

/// Lo que la cuenta guarda en este dispositivo: libros, portadas, progreso y preferencias de la cuenta (PRF-009).
public protocol LocalAccountData: Sendable {
    /// Lo borra todo, salvo los ajustes del lector y la identidad del dispositivo. Puede fallar.
    func wipe() async throws
}

/// La sincronización vista desde el cierre de sesión (PRF-009).
public protocol SyncControl: Sendable {
    /// Deja de escuchar la nube (posiciones, borrados, libros nuevos).
    func stopListening() async
    /// Corre `body` sin pasadas de biblioteca en curso ni nuevas, y olvida la última.
    func whileIdle<T: Sendable>(_ body: @Sendable () async -> T) async -> T
    /// Vuelve a escuchar; sin sesión no hace nada.
    func startListening() async
}

/// PRF-009: cierra la sesión y borra de este dispositivo los datos de la cuenta (siguen en la nube), como `SignOut`
/// de Android. El orden importa: primero se deja de escuchar y se espera a que no haya ninguna pasada en curso;
/// después se borra y recién al final se cierra la sesión. Así nada vuelve a escribir datos de la cuenta después del
/// borrado. Si el borrado falla, igual se cierra la sesión: al volver a entrar, la sincronización reconstruye la
/// biblioteca desde la nube.
public struct SignOut: Sendable {
    private let sync: SyncControl
    private let localData: LocalAccountData
    private let session: SessionRepository
    private let reminders: ReminderScheduler
    private let hasPending: @Sendable () async -> Bool

    public init(
        sync: SyncControl,
        localData: LocalAccountData,
        session: SessionRepository,
        reminders: ReminderScheduler,
        hasPending: @escaping @Sendable () async -> Bool
    ) {
        self.sync = sync
        self.localData = localData
        self.session = session
        self.reminders = reminders
        self.hasPending = hasPending
    }

    /// Hay libros importados sin subir, progreso o libros quitados que todavía no llegaron a la nube.
    public func hasPendingChanges() async -> Bool {
        await hasPending()
    }

    public func callAsFunction() async throws {
        await sync.stopListening()
        let result: Result<Void, Error> = await sync.whileIdle { [localData, reminders, session] in
            // Si falla, se sigue igual: quedarse con la sesión abierta sería peor que quedar con datos viejos que la
            // próxima sincronización reemplaza.
            try? await localData.wipe()
            await reminders.cancel()
            do {
                try await session.signOut()
                return .success(())
            } catch {
                return .failure(error)
            }
        }
        // La app sigue a la vista: sin sesión las escuchas no hacen nada y arrancan solas con la próxima cuenta.
        await sync.startListening()
        try result.get()
    }
}
