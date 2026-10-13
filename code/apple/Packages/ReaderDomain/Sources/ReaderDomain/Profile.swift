import Foundation

/// Unidad en la que se muestran los tamaños del espacio (PRF-006).
public enum StorageUnit: Sendable {
    case mb, gb

    var bytes: Int64 {
        switch self {
        case .mb: 1024 * 1024
        case .gb: 1024 * 1024 * 1024
        }
    }

    var label: String {
        switch self {
        case .mb: "MB"
        case .gb: "GB"
        }
    }

    /// PRF-006: los tamaños usan la unidad del total; GB si el total es de 1 GB o más.
    public static func forTotal(_ totalBytes: Int64) -> StorageUnit {
        totalBytes >= StorageUnit.gb.bytes ? .gb : .mb
    }
}

/// "1,2 GB", "2 GB", "3,4 MB": un decimal como máximo y coma decimal, sin depender del idioma del sistema. Como Android.
public func formatStorage(_ bytes: Int64, unit: StorageUnit) -> String {
    let tenths = Int64((Double(max(bytes, 0)) * 10 / Double(unit.bytes)).rounded())
    let whole = tenths / 10
    let decimal = tenths % 10
    return (decimal == 0 ? "\(whole)" : "\(whole),\(decimal)") + " " + unit.label
}

/// PRF-013: tamaño de letra en puntos, sobre la base de 18 del diseño.
public func readerFontPoints(fontTenths: Int) -> Int {
    Int((18 * Double(fontTenths) / 10).rounded())
}

/// Último espacio leído, para mostrarlo sin conexión o mientras llega el nuevo (PRF-006).
public protocol QuotaCache: Sendable {
    func last() -> StorageQuota?
    func save(_ quota: StorageQuota)
}
