import type { BeachFlagSignal } from "@/features/signals/types";

/**
 * Dados de demonstração da sinalização. `photoUrls` é uma galeria: o
 * guarda-vidas pode anexar mais de uma foto do local.
 */
export const beachFlagSignals: readonly BeachFlagSignal[] = [
  {
    id: "sig-01",
    beachId: "praia-1",
    beachName: "Praia Principal",
    flag: "RED",
    latitude: -10.983,
    longitude: -37.043,
    photoUrls: [
      "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=900&q=80",
      "https://images.unsplash.com/photo-1519046904884-53103b34b206?auto=format&fit=crop&w=900&q=80",
    ],
    lifeguardName: "Sgt. Silva",
    lifeguardId: "BM-10492",
    notes: "Corrente de retorno forte aberta após mudança de maré.",
    createdAt: "2026-11-10T12:10:00.000Z",
  },
  {
    id: "sig-02",
    beachId: "praia-2",
    beachName: "Praia do Farol",
    flag: "YELLOW",
    latitude: -10.9695,
    longitude: -37.0325,
    photoUrls: [
      "https://images.unsplash.com/photo-1502680390469-be75c86b636f?auto=format&fit=crop&w=900&q=80",
    ],
    lifeguardName: "Cb. Rocha",
    lifeguardId: "BM-20871",
    notes: "Buraco próximo à arrebentação; atenção na maré cheia.",
    createdAt: "2026-11-10T11:40:00.000Z",
  },
  {
    id: "sig-03",
    beachId: "praia-3",
    beachName: "Praia das Conchas",
    flag: "GREEN",
    latitude: -11.0005,
    longitude: -37.0535,
    photoUrls: [],
    lifeguardName: "Sd. Almeida",
    lifeguardId: "BM-30455",
    createdAt: "2026-11-10T10:55:00.000Z",
  },
];
