import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { setGlobalOptions } from "firebase-functions/v2";
import * as functionsV1 from "firebase-functions/v1";
import { onObjectDeleted, onObjectFinalized } from "firebase-functions/v2/storage";
import { createUserDoc } from "./accounts.js";
import { addStoredFile, parseBookFilePath, parseObjectSize, removeStoredFile } from "./quota.js";

initializeApp();
// Misma región que Firestore y Storage del proyecto (ADR 0007).
setGlobalOptions({ region: "us-central1" });

// Las funciones de Storage deben estar en la región de su bucket. El bucket por defecto de
// mirror-reading-staging quedó en us-east1 (no se puede mover), mientras que Firestore está en us-central1.
const STORAGE_REGION = "us-east1";

export const onBookFileFinalized = onObjectFinalized({ region: STORAGE_REGION }, async (event) => {
  const file = parseBookFilePath(event.data.name);
  if (!file) return;
  await addStoredFile(getFirestore(), file, parseObjectSize(event.data.size));
});

export const onBookFileDeleted = onObjectDeleted({ region: STORAGE_REGION }, async (event) => {
  const file = parseBookFilePath(event.data.name);
  if (!file) return;
  await removeStoredFile(getFirestore(), file);
});

// ACC-001, ACC-002: al registrarse (email o Google) se crea el documento con el plan gratuito.
// Es la versión 1 del trigger porque la 2 solo ofrece funciones bloqueantes, que exigen Identity Platform.
// La región se fija aquí: setGlobalOptions no afecta a la API v1.
export const onUserCreated = functionsV1.region("us-central1").auth.user().onCreate(async (user) => {
  await createUserDoc(getFirestore(), user.uid);
});
