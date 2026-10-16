import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, it } from "node:test";

// ADR 0016: publicar el template reemplaza el del proyecto entero. Si falta un parámetro que usan las apps, el
// deploy lo borra sin aviso. Este test lo impide.
const template = JSON.parse(readFileSync(new URL("../remoteconfig.template.json", import.meta.url), "utf8"));

describe("Remote Config (ADR 0016)", () => {
  it("el template es un objeto con parámetros", () => {
    assert.equal(typeof template.parameters, "object");
  });

  // ACC-011, SYN-017: la sincronización de notas se prende y apaga; por defecto, prendida
  it("notes_sync_enabled es booleano y prendido por defecto", () => {
    const param = template.parameters.notes_sync_enabled;
    assert.ok(param, "falta notes_sync_enabled");
    assert.equal(param.valueType, "BOOLEAN");
    assert.equal(param.defaultValue.value, "true");
  });

  // GST-025: el banner sin cuenta de Inicio arranca en la opción A
  it("guest_banner_no_backup_enabled es booleano y apagado por defecto", () => {
    const param = template.parameters.guest_banner_no_backup_enabled;
    assert.ok(param, "falta guest_banner_no_backup_enabled");
    assert.equal(param.valueType, "BOOLEAN");
    assert.equal(param.defaultValue.value, "false");
  });

  it("las condiciones que usan los parámetros existen", () => {
    const names = new Set((template.conditions ?? []).map((c) => c.name));
    for (const [key, param] of Object.entries(template.parameters)) {
      for (const condition of Object.keys(param.conditionalValues ?? {})) {
        assert.ok(names.has(condition), `${key} usa la condición ${condition}, que no está en el template`);
      }
    }
  });
});
