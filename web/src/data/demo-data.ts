import type { Beach, EducationalVideo, TideDay } from '../types';

/**
 * Dados de demonstração do frontend.
 *
 * Enquanto os endpoints públicos de praias/marés não existem, a interface usa
 * estes valores e os sinaliza na tela. Nunca trate estes dados como informação
 * oficial de segurança: em caso de emergência, ligue 193.
 */
export const DEMO_DATA_NOTICE =
  'Dados de demonstração. A informação oficial é a do posto de guarda-vidas e a do 193.';

const now = new Date().toISOString();

export const beaches: Beach[] = [
  {
    id: 'atalaia',
    name: 'Praia de Atalaia',
    municipality: 'Aracaju',
    state: 'SE',
    description:
      'Praia urbana com faixa de areia extensa, boa para caminhada. Atenção à corrente de retorno nos trechos próximos aos espigões.',
    flag: 'YELLOW',
    riskLevel: 'MODERATE',
    occupancyPercent: 62,
    updatedAt: now,
    latitude: -10.995,
    longitude: -37.042,
    facilities: ['Posto de guarda-vidas', 'Chuveiro', 'Acessibilidade parcial', 'Quiosques'],
    risks: [
      {
        id: 'atalaia-rip',
        type: 'RIP_CURRENT',
        label: 'Corrente de retorno',
        description:
          'Corrente estreita que puxa para o mar entre as ondas. Pode parecer calma na superfície.',
        severity: 'HIGH',
        guidance: [
          'Não tente nadar contra a corrente.',
          'Nade paralelo à praia até sair do fluxo.',
          'Se não conseguir, flutue e peça ajuda.',
        ],
      },
      {
        id: 'atalaia-rocks',
        type: 'ROCKS',
        label: 'Rochas e espigões',
        description: 'Estruturas que criam redemoinhos e mudam a direção da corrente.',
        severity: 'MODERATE',
        guidance: ['Evite nadar perto das pedras.', 'Observe a sinalização do posto.'],
      },
    ],
  },
  {
    id: 'aruana',
    name: 'Praia de Aruana',
    municipality: 'Aracaju',
    state: 'SE',
    description:
      'Trecho mais tranquilo, procurado por famílias. Ainda assim, observe a bandeira e a orientação dos guarda-vidas.',
    flag: 'GREEN',
    riskLevel: 'LOW',
    occupancyPercent: 35,
    updatedAt: now,
    latitude: -11.006,
    longitude: -37.041,
    facilities: ['Posto de guarda-vidas', 'Estacionamento', 'Quiosques'],
    risks: [
      {
        id: 'aruana-deep',
        type: 'DEEP_WATER',
        label: 'Profundidade variável',
        description: 'O fundo muda rapidamente de profundidade em alguns pontos.',
        severity: 'LOW',
        guidance: ['Entre devagar.', 'Crianças devem estar acompanhadas.'],
      },
    ],
  },
  {
    id: 'viral',
    name: 'Praia do Viral',
    municipality: 'Barra dos Coqueiros',
    state: 'SE',
    description:
      'Praia aberta, com ondas mais fortes e correnteza. Evite banho em trechos sem posto ativo.',
    flag: 'RED',
    riskLevel: 'HIGH',
    occupancyPercent: 78,
    updatedAt: now,
    latitude: -10.908,
    longitude: -37.032,
    facilities: ['Posto de guarda-vidas sazonal', 'Estacionamento'],
    risks: [
      {
        id: 'viral-rip',
        type: 'RIP_CURRENT',
        label: 'Correnteza forte',
        description: 'Fluxo constante de saída, com maior risco em maré vazante.',
        severity: 'EXTREME',
        guidance: [
          'Não entre no mar sem orientação do posto.',
          'Prefira trechos rasos e próximos à areia.',
          'Em caso de emergência, ligue 193.',
        ],
      },
      {
        id: 'viral-wildlife',
        type: 'WILDLIFE',
        label: 'Fauna marinha',
        description: 'Presença de águas-vivas e animais marinhos em algumas épocas.',
        severity: 'MODERATE',
        guidance: ['Não toque em animais encalhados.', 'Avise o posto de guarda-vidas.'],
      },
    ],
  },
];

