import { after, afterEach, before, describe, it } from "node:test";
import { assertFails } from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc } from "firebase/firestore";
import { ref, getBytes } from "firebase/storage";
import { createTestEnv } from "./helpers.mjs";

// Humo: lo que no está en el modelo sigue denegado, incluso con sesión.
// Storage deniega todo hasta K-046.
describe("denegado por defecto", () => {
  let env;
  before(async () => {
    env = await createTestEnv();
  });
  afterEach(async () => {
    await env.clearFirestore();
    await env.clearStorage();
  });
  after(async () => {
    await env.cleanup();
  });

  it("Firestore rechaza rutas fuera del modelo con sesión", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(getDoc(doc(db, "otra/cosa")));
    await assertFails(setDoc(doc(db, "otra/cosa"), { x: 1 }));
  });

  it("Firestore rechaza acceso sin sesión", async () => {
    const db = env.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, "otra/cosa")));
  });

  it("Storage rechaza lectura con sesión", async () => {
    const storage = env.authenticatedContext("alice").storage();
    await assertFails(getBytes(ref(storage, "users/alice/books/x.epub")));
  });
});
