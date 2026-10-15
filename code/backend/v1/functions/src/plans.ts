import * as logger from "firebase-functions/logger";
import type { RemoteConfig, RemoteConfigTemplate } from "firebase-admin/remote-config";

// Catálogo de planes (ACC-007, ADR 0017). Vive en el parámetro JSON `plans` de Remote Config;
// el backend lo aplica a users/{uid} (quotaBytes, maxFileBytes), que es lo que leen las reglas y la app.

export const PLANS_PARAMETER = "plans";

export interface Plan {
  storageGb: number;
  maxFileMb: number;
  priceMonthly: number;
  priceYearly: number;
  currency: string;
}

export type PlanCatalog = Record<string, Plan>;

export interface PlanLimits {
  quotaBytes: number;
  maxFileBytes: number;
}

// Igual al template del repo (remoteconfig.template.json); un test lo comprueba. Valores de PLN-001.
// Se usa al dar de alta un usuario si Remote Config no responde o el catálogo es inválido.
// free: 15 MiB expresados en GB (15 / 1024).
export const DEFAULT_PLANS: PlanCatalog = {
  free: { storageGb: 0.0146484375, maxFileMb: 2, priceMonthly: 0, priceYearly: 0, currency: "USD" },
  plus: { storageGb: 5, maxFileMb: 50, priceMonthly: 2.99, priceYearly: 24.99, currency: "USD" },
  pro: { storageGb: 50, maxFileMb: 100, priceMonthly: 5.99, priceYearly: 49.99, currency: "USD" },
};

const GB = 1024 ** 3;
const MB = 1024 ** 2;
const PLAN_ID = /^[a-z][a-z0-9_-]{0,31}$/;
const CURRENCY = /^[A-Z]{3}$/;

/** Unidades binarias, redondeando hacia abajo a bytes enteros. */
export function planLimits(plan: Plan): PlanLimits {
  return { quotaBytes: Math.floor(plan.storageGb * GB), maxFileBytes: Math.floor(plan.maxFileMb * MB) };
}

const isNumber = (value: unknown): value is number => typeof value === "number" && Number.isFinite(value);

function parsePlan(value: unknown): Plan | null {
  if (typeof value !== "object" || value === null || Array.isArray(value)) return null;
  const { storageGb, maxFileMb, priceMonthly, priceYearly, currency } = value as Record<string, unknown>;
  if (!isNumber(storageGb) || !isNumber(maxFileMb)) return null;
  if (!isNumber(priceMonthly) || priceMonthly < 0 || !isNumber(priceYearly) || priceYearly < 0) return null;
  if (typeof currency !== "string" || !CURRENCY.test(currency)) return null;
  const plan = { storageGb, maxFileMb, priceMonthly, priceYearly, currency };
  const { quotaBytes, maxFileBytes } = planLimits(plan);
  const validBytes = (bytes: number) => Number.isSafeInteger(bytes) && bytes > 0;
  return validBytes(quotaBytes) && validBytes(maxFileBytes) ? plan : null;
}

/** Devuelve el catálogo, o null si algo es inválido: un plan roto invalida el catálogo entero. */
export function parsePlans(json: string): PlanCatalog | null {
  let value: unknown;
  try {
    value = JSON.parse(json);
  } catch {
    return null;
  }
  if (typeof value !== "object" || value === null || Array.isArray(value)) return null;
  const catalog: PlanCatalog = {};
  for (const [id, raw] of Object.entries(value)) {
    const plan = PLAN_ID.test(id) ? parsePlan(raw) : null;
    if (!plan) return null;
    catalog[id] = plan;
  }
  return catalog.free ? catalog : null;
}

/** Solo cuenta el valor por defecto del parámetro: las condiciones no se usan (ADR 0017). */
export function plansFromTemplate(template: Pick<RemoteConfigTemplate, "parameters">): PlanCatalog | null {
  const defaultValue = template.parameters[PLANS_PARAMETER]?.defaultValue;
  if (!defaultValue || !("value" in defaultValue) || typeof defaultValue.value !== "string") return null;
  return parsePlans(defaultValue.value);
}

/**
 * Lee el catálogo publicado. Si no puede o es inválido, lo registra y devuelve DEFAULT_PLANS
 * con `fallback` (alta de usuario) o null sin él (aplicar cambios: mejor no tocar a nadie).
 */
export async function loadPlans(
  remoteConfig: Pick<RemoteConfig, "getTemplate">,
  { fallback }: { fallback: boolean },
): Promise<PlanCatalog | null> {
  try {
    const plans = plansFromTemplate(await remoteConfig.getTemplate());
    if (plans) return plans;
    logger.error(`Catálogo de planes inválido en Remote Config (parámetro "${PLANS_PARAMETER}")`);
  } catch (error) {
    logger.error("No se pudo leer el catálogo de planes de Remote Config", error);
  }
  return fallback ? DEFAULT_PLANS : null;
}
