import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  OCCUPANCY_BUCKET_MS,
  bucketStartOf,
  createOccupancySampler,
} from "./occupancy-sampler.ts";

describe("occupancy-sampler", () => {
  it("envia no máximo 1 amostra por janela de 15 min por setor em memória", async () => {
    let nowMs = 1_700_000_100_000;
    const sent: Array<{ latitude: number; longitude: number; token: string }> =
      [];

    const sampler = createOccupancySampler({
      token: "ephemeral-token-1234",
      now: () => nowMs,
      isHidden: () => false,
      sendSample: async (position, token) => {
        sent.push({ ...position, token });
      },
    });

    const coords = { latitude: -10.985, longitude: -37.045 };

    // Primeiro envio no setor-1: deve enviar.
    const first = await sampler.record(coords, "sector-1");
    assert.equal(first.sent, true);
    assert.equal(first.bucketStart, bucketStartOf(nowMs));
    assert.equal(sent.length, 1);

    // Segundo envio no mesmo setor dentro dos mesmos 15 min: travado.
    nowMs += 5 * 60 * 1000;
    const second = await sampler.record(coords, "sector-1");
    assert.equal(second.sent, false);
    assert.equal(sent.length, 1);

    // Mesmo instante em outro setor: deve enviar.
    const otherSector = await sampler.record(coords, "sector-2");
    assert.equal(otherSector.sent, true);
    assert.equal(sent.length, 2);

    // Virada da janela de 15 minutos para sector-1: deve enviar novamente.
    nowMs += OCCUPANCY_BUCKET_MS;
    const nextWindow = await sampler.record(coords, "sector-1");
    assert.equal(nextWindow.sent, true);
    assert.equal(sent.length, 3);
  });

  it("pausa o envio quando a aba está escondida e retoma ao ficar visível", async () => {
    let hidden = true;
    let calls = 0;

    const sampler = createOccupancySampler({
      token: "ephemeral-token-1234",
      now: () => 1_700_000_100_000,
      isHidden: () => hidden,
      sendSample: async () => {
        calls += 1;
      },
    });

    const coords = { latitude: -10.985, longitude: -37.045 };
    const paused = await sampler.record(coords, "sector-1");
    assert.equal(paused.sent, false);
    assert.equal(calls, 0);

    hidden = false;
    const resumed = await sampler.record(coords, "sector-1");
    assert.equal(resumed.sent, true);
    assert.equal(calls, 1);
  });

  it("trata 204 fora de área mapeada silenciosamente", async () => {
    let calls = 0;
    const sampler = createOccupancySampler({
      token: "ephemeral-token-1234",
      now: () => 1_700_000_100_000,
      isHidden: () => false,
      sendSample: async () => {
        calls += 1;
        // 204 No Content resolve sem corpo e sem erro.
      },
    });

    const result = await sampler.record(
      { latitude: -15.0, longitude: -40.0 },
      null,
    );
    assert.equal(result.sent, true);
    assert.equal(calls, 1);
    assert.equal(sampler.snapshot().length, 0);
  });

  it("não utiliza localStorage nem sessionStorage", async () => {
    let storageTouched = false;
    const fakeStorage = {
      getItem: () => {
        storageTouched = true;
        return null;
      },
      setItem: () => {
        storageTouched = true;
      },
      removeItem: () => {
        storageTouched = true;
      },
    };

    const g = globalThis as Record<string, unknown>;
    const prevLocal = g.localStorage;
    const prevSession = g.sessionStorage;
    g.localStorage = fakeStorage;
    g.sessionStorage = fakeStorage;

    try {
      const sampler = createOccupancySampler({
        token: "ephemeral-token-1234",
        now: () => 1_700_000_100_000,
        isHidden: () => false,
        sendSample: async () => undefined,
      });

      await sampler.record({ latitude: -10.985, longitude: -37.045 }, "sector-1");
      await sampler.record({ latitude: -10.985, longitude: -37.045 }, "sector-1");

      assert.equal(storageTouched, false);
    } finally {
      g.localStorage = prevLocal;
      g.sessionStorage = prevSession;
    }
  });
});
