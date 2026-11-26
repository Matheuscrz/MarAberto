import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { describeDevice } from "./device-label.ts";

describe("device-label", () => {
  it("identifica navegador e sistema operacional em desktop e mobile", () => {
    assert.deepEqual(
      describeDevice(
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36",
      ),
      { label: "Chrome · Linux", kind: "desktop" },
    );

    assert.deepEqual(
      describeDevice(
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Safari/604.1",
      ),
      { label: "Safari · iPhone", kind: "mobile" },
    );

    assert.deepEqual(
      describeDevice(
        "Mozilla/5.0 (Linux; Android 14; Pixel 8; wv) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36",
      ),
      { label: "App MarAberto · Android", kind: "mobile" },
    );

    assert.deepEqual(
      describeDevice(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0",
      ),
      { label: "Edge · Windows", kind: "desktop" },
    );
  });

  it("trata User-Agent vazio ou desconhecido sem erro", () => {
    assert.deepEqual(describeDevice(null), {
      label: "Dispositivo não identificado",
      kind: "unknown",
    });
    assert.deepEqual(describeDevice("curl/8.5.0"), {
      label: "Navegador ou aplicativo",
      kind: "unknown",
    });
  });
});
