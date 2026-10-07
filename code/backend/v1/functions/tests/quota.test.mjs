import { randomBytes } from "node:crypto";
import assert from "node:assert/strict";
import { before, describe, it } from "node:test";
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { addStoredFile, parseBookFilePath, parseObjectSize, removeStoredFile } from "../lib/quota.js";

const hash = () => randomBytes(32).toString("hex");

// Cuota (ADR 0008). Corre contra el emulador de Firestore (FIRESTORE_EMULATOR_HOST).
describe("contabilidad de cuota", () => {
  let db;
  before(() => {
    initializeApp({ projectId: "demo-pluk-reader" });
    db = getFirestore();
  });

  const newUser = async (usedBytes = 0) => {
    const uid = `u-${randomBytes(6).toString("hex")}`;
    await db.doc(`users/${uid}`).set({ plan: "free", quotaBytes: 1000, usedBytes });
    return uid;
  };
  const used = async (uid) => (await db.doc(`users/${uid}`).get()).get("usedBytes");

  describe("parseBookFilePath", () => {
    it("reconoce users/{uid}/books/{sha256}.epub", () => {
      const h = hash();
      assert.deepEqual(parseBookFilePath(`users/alice/books/${h}.epub`), { uid: "alice", hash: h });
    });
    it("ignora cualquier otra ruta", () => {
      for (const p of ["otra/cosa.epub", "users/alice/books/x.epub", "users/alice/otra/" + hash() + ".epub", `users/a/b/books/${hash()}.epub`]) {
        assert.equal(parseBookFilePath(p), null, p);
      }
    });
  });

  describe("parseObjectSize", () => {
    it("acepta el tamaño como texto (así llega en los eventos) y como número", () => {
      assert.equal(parseObjectSize("1234"), 1234);
      assert.equal(parseObjectSize(1234), 1234);
      assert.equal(parseObjectSize("0"), 0);
    });
    it("rechaza valores que no son un entero válido", () => {
      for (const v of [undefined, null, "", "abc", "1.5", -1, "-5", NaN, Infinity, {}]) {
        assert.throws(() => parseObjectSize(v), undefined, String(v));
      }
    });
  });

  describe("ACC-003: usedBytes refleja el espacio usado", () => {
    it("suma el tamaño real del archivo", async () => {
      const uid = await newUser();
      assert.equal(await addStoredFile(db, { uid, hash: hash() }, 300), true);
      assert.equal(await used(uid), 300);
    });

    it("suma varios archivos", async () => {
      const uid = await newUser();
      await addStoredFile(db, { uid, hash: hash() }, 300);
      await addStoredFile(db, { uid, hash: hash() }, 200);
      assert.equal(await used(uid), 500);
    });

    it("un evento duplicado no suma dos veces", async () => {
      const uid = await newUser();
      const file = { uid, hash: hash() };
      assert.equal(await addStoredFile(db, file, 300), true);
      assert.equal(await addStoredFile(db, file, 300), false);
      assert.equal(await used(uid), 300);
    });

    it("eventos simultáneos del mismo archivo suman una sola vez", async () => {
      const uid = await newUser();
      const file = { uid, hash: hash() };
      await Promise.all([1, 2, 3, 4, 5].map(() => addStoredFile(db, file, 300)));
      assert.equal(await used(uid), 300);
    });

    it("falla si el usuario no existe", async () => {
      await assert.rejects(addStoredFile(db, { uid: "no-existe-" + hash(), hash: hash() }, 10));
    });
  });

  describe("LIB-009: borrar libera espacio", () => {
    it("resta el tamaño con el que se contabilizó", async () => {
      const uid = await newUser();
      const file = { uid, hash: hash() };
      await addStoredFile(db, file, 300);
      assert.equal(await removeStoredFile(db, file), true);
      assert.equal(await used(uid), 0);
    });

    it("un borrado duplicado no resta dos veces", async () => {
      const uid = await newUser();
      const a = { uid, hash: hash() };
      const b = { uid, hash: hash() };
      await addStoredFile(db, a, 300);
      await addStoredFile(db, b, 200);
      assert.equal(await removeStoredFile(db, a), true);
      assert.equal(await removeStoredFile(db, a), false);
      assert.equal(await used(uid), 200);
    });

    it("borrar un archivo que nunca se contabilizó no cambia nada", async () => {
      const uid = await newUser(250);
      assert.equal(await removeStoredFile(db, { uid, hash: hash() }), false);
      assert.equal(await used(uid), 250);
    });

    it("si el usuario ya no existe, limpia el registro sin fallar", async () => {
      const uid = await newUser();
      const file = { uid, hash: hash() };
      await addStoredFile(db, file, 300);
      await db.doc(`users/${uid}`).delete();
      assert.equal(await removeStoredFile(db, file), true);
    });
  });
});
