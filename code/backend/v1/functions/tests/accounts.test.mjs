import { randomBytes } from "node:crypto";
import assert from "node:assert/strict";
import { before, describe, it } from "node:test";
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { FREE_PLAN, createUserDoc } from "../lib/accounts.js";

// ACC-002, ACC-005. Corre contra el emulador de Firestore.
describe("alta del documento de usuario", () => {
  let db;
  before(() => {
    try { initializeApp({ projectId: "demo-pluk-reader" }); } catch {}
    db = getFirestore();
  });
  const uid = () => `u-${randomBytes(6).toString("hex")}`;

  it("ACC-002: crea el plan gratuito con cuota menor a 20 MB y nada usado", async () => {
    const id = uid();
    assert.equal(await createUserDoc(db, id), true);
    const data = (await db.doc(`users/${id}`).get()).data();
    assert.equal(data.plan, "free");
    assert.equal(data.usedBytes, 0);
    assert.equal(data.quotaBytes, FREE_PLAN.quotaBytes);
    assert.ok(data.quotaBytes > 0 && data.quotaBytes < 20 * 1000 * 1000);
    assert.ok(data.createdAt);
  });

  it("ACC-005: el plan es un dato del documento, no del código del cliente", async () => {
    const id = uid();
    await createUserDoc(db, id);
    await db.doc(`users/${id}`).update({ plan: "pro", quotaBytes: 1e9 });
    assert.equal((await db.doc(`users/${id}`).get()).get("plan"), "pro");
  });

  it("un evento repetido no pisa el documento existente", async () => {
    const id = uid();
    await createUserDoc(db, id);
    await db.doc(`users/${id}`).update({ usedBytes: 777, plan: "pro" });
    assert.equal(await createUserDoc(db, id), false);
    const data = (await db.doc(`users/${id}`).get()).data();
    assert.equal(data.usedBytes, 777);
    assert.equal(data.plan, "pro");
  });
});
