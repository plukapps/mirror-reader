import assert from "node:assert/strict";
import { after, afterEach, before, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc, updateDoc } from "firebase/firestore";
import { createTestEnv } from "./helpers.mjs";

const HASH = "a".repeat(64);
const OTHER_HASH = "b".repeat(64);

const bookData = (uid, bookId = HASH, extra = {}) => ({
  title: "Un libro",
  authors: ["Alguien"],
  filePath: `users/${uid}/books/${bookId}.epub`,
  sizeBytes: 1000,
  createdAt: serverTimestamp(),
  updatedAt: serverTimestamp(),
  deletedAt: null,
  ...extra,
});

const collectionData = (extra = {}) => ({
  name: "Favoritos",
  bookIds: [HASH],
  createdAt: serverTimestamp(),
  updatedAt: serverTimestamp(),
  deletedAt: null,
  ...extra,
});

describe("Firestore", () => {
  let env;
  let alice;
  before(async () => {
    env = await createTestEnv();
  });
  afterEach(async () => {
    await env.clearFirestore();
  });
  after(async () => {
    await env.cleanup();
  });

  const db = (uid) => env.authenticatedContext(uid).firestore();
  const seedUser = (uid, data = {}) =>
    env.withSecurityRulesDisabled((ctx) =>
      setDoc(doc(ctx.firestore(), `users/${uid}`), {
        plan: "free",
        quotaBytes: 100000000,
        usedBytes: 0,
        createdAt: new Date(),
        ...data,
      }),
    );
  const seedBook = (uid, bookId = HASH) =>
    env.withSecurityRulesDisabled((ctx) =>
      setDoc(doc(ctx.firestore(), `users/${uid}/books/${bookId}`), {
        title: "Un libro",
        authors: [],
        filePath: `users/${uid}/books/${bookId}.epub`,
        sizeBytes: 1000,
        createdAt: new Date(),
        updatedAt: new Date(),
        deletedAt: null,
      }),
    );

  describe("ACC-004: cada usuario solo accede a lo suyo", () => {
    it("el dueño lee su documento de usuario", async () => {
      await seedUser("alice");
      await assertSucceeds(getDoc(doc(db("alice"), "users/alice")));
    });

    it("ACC-003: el dueño ve cuánto espacio usó y cuánto tiene", async () => {
      await seedUser("alice", { usedBytes: 250, quotaBytes: 1000 });
      const snap = await assertSucceeds(getDoc(doc(db("alice"), "users/alice")));
      assert.equal(snap.get("usedBytes"), 250);
      assert.equal(snap.get("quotaBytes"), 1000);
    });

    it("otro usuario no lee el documento de usuario", async () => {
      await seedUser("alice");
      await assertFails(getDoc(doc(db("bob"), "users/alice")));
    });

    it("sin sesión no lee nada", async () => {
      await seedUser("alice");
      await seedBook("alice");
      const anon = env.unauthenticatedContext().firestore();
      await assertFails(getDoc(doc(anon, "users/alice")));
      await assertFails(getDoc(doc(anon, `users/alice/books/${HASH}`)));
    });

    it("el dueño no puede leer el registro interno de archivos", async () => {
      await env.withSecurityRulesDisabled((ctx) =>
        setDoc(doc(ctx.firestore(), `users/alice/storedFiles/${HASH}`), { sizeBytes: 1 }),
      );
      await assertFails(getDoc(doc(db("alice"), `users/alice/storedFiles/${HASH}`)));
      await assertFails(setDoc(doc(db("alice"), `users/alice/storedFiles/${OTHER_HASH}`), { sizeBytes: 1 }));
    });

    it("el dueño crea y lee sus libros", async () => {
      const d = db("alice");
      await assertSucceeds(setDoc(doc(d, `users/alice/books/${HASH}`), bookData("alice")));
      await assertSucceeds(getDoc(doc(d, `users/alice/books/${HASH}`)));
    });

    it("otro usuario no lee ni escribe libros ajenos", async () => {
      await seedBook("alice");
      const d = db("bob");
      await assertFails(getDoc(doc(d, `users/alice/books/${HASH}`)));
      await assertFails(setDoc(doc(d, `users/alice/books/${OTHER_HASH}`), bookData("alice", OTHER_HASH)));
      await assertFails(updateDoc(doc(d, `users/alice/books/${HASH}`), { title: "x", updatedAt: serverTimestamp() }));
    });

    it("otro usuario no lee ni escribe colecciones ajenas", async () => {
      await env.withSecurityRulesDisabled((ctx) =>
        setDoc(doc(ctx.firestore(), "users/alice/collections/c1"), {
          name: "Favoritos", bookIds: [], createdAt: new Date(), updatedAt: new Date(), deletedAt: null,
        }),
      );
      const d = db("bob");
      await assertFails(getDoc(doc(d, "users/alice/collections/c1")));
      await assertFails(setDoc(doc(d, "users/alice/collections/c2"), collectionData()));
    });

    it("lo que no está en el modelo está denegado", async () => {
      await assertFails(setDoc(doc(db("alice"), "users/alice/annotations/a1"), { text: "x" }));
      await assertFails(setDoc(doc(db("alice"), "otra/cosa"), { x: 1 }));
    });
  });

  describe("ACC-005 y ACC-002: el cliente no controla su plan ni su cuota", () => {
    it("no puede crear su documento de usuario con otro plan", async () => {
      await assertFails(
        setDoc(doc(db("alice"), "users/alice"), {
          plan: "pro", quotaBytes: 999999999999, usedBytes: 0, createdAt: serverTimestamp(),
        }),
      );
    });

    it("no puede cambiar plan, cuota ni bytes usados", async () => {
      await seedUser("alice");
      const ref = doc(db("alice"), "users/alice");
      await assertFails(updateDoc(ref, { plan: "pro" }));
      await assertFails(updateDoc(ref, { quotaBytes: 999999999999 }));
      await assertFails(updateDoc(ref, { usedBytes: 0 }));
    });

    it("no puede borrar su documento de usuario", async () => {
      await seedUser("alice");
      await assertFails(deleteDoc(doc(db("alice"), "users/alice")));
    });
  });

  describe("SYN-007: los borrados son marcas, no borrado físico", () => {
    it("marcar un libro como borrado funciona", async () => {
      await seedBook("alice");
      await assertSucceeds(
        updateDoc(doc(db("alice"), `users/alice/books/${HASH}`), {
          deletedAt: serverTimestamp(), updatedAt: serverTimestamp(),
        }),
      );
    });

    it("deletedAt solo puede ser la hora del servidor, no una fecha del cliente", async () => {
      await seedBook("alice");
      const ref = doc(db("alice"), `users/alice/books/${HASH}`);
      await assertFails(updateDoc(ref, { deletedAt: new Date(2000, 1, 1), updatedAt: serverTimestamp() }));
      await assertFails(updateDoc(ref, { deletedAt: new Date(2999, 1, 1), updatedAt: serverTimestamp() }));
    });

    it("un libro ya borrado conserva su deletedAt al editarlo y puede restaurarse", async () => {
      const ref = doc(db("alice"), `users/alice/books/${HASH}`);
      await assertSucceeds(setDoc(ref, bookData("alice")));
      await assertSucceeds(updateDoc(ref, { deletedAt: serverTimestamp(), updatedAt: serverTimestamp() }));
      await assertSucceeds(updateDoc(ref, { title: "Editado", updatedAt: serverTimestamp() }));
      await assertSucceeds(updateDoc(ref, { deletedAt: null, updatedAt: serverTimestamp() }));
    });

    it("lo mismo vale para las colecciones", async () => {
      const ref = doc(db("alice"), "users/alice/collections/c1");
      await assertSucceeds(setDoc(ref, collectionData()));
      await assertFails(updateDoc(ref, { deletedAt: new Date(2000, 1, 1), updatedAt: serverTimestamp() }));
      await assertSucceeds(updateDoc(ref, { deletedAt: serverTimestamp(), updatedAt: serverTimestamp() }));
      await assertSucceeds(updateDoc(ref, { name: "Otro nombre", updatedAt: serverTimestamp() }));
    });

    it("el cliente no puede borrar físicamente un libro", async () => {
      await seedBook("alice");
      await assertFails(deleteDoc(doc(db("alice"), `users/alice/books/${HASH}`)));
    });

    it("el cliente no puede borrar físicamente una colección", async () => {
      await assertSucceeds(setDoc(doc(db("alice"), "users/alice/collections/c1"), collectionData()));
      await assertFails(deleteDoc(doc(db("alice"), "users/alice/collections/c1")));
    });

    it("marcar una colección como borrada funciona", async () => {
      await assertSucceeds(setDoc(doc(db("alice"), "users/alice/collections/c1"), collectionData()));
      await assertSucceeds(
        updateDoc(doc(db("alice"), "users/alice/collections/c1"), {
          deletedAt: serverTimestamp(), updatedAt: serverTimestamp(),
        }),
      );
    });
  });

  describe("SYN-006: las marcas de tiempo las pone el servidor", () => {
    it("rechaza un libro con updatedAt del reloj del cliente", async () => {
      await assertFails(
        setDoc(doc(db("alice"), `users/alice/books/${HASH}`), bookData("alice", HASH, { updatedAt: new Date(2000, 1, 1) })),
      );
    });

    it("rechaza actualizar un libro sin renovar updatedAt", async () => {
      await seedBook("alice");
      await assertFails(updateDoc(doc(db("alice"), `users/alice/books/${HASH}`), { title: "Nuevo" }));
    });

    it("actualizar un libro con updatedAt del servidor funciona", async () => {
      await seedBook("alice");
      await assertSucceeds(
        updateDoc(doc(db("alice"), `users/alice/books/${HASH}`), { title: "Nuevo", updatedAt: serverTimestamp() }),
      );
    });
  });

  describe("forma de los documentos de libro", () => {
    it("rechaza un bookId que no es un hash", async () => {
      await assertFails(setDoc(doc(db("alice"), "users/alice/books/no-es-hash"), bookData("alice", "no-es-hash")));
    });

    it("rechaza un filePath de otro usuario o de otro libro", async () => {
      const d = db("alice");
      await assertFails(setDoc(doc(d, `users/alice/books/${HASH}`), bookData("alice", HASH, { filePath: `users/bob/books/${HASH}.epub` })));
      await assertFails(setDoc(doc(d, `users/alice/books/${HASH}`), bookData("alice", HASH, { filePath: `users/alice/books/${OTHER_HASH}.epub` })));
    });

    it("rechaza campos faltantes, de más o de tipo equivocado", async () => {
      const d = db("alice");
      const ref = doc(d, `users/alice/books/${HASH}`);
      const { title, ...sinTitulo } = bookData("alice");
      await assertFails(setDoc(ref, sinTitulo));
      await assertFails(setDoc(ref, bookData("alice", HASH, { extra: "no" })));
      await assertFails(setDoc(ref, bookData("alice", HASH, { sizeBytes: "mil" })));
      await assertFails(setDoc(ref, bookData("alice", HASH, { sizeBytes: -1 })));
      await assertFails(setDoc(ref, bookData("alice", HASH, { title: "" })));
      await assertFails(setDoc(ref, bookData("alice", HASH, { title: "x".repeat(501) })));
    });

    it("rechaza crear un libro ya marcado como borrado", async () => {
      await assertFails(setDoc(doc(db("alice"), `users/alice/books/${HASH}`), bookData("alice", HASH, { deletedAt: serverTimestamp() })));
    });

    it("rechaza cambiar filePath, sizeBytes o createdAt de un libro existente", async () => {
      await seedBook("alice");
      const ref = doc(db("alice"), `users/alice/books/${HASH}`);
      await assertFails(updateDoc(ref, { sizeBytes: 5, updatedAt: serverTimestamp() }));
      await assertFails(updateDoc(ref, { filePath: `users/alice/books/${OTHER_HASH}.epub`, updatedAt: serverTimestamp() }));
      await assertFails(updateDoc(ref, { createdAt: serverTimestamp(), updatedAt: serverTimestamp() }));
    });
  });

  describe("LIB-005: colecciones", () => {
    it("el dueño crea una colección con varios libros", async () => {
      await assertSucceeds(
        setDoc(doc(db("alice"), "users/alice/collections/c1"), collectionData({ bookIds: [HASH, OTHER_HASH] })),
      );
    });

    it("rechaza nombre vacío o demasiado largo y campos de más", async () => {
      const ref = doc(db("alice"), "users/alice/collections/c1");
      await assertFails(setDoc(ref, collectionData({ name: "" })));
      await assertFails(setDoc(ref, collectionData({ name: "x".repeat(101) })));
      await assertFails(setDoc(ref, collectionData({ extra: 1 })));
      await assertFails(setDoc(ref, collectionData({ bookIds: "no-es-lista" })));
    });
  });
});
