import assert from "node:assert/strict";
import { randomBytes } from "node:crypto";
import { after, before, describe, it } from "node:test";
import { doc, getDoc, setDoc } from "firebase/firestore";
import { deleteObject, ref, uploadBytes } from "firebase/storage";
import { createTestEnv } from "./helpers.mjs";

// Punta a punta (ADR 0008): subir y borrar en Storage dispara las funciones del emulador
// y actualiza users/{uid}.usedBytes.
// El cliente de pruebas sube por defecto al bucket "demo-pluk-reader", pero las funciones escuchan
// el bucket por defecto del proyecto (FIREBASE_CONFIG.storageBucket). Se indica explícito.
const DEFAULT_BUCKET = "gs://demo-pluk-reader.appspot.com";

describe("cuota punta a punta (ACC-003, LIB-009)", () => {
  let env;
  before(async () => {
    env = await createTestEnv();
  });
  after(async () => {
    await env.cleanup();
  });

  // Se lee como lo haría la app: con la sesión del dueño (ACC-003).
  const usedBytes = async (uid) =>
    (await getDoc(doc(env.authenticatedContext(uid).firestore(), `users/${uid}`))).get("usedBytes");

  async function waitForUsed(uid, expected, timeoutMs = 30000) {
    const start = Date.now();
    let last;
    while (Date.now() - start < timeoutMs) {
      last = await usedBytes(uid);
      if (last === expected) return;
      await new Promise((r) => setTimeout(r, 250));
    }
    assert.fail(`usedBytes de ${uid} era ${last} tras ${timeoutMs} ms, se esperaba ${expected}`);
  }

  it("subir suma el tamaño real y borrar lo libera", async () => {
    const uid = `e2e-${randomBytes(4).toString("hex")}`;
    await env.withSecurityRulesDisabled((ctx) =>
      setDoc(doc(ctx.firestore(), `users/${uid}`), { plan: "free", quotaBytes: 100000, usedBytes: 0, createdAt: new Date() }),
    );
    const file = ref(env.authenticatedContext(uid).storage(DEFAULT_BUCKET), `users/${uid}/books/${randomBytes(32).toString("hex")}.epub`);

    await uploadBytes(file, new Uint8Array(1234), { contentType: "application/epub+zip" });
    await waitForUsed(uid, 1234);

    await deleteObject(file);
    await waitForUsed(uid, 0);
  });
});
