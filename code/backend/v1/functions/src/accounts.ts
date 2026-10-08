import { FieldValue, type Firestore } from "firebase-admin/firestore";

// Cuota del plan gratuito (ACC-002, open-questions #2): 15 MiB, decisión del 2026-10-07.
// Para cambiarla basta este valor; los usuarios ya creados conservan la suya en users/{uid}.quotaBytes.
export const FREE_PLAN = { plan: "free", quotaBytes: 15 * 1024 * 1024 } as const;

/**
 * Crea users/{uid} con el plan gratuito y nada usado (ACC-002, ACC-005).
 * Idempotente: el evento de alta puede repetirse y nunca debe pisar un documento existente
 * (por ejemplo, con usedBytes ya sumado). Devuelve false si ya existía.
 */
export async function createUserDoc(db: Firestore, uid: string): Promise<boolean> {
  return db.runTransaction(async (tx) => {
    const ref = db.doc(`users/${uid}`);
    if ((await tx.get(ref)).exists) return false;
    tx.create(ref, { ...FREE_PLAN, usedBytes: 0, createdAt: FieldValue.serverTimestamp() });
    return true;
  });
}
