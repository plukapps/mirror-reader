import { after, afterEach, before, describe, it } from "node:test";
import { assertFails } from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc } from "firebase/firestore";
import { ref, getBytes } from "firebase/storage";
import { createTestEnv } from "./helpers.mjs";

// Humo de K-044: las reglas iniciales deniegan todo, incluso a un usuario con sesión.
describe("reglas iniciales (denegar todo)", () => {
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

  it("Firestore rechaza lectura y escritura con sesión", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(getDoc(doc(db, "users/alice")));
    await assertFails(setDoc(doc(db, "users/alice"), { plan: "free" }));
  });

  it("Firestore rechaza acceso sin sesión", async () => {
    const db = env.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, "users/alice")));
  });

  it("Storage rechaza lectura con sesión", async () => {
    const storage = env.authenticatedContext("alice").storage();
    await assertFails(getBytes(ref(storage, "users/alice/books/x.epub")));
  });
});
