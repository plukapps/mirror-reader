import { readFileSync } from "node:fs";
import { initializeTestEnvironment } from "@firebase/rules-unit-testing";

// Proyecto "demo-": el emulador nunca toca recursos reales.
const PROJECT_ID = "demo-pluk-reader";

export async function createTestEnv() {
  // Host y puerto salen de FIRESTORE_EMULATOR_HOST y FIREBASE_STORAGE_EMULATOR_HOST,
  // que define `firebase emulators:exec`.
  return initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: { rules: readFileSync("firestore.rules", "utf8") },
    storage: { rules: readFileSync("storage.rules", "utf8") },
  });
}
