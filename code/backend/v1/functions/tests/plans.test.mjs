import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, it } from "node:test";
import { DEFAULT_PLANS, loadPlans, parsePlans, planLimits, plansFromTemplate } from "../lib/plans.js";

// ACC-007 a ACC-008, ADR 0017. Lógica pura: no toca Remote Config ni el emulador.

const templateFile = JSON.parse(
  readFileSync(new URL("../../remoteconfig.template.json", import.meta.url), "utf8"),
);
const valid = (overrides = {}) =>
  JSON.stringify({
    free: { storageGb: 0.015, maxFileMb: 5, priceMonthly: 0, priceYearly: 0, currency: "USD" },
    pro: { storageGb: 10, maxFileMb: 50, priceMonthly: 5.99, priceYearly: 49.99, currency: "USD" },
    ...overrides,
  });
const fakeRemoteConfig = (getTemplate) => ({ getTemplate });

describe("catálogo de planes", () => {
  it("ACC-007: los valores por defecto del código son los del template del repo", () => {
    assert.deepEqual(plansFromTemplate(templateFile), DEFAULT_PLANS);
    assert.deepEqual(Object.keys(DEFAULT_PLANS).sort(), ["free", "plus", "pro"]);
  });

  it("el template cumple los límites de Remote Config (descripción de hasta 256 caracteres)", () => {
    for (const [name, parameter] of Object.entries(templateFile.parameters)) {
      assert.ok((parameter.description ?? "").length <= 256, name);
    }
  });

  it("ACC-007: pasa GB y MB a bytes binarios, redondeando hacia abajo", () => {
    // PLN-001: el plan gratuito conserva los 15 MiB de antes del catálogo.
    assert.deepEqual(planLimits(DEFAULT_PLANS.free), {
      quotaBytes: 15 * 1024 ** 2,
      maxFileBytes: 2 * 1024 ** 2,
    });
    assert.deepEqual(planLimits({ storageGb: 2, maxFileMb: 20, priceMonthly: 2.99, priceYearly: 24.99, currency: "USD" }), {
      quotaBytes: 2 * 1024 ** 3,
      maxFileBytes: 20 * 1024 ** 2,
    });
    // Fracción mayor a ,5: redondear al más cercano daría un byte de más.
    assert.deepEqual(planLimits({ storageGb: 1.9999999999, maxFileMb: 1.9999999, priceMonthly: 0, priceYearly: 0, currency: "USD" }), {
      quotaBytes: 2 * 1024 ** 3 - 1,
      maxFileBytes: 2 * 1024 ** 2 - 1,
    });
  });

  it("ACC-007: acepta un catálogo válido", () => {
    const plans = parsePlans(valid());
    assert.equal(plans.pro.priceMonthly, 5.99);
    assert.equal(plans.pro.priceYearly, 49.99);
    assert.equal(plans.free.maxFileMb, 5);
  });

  it("ACC-007: rechaza el catálogo entero si algo es inválido", () => {
    const invalid = [
      "no es json",
      "[]",
      "null",
      JSON.stringify({ pro: { storageGb: 1, maxFileMb: 1, priceMonthly: 1, priceYearly: 1, currency: "USD" } }), // falta free
      valid({ pro: { storageGb: -1, maxFileMb: 50, priceMonthly: 5.99, priceYearly: 49.99, currency: "USD" } }),
      valid({ pro: { storageGb: 0, maxFileMb: 50, priceMonthly: 5.99, priceYearly: 49.99, currency: "USD" } }),
      valid({ pro: { storageGb: 10, maxFileMb: "50", priceMonthly: 5.99, priceYearly: 49.99, currency: "USD" } }),
      valid({ pro: { storageGb: 10, maxFileMb: 50, priceMonthly: -1, priceYearly: 49.99, currency: "USD" } }),
      valid({ pro: { storageGb: 10, maxFileMb: 50, priceMonthly: 5.99, priceYearly: -1, currency: "USD" } }),
      valid({ pro: { storageGb: 10, maxFileMb: 50, priceMonthly: 5.99, priceYearly: 49.99, currency: "" } }),
      valid({ pro: { storageGb: 10, maxFileMb: 50, currency: "USD" } }),
      valid({ pro: { storageGb: 10, maxFileMb: 50, priceMonthly: 5.99, currency: "USD" } }), // falta el anual
      valid({ pro: { storageGb: 1e12, maxFileMb: 50, priceMonthly: 5.99, priceYearly: 49.99, currency: "USD" } }), // no entra en bytes
      valid({ pro: { storageGb: 10, maxFileMb: 1e-9, priceMonthly: 5.99, priceYearly: 49.99, currency: "USD" } }), // 0 bytes
      valid({ "Pro Plus": { storageGb: 10, maxFileMb: 50, priceMonthly: 5.99, priceYearly: 49.99, currency: "USD" } }),
      valid({ pro: null }),
    ];
    for (const json of invalid) assert.equal(parsePlans(json), null, json);
  });

  it("ACC-007: solo cuenta el valor por defecto del parámetro, no las condiciones", () => {
    const template = {
      parameters: {
        plans: {
          defaultValue: { value: valid() },
          conditionalValues: { ios: { value: valid({ pro: { storageGb: 99, maxFileMb: 1, priceMonthly: 0, priceYearly: 0, currency: "USD" } }) } },
        },
      },
    };
    assert.equal(plansFromTemplate(template).pro.storageGb, 10);
    assert.equal(plansFromTemplate({ parameters: {} }), null);
    assert.equal(plansFromTemplate({ parameters: { plans: { defaultValue: { useInAppDefault: true } } } }), null);
  });

  it("ACC-008: lee el template publicado", async () => {
    const rc = fakeRemoteConfig(async () => ({ parameters: { plans: { defaultValue: { value: valid() } } } }));
    assert.equal((await loadPlans(rc, { fallback: true })).pro.priceYearly, 49.99);
  });

  it("ACC-008: si no puede leer o el catálogo es inválido, usa los valores por defecto solo si se pide", async () => {
    const failing = fakeRemoteConfig(async () => { throw new Error("sin red"); });
    const broken = fakeRemoteConfig(async () => ({ parameters: { plans: { defaultValue: { value: "{" } } } }));
    for (const rc of [failing, broken]) {
      assert.deepEqual(await loadPlans(rc, { fallback: true }), DEFAULT_PLANS);
      assert.equal(await loadPlans(rc, { fallback: false }), null);
    }
  });
});
