import { FieldValue, type Firestore } from "firebase-admin/firestore";

// Contabilidad de cuota (ADR 0008). Los eventos de Storage llegan al menos una vez,
// así que cada archivo contabilizado se registra en users/{uid}/storedFiles/{hash}
// y sumar o restar ocurre una sola vez, dentro de una transacción.

const BOOK_FILE_PATH = /^users\/([^/]+)\/books\/([a-f0-9]{64})\.epub$/;

export interface BookFile {
  uid: string;
  hash: string;
}

export function parseBookFilePath(objectName: string): BookFile | null {
  const match = BOOK_FILE_PATH.exec(objectName);
  return match ? { uid: match[1], hash: match[2] } : null;
}

/**
 * El tamaño del objeto llega como texto en los eventos de Storage (JSON de la API), aunque los
 * tipos de firebase-functions digan `number`. Se acepta ambos y se exige un entero válido.
 */
export function parseObjectSize(value: unknown): number {
  // Number("") vale 0: un texto vacío o con signo no es un tamaño.
  const size = typeof value === "string" ? (/^\d+$/.test(value) ? Number(value) : NaN) : value;
  if (typeof size !== "number" || !Number.isSafeInteger(size) || size < 0) {
    throw new Error(`Tamaño de objeto inválido: ${String(value)}`);
  }
  return size;
}

/** Suma el archivo a usedBytes. Devuelve false si ya estaba contabilizado. */
export async function addStoredFile(db: Firestore, file: BookFile, sizeBytes: number): Promise<boolean> {
  const userRef = db.doc(`users/${file.uid}`);
  const fileRef = db.doc(`users/${file.uid}/storedFiles/${file.hash}`);
  return db.runTransaction(async (tx) => {
    const [user, stored] = await Promise.all([tx.get(userRef), tx.get(fileRef)]);
    if (stored.exists) return false;
    // La regla de Storage ya exige que el documento exista; si falta, es un error a revisar.
    if (!user.exists) throw new Error(`No existe users/${file.uid}: no se puede contabilizar ${file.hash}`);
    tx.set(fileRef, { sizeBytes, createdAt: FieldValue.serverTimestamp() });
    tx.update(userRef, { usedBytes: FieldValue.increment(sizeBytes) });
    return true;
  });
}

/** Resta el archivo de usedBytes con el tamaño que se guardó al sumarlo. Devuelve false si no estaba. */
export async function removeStoredFile(db: Firestore, file: BookFile): Promise<boolean> {
  const userRef = db.doc(`users/${file.uid}`);
  const fileRef = db.doc(`users/${file.uid}/storedFiles/${file.hash}`);
  return db.runTransaction(async (tx) => {
    const [user, stored] = await Promise.all([tx.get(userRef), tx.get(fileRef)]);
    if (!stored.exists) return false;
    tx.delete(fileRef);
    if (user.exists) tx.update(userRef, { usedBytes: FieldValue.increment(-(stored.get("sizeBytes") as number)) });
    return true;
  });
}
