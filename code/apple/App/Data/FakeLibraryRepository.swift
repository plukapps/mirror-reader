import Foundation
import ReaderDomain

/// Biblioteca de ejemplo para la vista previa de Inicio en Xcode. Sin portadas: se ven las generadas.
struct FakeLibraryRepository: LibraryRepository {
    func books() async -> [LibraryBook] {
        let now = Date.now
        func daysAgo(_ days: Double) -> Date { now.addingTimeInterval(-days * 86_400) }
        func book(_ id: String, _ title: String, _ author: String, _ progress: Int?, read: Double?, added: Double) -> LibraryBook {
            LibraryBook(
                id: id, title: title, author: author, coverPath: nil, progressPercent: progress,
                lastReadAt: read.map(daysAgo), addedAt: daysAgo(added)
            )
        }
        // El importado más reciente primero, como lo entrega la biblioteca real.
        return [
            book("n1", "Pride and Prejudice", "Jane Austen", nil, read: nil, added: 1),
            book("n2", "Moby-Dick", "H. Melville", nil, read: nil, added: 2),
            book("a", "Meditations", "Marcus Aurelius", 42, read: 0.1, added: 3),
            book("r1", "Crime and Punishment", "F. Dostoevsky", 64, read: 1, added: 5),
            book("n3", "Walden", "H. D. Thoreau", nil, read: nil, added: 8),
            book("r2", "Frankenstein", "Mary Shelley", 18, read: 3, added: 12),
            book("r3", "The Odyssey", "Homer", 33, read: 6, added: 20),
            book("f1", "The Great Gatsby", "F. S. Fitzgerald", 100, read: 25, added: 40),
            book("f2", "Hamlet", "William Shakespeare", 100, read: 60, added: 70),
        ]
    }
}
