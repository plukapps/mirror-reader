import { randomBytes } from "node:crypto";
import assert from "node:assert/strict";
import { before, describe, it } from "node:test";
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { applyPlans } from "../lib/accounts.js";
import { DEFAULT_PLANS } from "../lib/plans.js";

// ACC-009, ADR 0017. Corre contra el emulador de Firestore. Cada test usa planes propios
// para no depender de los usuarios que dejan otros tests.
describe("aplicar el catálogo a los usuarios", () => {
  let db;
  before(() => {
    try { initializeApp({ projectId: "demo-pluk-reader" }); } catch {}
    db = getFirestore();
  });
  const id = (prefix) => `${prefix}${randomBytes(4).toString("hex")}`;
  const plan = (storageGb, maxFileMb) => ({ storageGb, maxFileMb, priceMonthly: 1, priceYearly: 10, currency: "USD" });
  const seed = (uid, data) => db.doc(`users/${uid}`).set({ usedBytes: 0, createdAt: new Date(), ...data });
  const read = async (uid) => (await db.doc(`users/${uid}`).get()).data();

  it("ACC-009: actualiza solo a los usuarios del plan que cambió", async () => {
    const [a, b] = [id("a"), id("b")];
    const [changed, same] = [id("u-"), id("u-")];
    await seed(changed, { plan: a, quotaBytes: 1024 ** 3, maxFileBytes: 1024 ** 2 });
    await seed(same, { plan: b, quotaBytes: 2 * 1024 ** 3, maxFileBytes: 2 * 1024 ** 2 });

    await applyPlans(db, { ...DEFAULT_PLANS, [a]: plan(3, 7), [b]: plan(2, 2) });

    const updated = await read(changed);
    assert.equal(updated.quotaBytes, 3 * 1024 ** 3);
    assert.equal(updated.maxFileBytes, 7 * 1024 ** 2);
    const untouched = await read(same);
    assert.equal(untouched.quotaBytes, 2 * 1024 ** 3);
    assert.equal(untouched.maxFileBytes, 2 * 1024 ** 2);
  });

  it("ACC-009: completa maxFileBytes en usuarios creados antes del catálogo", async () => {
    const p = id("p");
    const uid = id("u-");
    // La cuota ya coincide: lo único que falta es el máximo por archivo.
    await seed(uid, { plan: p, quotaBytes: 1024 ** 3 });
    await applyPlans(db, { ...DEFAULT_PLANS, [p]: plan(1, 5) });
    assert.equal((await read(uid)).maxFileBytes, 5 * 1024 ** 2);
  });

  it("ACC-009: bajar el espacio por debajo de lo usado no borra nada ni toca usedBytes", async () => {
    const p = id("p");
    const uid = id("u-");
    await seed(uid, { plan: p, quotaBytes: 1024 ** 3, maxFileBytes: 1024 ** 2, usedBytes: 900 * 1024 ** 2, extra: "x" });
    await applyPlans(db, { ...DEFAULT_PLANS, [p]: plan(0.5, 1) });
    const data = await read(uid);
    assert.equal(data.quotaBytes, 512 * 1024 ** 2);
    assert.equal(data.usedBytes, 900 * 1024 ** 2);
    assert.equal(data.extra, "x");
    assert.equal(data.plan, p);
  });

  it("ACC-009: aplicar dos veces el mismo catálogo no cambia nada la segunda", async () => {
    const p = id("p");
    await seed(id("u-"), { plan: p, quotaBytes: 1, maxFileBytes: 1 });
    const plans = { ...DEFAULT_PLANS, [p]: plan(1, 1) };
    assert.ok((await applyPlans(db, plans)).updated >= 1);
    assert.equal((await applyPlans(db, plans)).updated, 0);
  });

  it("ACC-009: un usuario con un plan que no está en el catálogo queda igual", async () => {
    const uid = id("u-");
    const missing = id("x");
    await seed(uid, { plan: missing, quotaBytes: 123, maxFileBytes: 45 });
    const result = await applyPlans(db, DEFAULT_PLANS);
    const data = await read(uid);
    assert.equal(data.quotaBytes, 123);
    assert.equal(data.maxFileBytes, 45);
    assert.ok(result.unknownPlans.includes(missing));
  });
});
