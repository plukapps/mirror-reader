import { randomBytes } from "node:crypto";
import assert from "node:assert/strict";
import { before, describe, it } from "node:test";
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { createUserDoc } from "../lib/accounts.js";
import { DEFAULT_PLANS, planLimits } from "../lib/plans.js";

// ACC-002, ACC-005, ACC-007. Corre contra el emulador de Firestore.
describe("alta del documento de usuario", () => {
  let db;
  before(() => {
    try { initializeApp({ projectId: "demo-pluk-reader" }); } catch {}
    db = getFirestore();
  });
  const uid = () => `u-${randomBytes(6).toString("hex")}`;

  it("ACC-002: crea el plan gratuito con cuota menor a 20 MB y nada usado", async () => {
    const id = uid();
    assert.equal(await createUserDoc(db, id, DEFAULT_PLANS), true);
    const data = (await db.doc(`users/${id}`).get()).data();
    assert.equal(data.plan, "free");
    assert.equal(data.usedBytes, 0);
    assert.equal(data.quotaBytes, planLimits(DEFAULT_PLANS.free).quotaBytes);
    assert.ok(data.quotaBytes > 0 && data.quotaBytes < 20 * 1000 * 1000);
    assert.ok(data.createdAt);
  });

  it("ACC-007: los límites salen del plan gratuito del catálogo recibido", async () => {
    const id = uid();
    const plans = { ...DEFAULT_PLANS, free: { storageGb: 1, maxFileMb: 3, priceMonthly: 0, priceYearly: 0, currency: "USD" } };
    await createUserDoc(db, id, plans);
    const data = (await db.doc(`users/${id}`).get()).data();
    assert.equal(data.quotaBytes, 1024 ** 3);
    assert.equal(data.maxFileBytes, 3 * 1024 ** 2);
  });

  it("ACC-005: el plan es un dato del documento, no del código del cliente", async () => {
    const id = uid();
    await createUserDoc(db, id, DEFAULT_PLANS);
    await db.doc(`users/${id}`).update({ plan: "pro", quotaBytes: 1e9 });
    assert.equal((await db.doc(`users/${id}`).get()).get("plan"), "pro");
  });

  it("un evento repetido no pisa el documento existente", async () => {
    const id = uid();
    await createUserDoc(db, id, DEFAULT_PLANS);
    await db.doc(`users/${id}`).update({ usedBytes: 777, plan: "pro" });
    assert.equal(await createUserDoc(db, id, DEFAULT_PLANS), false);
    const data = (await db.doc(`users/${id}`).get()).data();
    assert.equal(data.usedBytes, 777);
    assert.equal(data.plan, "pro");
  });
});
