import { FieldValue, type Firestore } from "firebase-admin/firestore";
import { type PlanCatalog, planLimits } from "./plans.js";

/**
 * Crea users/{uid} con el plan gratuito del catálogo y nada usado (ACC-002, ACC-005, ACC-007).
 * Idempotente: el evento de alta puede repetirse y nunca debe pisar un documento existente
 * (por ejemplo, con usedBytes ya sumado). Devuelve false si ya existía.
 */
export async function createUserDoc(db: Firestore, uid: string, plans: PlanCatalog): Promise<boolean> {
  const limits = planLimits(plans.free);
  return db.runTransaction(async (tx) => {
    const ref = db.doc(`users/${uid}`);
    if ((await tx.get(ref)).exists) return false;
    tx.create(ref, { plan: "free", ...limits, usedBytes: 0, createdAt: FieldValue.serverTimestamp() });
    return true;
  });
}

export interface ApplyPlansResult {
  /** Usuarios cuyos límites cambiaron. */
  updated: number;
  /** Planes de usuarios que no están en el catálogo: esos usuarios quedan como estaban. */
  unknownPlans: string[];
}

// Límite de operaciones de un lote de escritura de Firestore.
const BATCH_SIZE = 500;

/**
 * Aplica el catálogo a todos los usuarios (ACC-009, ADR 0017): escribe quotaBytes y maxFileBytes
 * de su plan solo donde difieren, así repetirlo no cambia nada. Nunca toca usedBytes ni borra:
 * si el espacio queda por debajo de lo usado, las reglas bloquean nuevas subidas (LIB-009).
 * Recorre la colección entera; con muchos usuarios habrá que paginar (ADR 0017).
 */
export async function applyPlans(db: Firestore, plans: PlanCatalog): Promise<ApplyPlansResult> {
  const users = await db.collection("users").select("plan", "quotaBytes", "maxFileBytes").get();
  const unknownPlans = new Set<string>();
  const changes: { ref: FirebaseFirestore.DocumentReference; limits: ReturnType<typeof planLimits> }[] = [];
  for (const user of users.docs) {
    const planId = user.get("plan");
    const plan = typeof planId === "string" && Object.hasOwn(plans, planId) ? plans[planId] : undefined;
    if (!plan) {
      unknownPlans.add(String(planId));
      continue;
    }
    const limits = planLimits(plan);
    if (user.get("quotaBytes") !== limits.quotaBytes || user.get("maxFileBytes") !== limits.maxFileBytes) {
      changes.push({ ref: user.ref, limits });
    }
  }
  for (let i = 0; i < changes.length; i += BATCH_SIZE) {
    const batch = db.batch();
    for (const { ref, limits } of changes.slice(i, i + BATCH_SIZE)) batch.update(ref, { ...limits });
    await batch.commit();
  }
  return { updated: changes.length, unknownPlans: [...unknownPlans] };
}
