import { randomBytes } from "node:crypto";
import { after, before, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { deleteObject, getBytes, ref, uploadBytes } from "firebase/storage";
import { createTestEnv } from "./helpers.mjs";

// clearStorage() no borra los archivos en este emulador: cada test usa hashes nuevos.
const randomHash = () => randomBytes(32).toString("hex");
let HASH;
let OTHER_HASH;
const EPUB = "application/epub+zip";
const MB = 1024 * 1024;
const bytes = (n) => new Uint8Array(n);
const path = (uid, hash = HASH) => `users/${uid}/books/${hash}.epub`;

describe("Storage", () => {
  let env;
  before(async () => {
    env = await createTestEnv();
  });
  beforeEach(() => {
    HASH = randomHash();
    OTHER_HASH = randomHash();
  });
  after(async () => {
    await env.cleanup();
  });

  const storage = (uid) => env.authenticatedContext(uid).storage();
  const seed = (uid, hash = HASH) =>
    env.withSecurityRulesDisabled((ctx) =>
      uploadBytes(ref(ctx.storage(), path(uid, hash)), bytes(10), { contentType: EPUB }),
    );

  describe("ACC-004: cada usuario solo accede a sus archivos", () => {
    it("el dueño sube y lee su libro", async () => {
      const s = storage("alice");
      await assertSucceeds(uploadBytes(ref(s, path("alice")), bytes(100), { contentType: EPUB }));
      await assertSucceeds(getBytes(ref(s, path("alice"))));
    });

    it("otro usuario no lee ni sube en la carpeta ajena", async () => {
      await seed("alice");
      const s = storage("bob");
      await assertFails(getBytes(ref(s, path("alice"))));
      await assertFails(uploadBytes(ref(s, path("alice", OTHER_HASH)), bytes(100), { contentType: EPUB }));
    });

    it("otro usuario no borra archivos ajenos", async () => {
      await seed("alice");
      await assertFails(deleteObject(ref(storage("bob"), path("alice"))));
    });

    it("sin sesión no lee ni sube", async () => {
      await seed("alice");
      const s = env.unauthenticatedContext().storage();
      await assertFails(getBytes(ref(s, path("alice"))));
      await assertFails(uploadBytes(ref(s, path("alice", OTHER_HASH)), bytes(100), { contentType: EPUB }));
    });

    it("rutas fuera de users/{uid}/books están denegadas", async () => {
      const s = storage("alice");
      await assertFails(uploadBytes(ref(s, "users/alice/otra/x.epub"), bytes(100), { contentType: EPUB }));
      await assertFails(uploadBytes(ref(s, "publico/x.epub"), bytes(100), { contentType: EPUB }));
    });
  });

  describe("LIB-002: solo archivos EPUB, de tamaño acotado", () => {
    it("rechaza un tipo que no es EPUB", async () => {
      await assertFails(uploadBytes(ref(storage("alice"), path("alice")), bytes(100), { contentType: "application/pdf" }));
      await assertFails(uploadBytes(ref(storage("alice"), path("alice")), bytes(100), { contentType: "text/html" }));
    });

    it("rechaza un nombre que no es hash.epub", async () => {
      const s = storage("alice");
      await assertFails(uploadBytes(ref(s, "users/alice/books/libro.epub"), bytes(100), { contentType: EPUB }));
      await assertFails(uploadBytes(ref(s, `users/alice/books/${HASH}`), bytes(100), { contentType: EPUB }));
    });

    it("rechaza un archivo vacío", async () => {
      await assertFails(uploadBytes(ref(storage("alice"), path("alice")), bytes(0), { contentType: EPUB }));
    });

    it("acepta 100 MB y rechaza más de 100 MB", async () => {
      await assertSucceeds(uploadBytes(ref(storage("alice"), path("alice")), bytes(100 * MB), { contentType: EPUB }));
      await assertFails(uploadBytes(ref(storage("alice"), path("alice", OTHER_HASH)), bytes(100 * MB + 1), { contentType: EPUB }));
    });
  });

  describe("el nombre es el hash: los archivos no se sobrescriben", () => {
    it("rechaza sobrescribir un archivo existente", async () => {
      await seed("alice");
      await assertFails(uploadBytes(ref(storage("alice"), path("alice")), bytes(200), { contentType: EPUB }));
    });
  });

  describe("LIB-008: quitar el archivo del servidor", () => {
    it("el dueño borra su archivo", async () => {
      await seed("alice");
      await assertSucceeds(deleteObject(ref(storage("alice"), path("alice"))));
    });
  });
});
