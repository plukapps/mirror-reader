import { randomBytes } from "node:crypto";
import { after, before, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { doc, setDoc } from "firebase/firestore";
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
  beforeEach(async () => {
    HASH = randomHash();
    OTHER_HASH = randomHash();
    // Subir exige que exista el documento del usuario (cuota). Por defecto, con espacio de sobra.
    await env.withSecurityRulesDisabled((ctx) =>
      setDoc(doc(ctx.firestore(), "users/alice"), { plan: "free", quotaBytes: 1024 * MB, usedBytes: 0, createdAt: new Date() }),
    );
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

  describe("LIB-012: portadas, fuera de la cuota", () => {
    const JPEG = "image/jpeg";
    const coverPath = (uid, hash = HASH) => `users/${uid}/covers/${hash}.jpg`;
    // Subir una portada exige que exista el documento del libro.
    const seedBookDoc = (uid, hash = HASH) =>
      env.withSecurityRulesDisabled((ctx) =>
        setDoc(doc(ctx.firestore(), `users/${uid}/books/${hash}`), {
          title: "T", authors: [], filePath: path(uid, hash), sizeBytes: 10,
          createdAt: new Date(), updatedAt: new Date(), deletedAt: null,
        }),
      );
    const seedCover = (uid, hash = HASH) =>
      env.withSecurityRulesDisabled((ctx) =>
        uploadBytes(ref(ctx.storage(), coverPath(uid, hash)), bytes(10), { contentType: JPEG }),
      );
    const uploadCover = (uid, size = 100, { hash = HASH, contentType = JPEG, name } = {}) =>
      uploadBytes(ref(storage(uid), name ?? coverPath(uid, hash)), bytes(size), { contentType });

    it("el dueño sube y lee la portada de su libro", async () => {
      await seedBookDoc("alice");
      await assertSucceeds(uploadCover("alice"));
      await assertSucceeds(getBytes(ref(storage("alice"), coverPath("alice"))));
    });

    it("sin documento del libro no se sube la portada", async () => {
      await assertFails(uploadCover("alice"));
    });

    it("otro usuario no lee, sube ni borra portadas ajenas", async () => {
      await seedBookDoc("alice");
      await seedCover("alice");
      await assertFails(getBytes(ref(storage("bob"), coverPath("alice"))));
      await assertFails(uploadBytes(ref(storage("bob"), coverPath("alice", OTHER_HASH)), bytes(100), { contentType: JPEG }));
      await assertFails(deleteObject(ref(storage("bob"), coverPath("alice"))));
    });

    it("sin sesión no accede", async () => {
      await seedBookDoc("alice");
      await seedCover("alice");
      const s = env.unauthenticatedContext().storage();
      await assertFails(getBytes(ref(s, coverPath("alice"))));
    });

    it("rechaza lo que no es JPEG, un nombre que no es hash.jpg y un archivo vacío", async () => {
      await seedBookDoc("alice");
      await assertFails(uploadCover("alice", 100, { contentType: "image/png" }));
      await assertFails(uploadCover("alice", 100, { name: "users/alice/covers/portada.jpg" }));
      await assertFails(uploadCover("alice", 0));
    });

    it("acepta 1 MiB y rechaza más", async () => {
      await seedBookDoc("alice");
      await seedBookDoc("alice", OTHER_HASH);
      await assertSucceeds(uploadCover("alice", MB));
      await assertFails(uploadCover("alice", MB + 1, { hash: OTHER_HASH }));
    });

    it("no sobrescribe una portada existente", async () => {
      await seedBookDoc("alice");
      await seedCover("alice");
      await assertFails(uploadCover("alice", 200));
    });

    it("el dueño borra su portada", async () => {
      await seedBookDoc("alice");
      await seedCover("alice");
      await assertSucceeds(deleteObject(ref(storage("alice"), coverPath("alice"))));
    });

    it("no depende de la cuota: con la cuota llena se sigue subiendo", async () => {
      await seedBookDoc("alice");
      await env.withSecurityRulesDisabled((ctx) =>
        setDoc(doc(ctx.firestore(), "users/alice"), { plan: "free", quotaBytes: 1000, usedBytes: 1000, createdAt: new Date() }),
      );
      await assertSucceeds(uploadCover("alice"));
    });
  });

  describe("ACC-002 y LIB-009: cuota de espacio", () => {
    // El documento del usuario lo mantiene el servidor (ADR 0008): aquí se siembra con las reglas apagadas.
    const seedUser = (uid, { usedBytes = 0, quotaBytes = 1000 } = {}) =>
      env.withSecurityRulesDisabled((ctx) =>
        setDoc(doc(ctx.firestore(), `users/${uid}`), { plan: "free", quotaBytes, usedBytes, createdAt: new Date() }),
      );
    const upload = (uid, size, hash = HASH) =>
      uploadBytes(ref(storage(uid), path(uid, hash)), bytes(size), { contentType: EPUB });

    it("acepta una subida que cabe en el espacio libre", async () => {
      await seedUser("alice", { usedBytes: 400 });
      await assertSucceeds(upload("alice", 600));
    });

    it("rechaza una subida que pasa la cuota", async () => {
      await seedUser("alice", { usedBytes: 400 });
      await assertFails(upload("alice", 601));
    });

    it("rechaza cualquier subida con la cuota llena", async () => {
      await seedUser("alice", { usedBytes: 1000 });
      await assertFails(upload("alice", 1));
    });

    it("rechaza subir si no existe el documento del usuario", async () => {
      await assertFails(upload("carol", 10));
    });

    it("con la cuota llena se sigue leyendo y borrando", async () => {
      await seed("alice");
      await seedUser("alice", { usedBytes: 1000 });
      await assertSucceeds(getBytes(ref(storage("alice"), path("alice"))));
      await assertSucceeds(deleteObject(ref(storage("alice"), path("alice"))));
    });

    it("la cuota de un usuario no depende del uso de otro", async () => {
      await seedUser("alice", { usedBytes: 1000 });
      await seedUser("bob", { usedBytes: 0 });
      await assertFails(upload("alice", 1));
      await assertSucceeds(upload("bob", 500));
    });
  });
});