const tidePattern = [
  [
    { time: '03:12', heightMeters: 1.7, type: 'HIGH' as const },
    { time: '09:25', heightMeters: 0.4, type: 'LOW' as const },
    { time: '15:38', heightMeters: 1.8, type: 'HIGH' as const },
    { time: '21:52', heightMeters: 0.3, type: 'LOW' as const },
  ],
  [
    { time: '03:58', heightMeters: 1.6, type: 'HIGH' as const },
    { time: '10:08', heightMeters: 0.5, type: 'LOW' as const },
    { time: '16:21', heightMeters: 1.7, type: 'HIGH' as const },
    { time: '22:34', heightMeters: 0.4, type: 'LOW' as const },
  ],
  [
    { time: '04:44', heightMeters: 1.5, type: 'HIGH' as const },
    { time: '10:52', heightMeters: 0.6, type: 'LOW' as const },
    { time: '17:05', heightMeters: 1.6, type: 'HIGH' as const },
    { time: '23:18', heightMeters: 0.5, type: 'LOW' as const },
  ],
  [
    { time: '05:31', heightMeters: 1.4, type: 'HIGH' as const },
    { time: '11:38', heightMeters: 0.7, type: 'LOW' as const },
    { time: '17:51', heightMeters: 1.5, type: 'HIGH' as const },
    { time: '00:04', heightMeters: 0.6, type: 'LOW' as const },
  ],
  [
    { time: '06:20', heightMeters: 1.3, type: 'HIGH' as const },
    { time: '12:27', heightMeters: 0.8, type: 'LOW' as const },
    { time: '18:40', heightMeters: 1.4, type: 'HIGH' as const },
  ],
  [
    { time: '00:53', heightMeters: 0.7, type: 'LOW' as const },
    { time: '07:13', heightMeters: 1.2, type: 'HIGH' as const },
    { time: '13:20', heightMeters: 0.9, type: 'LOW' as const },
    { time: '19:34', heightMeters: 1.3, type: 'HIGH' as const },
  ],
  [
    { time: '01:48', heightMeters: 0.7, type: 'LOW' as const },
    { time: '08:11', heightMeters: 1.2, type: 'HIGH' as const },
    { time: '14:20', heightMeters: 0.9, type: 'LOW' as const },
    { time: '20:35', heightMeters: 1.2, type: 'HIGH' as const },
  ],
];

export const tides: TideDay[] = tidePattern.map((events, index) => {
  const date = new Date();
  date.setDate(date.getDate() + index);
  return {
    date: date.toISOString().slice(0, 10),
    weekday: new Intl.DateTimeFormat('pt-BR', { weekday: 'short' }).format(date),
    events,
    syzygy: index === 2 || index === 3,
    source: 'Tábua de demonstração (substituir pela fonte oficial da instalação)',
    updatedAt: now,
  };
});

export const educationalVideos: EducationalVideo[] = [
  {
    id: 'dQw4w9WgXcQ',
    title: 'Como identificar uma corrente de retorno',
    description:
      'Sinais visuais, comportamento da água e o que fazer se for pego por uma corrente.',
    duration: '4 min',
    topic: 'Correntes',
  },
  {
    id: 'M7lc1UVf-VE',
    title: 'Bandeiras e sinais do guarda-vidas',
    description: 'O significado das cores e como pedir ajuda sem se colocar em risco.',
    duration: '3 min',
    topic: 'Bandeiras',
  },
  {
    id: 'aqz-KE-bpKQ',
    title: 'Primeiros socorros na areia',
    description: 'Passos iniciais seguros enquanto o atendimento especializado não chega.',
    duration: '6 min',
    topic: 'Primeiros socorros',
  },
  {
    id: 'ScMzIvxBSi4',
    title: 'Crianças na praia: prevenção',
    description: 'Supervisão, distância segura da água e pontos de atenção.',
    duration: '5 min',
    topic: 'Prevenção',
  },
];

export function findBeach(id: string | undefined): Beach | undefined {
  return beaches.find((beach) => beach.id === id);
}
