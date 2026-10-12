import Foundation
import Testing
import ReaderDomain

// Mismos casos que `OnboardingRulesTest` de Android. La próxima hora del recordatorio no está: en Apple la calcula el
// sistema (ADR 0015).

struct OnboardingRulesTests {
    @Test("ONB-005: al menos 8 caracteres y dos de número, símbolo y mayúscula")
    func passwordRules() {
        #expect(!checkPassword("Ab1!").isValid) // corta
        #expect(!checkPassword("abcdefgh").isValid) // ninguna extra
        #expect(!checkPassword("abcdefg1").isValid) // solo número
        #expect(checkPassword("abcdefg1!").isValid) // número y símbolo
        #expect(checkPassword("Abcdefgh1").isValid) // mayúscula y número
        #expect(checkPassword("slow-reads-2026").isValid)
    }

    @Test("ONB-005: un tramo del medidor por regla cumplida")
    func strengthCountsRules() {
        #expect(checkPassword("").strength == .empty)
        #expect(checkPassword("abcdefgh").strength == .weak)
        #expect(checkPassword("abcdefg1").strength == .fair)
        #expect(checkPassword("slow-reads-2026").strength == .good)
        #expect(checkPassword("Slow-reads-2026").strength == .strong)
        #expect(!checkPassword("a b c d e").hasSymbol) // el espacio no cuenta como símbolo
    }

    @Test("ONB-004: el email necesita un formato mínimo")
    func emailFormat() {
        #expect(isValidEmail("ana.ruiz@mail.com"))
        #expect(isValidEmail("  ana@mail.co "))
        #expect(!isValidEmail("ana"))
        #expect(!isValidEmail("ana@mail"))
        #expect(!isValidEmail("ana ruiz@mail.com"))
        #expect(!isValidEmail(""))
    }

    @Test("ONB-019: el arranque depende de la sesión y de la verificación")
    func start() {
        #expect(startDestination(nil) == .welcome)
        #expect(startDestination(AccountUser(id: "u", email: "a@b.c", needsEmailVerification: true)) == .verifyEmail)
        #expect(startDestination(AccountUser(id: "u", email: "a@b.c")) == .home)
    }

    @Test("ONB-009: hacen falta al menos 3 intereses")
    func interests() {
        #expect(!canContinueInterests([.poetry, .drama]))
        #expect(canContinueInterests([.poetry, .drama, .essays]))
    }

    @Test("ONB-010: las opciones de la meta son las del diseño")
    func goals() {
        #expect(ReadingGoal.allCases.map(\.minutes) == [10, 20, 30, 60])
        #expect(ReadingGoal.default == .regular)
        #expect(DailyReminder.default == DailyReminder(enabled: true, hour: 21, minute: 30))
        #expect(DailyReminder(enabled: true, hour: 24, minute: 0) == nil)
    }

    @Test("ONB-013: el saludo usa el primer nombre")
    func greeting() {
        #expect(firstName("  Ana Ruiz ") == "Ana")
        #expect(firstName("  ") == nil)
        #expect(firstName(nil) == nil)
    }

    @Test("ONB-007, ONB-018: los enlaces de los emails se reconocen solo desde el dominio del proyecto")
    func authLinks() {
        let host = "demo.firebaseapp.com"
        func parse(_ link: String) -> AuthLink? { parseAuthLink(URL(string: link)!, host: host) }
        #expect(parse("https://demo.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=ab-C_1&apiKey=k&lang=es") == .resetPassword(code: "ab-C_1"))
        #expect(parse("https://demo.firebaseapp.com/__/auth/action?oobCode=x%25y&mode=verifyEmail") == .verifyEmail(code: "x%y"))
        #expect(parse("https://demo.firebaseapp.com/__/auth/action?mode=recoverEmail&oobCode=c") == nil)
        #expect(parse("https://demo.firebaseapp.com/__/auth/action?mode=resetPassword") == nil)
        #expect(parse("https://evil.example.com/__/auth/action?mode=resetPassword&oobCode=c") == nil)
        #expect(parse("http://demo.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=c") == nil)
        #expect(parse("file:///libros/book.epub") == nil)
    }

    @Test("ONB-015: un error que no es de la cuenta es desconocido")
    func authErrorOfOtherErrors() {
        struct Other: Error {}
        #expect(AuthFailure(.network).authError == .network)
        #expect(Other().authError == .unknown)
    }
}
