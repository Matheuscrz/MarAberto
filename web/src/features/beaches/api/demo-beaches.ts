import type { Beach } from "@/features/beaches/types";

/**
 * Dados de demonstração para validar a interface enquanto o importador KML e
 * os endpoints públicos não existem. Substituir por dados reais; não usar em
 * produção. As coordenadas ficam próximas dos postos/sinalizações demo para o
 * mapa e a lista contarem a mesma história.
 */
export const beaches: readonly Beach[] = [
  {
    id: "praia-1",
    name: "Praia Principal",
    municipality: "Litoral",
    state: "BR",
    description:
      "Faixa central com posto fixo, acesso pavimentado e maior movimento de banhistas.",
    flag: "RED",
    riskLevel: "HIGH",
    occupancyPercent: 72,
    updatedAt: "2026-11-10T12:00:00.000Z",
    latitude: -10.985,
    longitude: -37.045,
    facilities: ["Posto de guarda-vidas", "Banheiros", "Quiosques", "Acesso acessível"],
    risks: [
      {
        id: "risco-1",
        type: "RIP_CURRENT",
        label: "Corrente de retorno",
        description:
          "Canal estreito entre bancos de areia com escoamento forte para o mar.",
        severity: "HIGH",
        guidance: [
          "Não entrar no mar na faixa sinalizada.",
          "Se for pego pela corrente, nade em paralelo à praia.",
          "Acene por ajuda mantendo a calma e flutuando.",
        ],
      },
      {
        id: "risco-2",
        type: "DEEP_WATER",
        label: "Buraco próximo à arrebentação",
        description:
          "Desnível repentino do fundo a poucos metros da areia, comum após a maré cheia.",
        severity: "MODERATE",
        guidance: [
          "Mantenha a água abaixo da linha da cintura.",
          "Caminhe arrastando os pés em trechos desconhecidos.",
        ],
      },
    ],
  },
  {
    id: "praia-2",
    name: "Praia do Farol",
    municipality: "Litoral",
    state: "BR",
    description:
      "Orla norte com mar calmo e base móvel de guarda-vidas em ronda na areia.",
    flag: "YELLOW",
    riskLevel: "MODERATE",
    occupancyPercent: 48,
    updatedAt: "2026-11-10T12:00:00.000Z",
    latitude: -10.968,
    longitude: -37.03,
    facilities: ["Base móvel", "Estacionamento", "Ciclofaixa"],
    risks: [
      {
        id: "risco-3",
        type: "ROCKS",
        label: "Rochas no costão",
        description:
          "Formações rochosas escorregadias e cobertas pela água na maré alta.",
        severity: "MODERATE",
        guidance: [
          "Evite caminhar sobre as pedras molhadas.",
          "Não mergulhe próximo ao costão.",
        ],
      },
    ],
  },
  {
    id: "praia-3",
    name: "Praia das Conchas",
    municipality: "Litoral",
    state: "BR",
    description:
      "Enseada abrigada, boa para famílias, com atenção a animais marinhos na maré baixa.",
    flag: "GREEN",
    riskLevel: "LOW",
    occupancyPercent: 35,
    updatedAt: "2026-11-10T12:00:00.000Z",
    latitude: -11.002,
    longitude: -37.055,
    facilities: ["Quiosques", "Área de sombra", "Rampa de acesso"],
    risks: [
      {
        id: "risco-4",
        type: "WILDLIFE",
        label: "Águas-vivas e caravelas",
        description:
          "Animais marinhos podem aparecer na linha d'água, principalmente após ventos fortes.",
        severity: "LOW",
        guidance: [
          "Não toque em animais encalhados na areia.",
          "Lave o contato apenas com água do mar e procure o posto.",
        ],
      },
    ],
  },
];

export function findBeach(id: string | undefined): Beach | undefined {
  return beaches.find((beach) => beach.id === id);
}
