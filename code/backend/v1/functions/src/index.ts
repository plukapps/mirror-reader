import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getRemoteConfig } from "firebase-admin/remote-config";
import { setGlobalOptions } from "firebase-functions/v2";
import * as functionsV1 from "firebase-functions/v1";
import * as logger from "firebase-functions/logger";
import { onConfigUpdated } from "firebase-functions/v2/remoteConfig";
import { onObjectDeleted, onObjectFinalized } from "firebase-functions/v2/storage";
import { applyPlans, createUserDoc } from "./accounts.js";
import { DEFAULT_PLANS, loadPlans } from "./plans.js";
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

// ADR 0017. En el emulador no hay Remote Config: los tests nunca tocan el proyecto real.
const IN_EMULATOR = process.env.FUNCTIONS_EMULATOR === "true";

// ACC-001, ACC-002, ACC-007: al registrarse (email o Google) se crea el documento con los
// límites del plan gratuito del catálogo. Si Remote Config falla, se usan los valores por defecto.
// Es la versión 1 del trigger porque la 2 solo ofrece funciones bloqueantes, que exigen Identity Platform.
// La región se fija aquí: setGlobalOptions no afecta a la API v1.
export const onUserCreated = functionsV1.region("us-central1").auth.user().onCreate(async (user) => {
  const plans = IN_EMULATOR ? DEFAULT_PLANS : await loadPlans(getRemoteConfig(), { fallback: true });
  await createUserDoc(getFirestore(), user.uid, plans ?? DEFAULT_PLANS);
});

// ACC-009, ADR 0017: cada versión publicada del template (también un rollback) se aplica a los
// usuarios existentes. Si el catálogo es inválido no se toca a nadie: loadPlans ya lo registró.
export const onPlansUpdated = onConfigUpdated(async (event) => {
  const plans = await loadPlans(getRemoteConfig(), { fallback: false });
  if (!plans) return;
  const { updated, unknownPlans } = await applyPlans(getFirestore(), plans);
  logger.info(`Catálogo de planes v${event.data.versionNumber} aplicado: ${updated} usuarios actualizados`);
  if (unknownPlans.length > 0) logger.warn(`Usuarios con planes fuera del catálogo: ${unknownPlans.join(", ")}`);
});
