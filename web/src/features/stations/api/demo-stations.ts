import type { LifeguardStation } from "@/features/stations/types";

/**
 * Dados de demonstração de postos/bases. Serão substituídos pelo endpoint de
 * operações. As fotos existem para validar a ficha de identificação do posto.
 */
export const lifeguardStations: readonly LifeguardStation[] = [
  {
    id: "pgv-01",
    name: "Posto Central 01",
    type: "FIXED",
    status: "ACTIVE",
    beachName: "Praia Principal",
    municipality: "Litoral",
    state: "BR",
    latitude: -10.985,
    longitude: -37.045,
    operatingHours: "07:00 às 18:00",
    contactPhone: "193",
    resources: [
      "Torre de Observação",
      "DEA",
      "Oxigênio",
      "Prancha de Salvamento",
    ],
    photoUrls: [
      "https://images.unsplash.com/photo-1502680390469-be75c86b636f?auto=format&fit=crop&w=900&q=80",
      "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=900&q=80",
    ],
    updatedAt: new Date().toISOString(),
  },
  {
    id: "bm-02",
    name: "Base Móvel Quadriciclo Alfa",
    type: "MOBILE",
    status: "ACTIVE",
    beachName: "Orla Norte",
    municipality: "Litoral",
    state: "BR",
    latitude: -10.965,
    longitude: -37.038,
    operatingHours: "08:00 às 17:00",
    contactPhone: "193",
    resources: ["Quadriciclo 4x4", "Kit Primeiros Socorros", "Flutuador"],
    photoUrls: [
      "https://images.unsplash.com/photo-1519046904884-53103b34b206?auto=format&fit=crop&w=900&q=80",
    ],
    updatedAt: new Date().toISOString(),
  },
];
