import type { RiskGuide } from "@/features/learn/types";

/**
 * Conteúdo educativo de demonstração. Será substituído pelo CMS (`content`)
 * quando os endpoints públicos existirem. Os IDs de vídeo são marcadores.
 */
export const demoRiskGuides: readonly RiskGuide[] = [
  {
    id: "corrente-retorno",
    category: "RIP_CURRENT",
    categoryLabel: "Corrente de Retorno",
    title: "Correntes de Retorno (Valas)",
    description:
      "Fluxo intenso de água que recua da praia em direção ao alto-mar, responsável pela maioria dos afogamentos.",
    howToIdentify: [
      "Faixa de água mais escura ou amarronzada, aparentando calma entre ondas que quebram.",
      "Superfície da água com aspecto espumoso ou agitado movendo-se mar adentro.",
      "Detritos e algas flutuando rapidamente para fora da costa.",
    ],
    whatToDoIfAffected: [
      "Não entre em pânico e não nade contra a corrente em direção à areia.",
      "Nade em paralelo à linha da praia até escapar da força da corrente.",
      "Se não conseguir sair, flutue de costas e acene com um braço estendido pedindo socorro.",
    ],
    prevention: [
      "Respeite as bandeiras vermelhas fincadas na areia.",
      "Pergunte aos guarda-vidas onde estão as valas do dia.",
      "Mantenha a água sempre abaixo da linha do umbigo.",
    ],
    video: {
      youtubeId: "dQw4w9WgXcQ",
      title: "Como identificar e escapar de uma corrente de retorno",
      duration: "03:15",
    },
  },
  {
    id: "agua-viva-caravela",
    category: "WILDLIFE",
    categoryLabel: "Animais Marinhos",
    title: "Águas-vivas e Caravelas-portuguesas",
    description:
      "Cnidários que liberam toxinas ao contato com a pele, causando dor intensa e marcas lineares.",
    howToIdentify: [
      "Estruturas gelatinosas transparentes ou arroxeadas flutuando na linha d’água ou presas na areia.",
      "Presença de bandeiras ou avisos de animais marinhos no posto.",
    ],
    whatToDoIfAffected: [
      "Lave o local exclusivamente com água do mar ou vinagre (ácido acético 5%).",
      "Nunca utilize água doce, álcool, urina ou esfregue areia (isso dispara mais toxinas).",
      "Remova tentáculos visíveis cuidadosamente usando uma pinça ou a borda de um cartão rígido.",
      "Procure auxílio no posto de guarda-vidas ou atendimento médico de emergência (193/192).",
    ],
    prevention: [
      "Evite entrar no mar caso aviste exemplares na faixa de areia.",
      "Não toque em animais encalhados na areia, pois ainda queimam por dias.",
    ],
    video: {
      youtubeId: "dQw4w9WgXcQ",
      title: "Primeiros socorros para queimaduras de caravelas e águas-vivas",
      duration: "02:40",
    },
  },
  {
    id: "buracos-e-depressoes",
    category: "SURF_HOLES",
    categoryLabel: "Buracos e Bancos de Areia",
    title: "Buracos e Desníveis Repentinos",
    description:
      "Mudanças bruscas no relevo submarino causadas pela dinâmica das marés e das correntes.",
    howToIdentify: [
      "Perda súbita de apoio dos pés ao caminhar na arrebentação.",
      "Variações na coloração da água em trechos próximos da praia.",
    ],
    whatToDoIfAffected: [
      "Dê impulsos com as pontas dos pés no fundo em direção ao banco de areia mais raso.",
      "Adote a posição de flutuação se não alcançar o solo.",
    ],
    prevention: [
      "Caminhe arrastando os pés devagar ao adentrar locais desconhecidos.",
      "Nunca mergulhe de ponta ou pontapé em locais rasos sem visibilidade total do leito.",
    ],
  },
];
