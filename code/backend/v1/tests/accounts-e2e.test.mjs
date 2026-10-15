import assert from "node:assert/strict";
import { randomBytes } from "node:crypto";
import { after, before, describe, it } from "node:test";
import { doc, getDocFromServer } from "firebase/firestore";
import { ref, uploadBytes } from "firebase/storage";
import { createTestEnv } from "./helpers.mjs";
import { DEFAULT_PLANS, planLimits } from "../functions/lib/plans.js";

// Punta a punta (ACC-001, ACC-002): registrarse en el emulador de Auth dispara la función
// onUserCreated, que crea users/{uid}. Se usa la API REST de Auth, la misma que usan los SDK.
const DEFAULT_BUCKET = "gs://demo-pluk-reader.appspot.com";
const AUTH = `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}/identitytoolkit.googleapis.com/v1`;

async function authCall(path, body) {
  const res = await fetch(`${AUTH}/${path}?key=fake-api-key`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  assert.equal(res.status, 200, await res.clone().text());
  return res.json();
}

describe("cuentas punta a punta (ACC-001, ACC-002)", () => {
  let env;
  before(async () => {
    env = await createTestEnv();
  });
  after(async () => {
    await env.cleanup();
  });

  async function waitForUserDoc(uid, timeoutMs = 30000) {
    const start = Date.now();
    while (Date.now() - start < timeoutMs) {
      // Un cliente nuevo en cada intento: a veces un mismo cliente sigue respondiendo "no existe"
      // aunque la función ya creó el documento, y nunca lo ve (K-289). Siempre del servidor.
      const db = env.authenticatedContext(uid).firestore();
      const snap = await getDocFromServer(doc(db, `users/${uid}`)).catch(() => null);
      if (snap?.exists()) return snap.data();
      await new Promise((r) => setTimeout(r, 250));
    }
    assert.fail(`users/${uid} no se creó en ${timeoutMs} ms`);
  }

  it("ACC-001, ACC-002: registro con email crea el plan gratuito", async () => {
    const { localId } = await authCall("accounts:signUp", {
      email: `${randomBytes(4).toString("hex")}@example.com`,
      password: "una-clave-larga-123",
      returnSecureToken: true,
    });
    const data = await waitForUserDoc(localId);
    assert.equal(data.plan, "free");
    assert.equal(data.usedBytes, 0);
    // En el emulador la función usa el catálogo por defecto: no toca Remote Config (ADR 0017).
    assert.deepEqual(
      { quotaBytes: data.quotaBytes, maxFileBytes: data.maxFileBytes },
      planLimits(DEFAULT_PLANS.free),
    );
  });

  it("ACC-001: registro con Google (proveedor simulado) crea el plan gratuito", async () => {
    const idToken = JSON.stringify({ sub: randomBytes(6).toString("hex"), email: `${randomBytes(4).toString("hex")}@example.com`, email_verified: true });
    const { localId } = await authCall("accounts:signInWithIdp", {
      postBody: `id_token=${encodeURIComponent(idToken)}&providerId=google.com`,
      requestUri: "http://localhost",
      returnIdpCredential: true,
      returnSecureToken: true,
    });
    const data = await waitForUserDoc(localId);
    assert.equal(data.plan, "free");
  });

  it("ACC-002, ACC-010: el plan gratuito admite un libro chico y rechaza uno más grande que su máximo", async () => {
    const { localId } = await authCall("accounts:signUp", {
      email: `${randomBytes(4).toString("hex")}@example.com`,
      password: "una-clave-larga-123",
      returnSecureToken: true,
    });
    await waitForUserDoc(localId);
    const storage = env.authenticatedContext(localId).storage(DEFAULT_BUCKET);
    const path = () => `users/${localId}/books/${randomBytes(32).toString("hex")}.epub`;
    const meta = { contentType: "application/epub+zip" };

    await uploadBytes(ref(storage, path()), new Uint8Array(1024), meta);
    const { maxFileBytes } = planLimits(DEFAULT_PLANS.free);
    await assert.rejects(uploadBytes(ref(storage, path()), new Uint8Array(maxFileBytes + 1), meta), /unauthorized/);
  });
});
