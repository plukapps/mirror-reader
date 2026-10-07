import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { setGlobalOptions } from "firebase-functions/v2";
import { onObjectDeleted, onObjectFinalized } from "firebase-functions/v2/storage";
import { addStoredFile, parseBookFilePath, parseObjectSize, removeStoredFile } from "./quota.js";

initializeApp();
// Misma región que Firestore y Storage del proyecto (ADR 0007).
setGlobalOptions({ region: "us-central1" });

export const onBookFileFinalized = onObjectFinalized(async (event) => {
  const file = parseBookFilePath(event.data.name);
  if (!file) return;
  await addStoredFile(getFirestore(), file, parseObjectSize(event.data.size));
});

export const onBookFileDeleted = onObjectDeleted(async (event) => {
  const file = parseBookFilePath(event.data.name);
  if (!file) return;
  await removeStoredFile(getFirestore(), file);
});
