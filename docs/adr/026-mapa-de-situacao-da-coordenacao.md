# ADR-026: Mapa de situação da coordenação e cadência da posição do plantão

- Status: **Proposto (2026-11-13)** — decisão a confirmar ao abrir a branch
  `31-feature-painel-de-situacao-da-coordenacao`.
- Relacionadas: ADR-018 (despacho), ADR-020 (escala e caminho crítico), ADR-022/ADR-024
  (estruturas e sinalização), ADR-025 (base móvel), ADR-027 (auditoria).
- Requisitos: RF40, RN17, RNF07/RNF09 (desempenho/carga), matriz §13.2.

## Contexto

RF40 pede um painel de acompanhamento da coordenação. Hoje `ManagementTeamPage` é uma tela
de demonstração com `INITIAL_TEAM` em memória: posição, plantão e contadores não vêm do
servidor. A rota inicial de `/gestao` é a importação de mapa.

O plantão já guarda **um** ponto (`shift_sessions.last_position`, `last_position_at`,
`last_position_accuracy_m`) e o console o atualiza a cada **5 min** quando o GPS está
autorizado. Para o guarda-vidas é o suficiente: a posição serve à elegibilidade do SOS e ao
lease (RN17). Para a coordenação decidir "quem está onde agora" e mandar reforço, 5 min é
demais — e a leitura "tempo real" esbarra na regra de que a posição **não vira rastreamento
contínuo**.

## Decisão

### 1. Um mapa de situação, só para a coordenação (e TI)

`/gestao` (índice) passa a ser um mapa de tela cheia com quatro camadas ligáveis:

| Camada | Fonte | O que mostra |
| :-- | :-- | :-- |
| Equipe em plantão | `shift_sessions` abertos com lease vivo | pin na última posição, cor pelo estado do sinal (vivo / sem sinal há X min) |
| Estruturas ativas | `GET /api/v1/operations/stations` (visão de campo) | postos fixos e bases móveis com plantão vivo |
| Sinalizações vigentes | `GET /api/v1/flags` | pin por escopo (área/setor), como no mapa público |
| Ocorrências | `GET /api/v1/occurrences` (escopo) | ficha em atendimento e pendente de CIOSP |

- **Todo objeto é clicável:** o clique centraliza o mapa (`fitBounds`/`flyTo` do MapLibre) e
  abre a ficha já existente (`StationSheet`, `SignalSheet`, `OccurrenceSheet`). Não se cria
  ficha nova nem se duplica dado em outra tela.
- **A lista lateral de equipe é o índice do mapa:** clicar no nome centraliza no guarda-vidas;
  a lista ordena por "sem sinal há mais tempo" no topo.
- **Camadas em memória:** `useState`, sem `localStorage`/`sessionStorage` (regra do projeto),
  valendo para a sessão.
- **Sem coordenada fora do escopo:** a rota da equipe é autenticada e `no-store`; nada de
  posição em log, cache, métrica, endpoint público ou exportação (RN17).

### 2. Posição de painel: 60 s, um ponto, sem histórico

- O console de plantão envia a posição a cada **60 s** enquanto o plantão está aberto e a
  aba visível, reutilizando `PUT /api/v1/dispatch/shifts/current/position`. Sem GPS, o
  batimento continua por `POST /current/heartbeat` (lease), como hoje.
- **Um ponto por plantão.** A escrita sobrescreve `last_position`; não existe tabela de
  telemetria, série temporal, polyline nem "trajeto do dia". O que a coordenação vê é a
  posição **atual**, não o caminho.
- **TTL é o lease.** Plantão encerrado (manual, logout ou inatividade) some do mapa na
  primeira consulta seguinte. Posição mais velha que `maraberto.dispatch.position-stale-after`
  (30 min) aparece como "sem sinal", nunca como atual.
- **Aplicativo em segundo plano** não envia posição (a aba escondida não fala com o
  servidor). É a mesma regra do console atual — não se introduz rastreamento de fundo.

### 3. Polling, não stream

- O mapa consulta a equipe a cada **15 s** (visível), pausa com a aba escondida e consulta
  na hora ao voltar/reconectar, igual aos outros polls do mapa.
- **Não** se envia posição por WebSocket/STOMP nesta fatia. A ADR-020 é explícita: o broker
  entrega o caminho crítico do SOS; posição de painel é leitura periódica. Se a medição
  mostrar custo, o caminho é cache/posição efetiva, não abrir um segundo canal em tempo real.
- **Carga esperada:** 500 plantões × 1 POST/60 s ≈ 8,3 req/s de escrita pequena; a leitura
  da coordenação é 1 consulta a cada 15 s por console de gestão. Nada disso chega perto dos
  gatilhos da ADR-020 (p95 do SOS > 1 s, plantões > 2.000, tabelas > 10 M linhas). Medir
  com `EXPLAIN (ANALYZE, BUFFERS)` na consulta da equipe antes de indexar.

## Por que isso não viola a RN17

A RN17 proíbe **rastreamento contínuo e histórico de trajeto** de qualquer operador. Esta
decisão mantém:

- um único ponto, sobrescrito, **somente enquanto o plantão está aberto**;
- visibilidade restrita à coordenação/TI do escopo, em rota autenticada;
- ausência de histórico, de exportação aberta e de dado em log/cache/métrica;
- desaparecimento imediato ao encerrar o plantão.

O que muda é a **cadência** (5 min → 60 s) e a **finalidade explícita** (supervisão do
plantão em curso). A RN17 ganha a ressalva de cadência; não ganha histórico.

## Alternativas avaliadas

| Alternativa | Por que não |
| :-- | :-- |
| Manter 5 min e "vender" como tempo real | Não responde "onde ele está agora" em ocorrência ativa; a coordenação perde confiança no painel |
| WebSocket com posição contínua | Custo de fan-out (ADR-020, gargalo 2) sem requisito; a posição só precisa ser lida quando alguém olha o mapa |
| Rastreamento em segundo plano (app fechado) | Viola RN17 e a política de dados; consumo de bateria e base legal desproporcional |
| Tabela de telemetria para "histórico do turno" | Vira rastro permanente; se um dia for requisito, entra com retenção curta, base legal e ADR própria |
| Mostrar posição para todos os operadores | Fora do escopo da RN17 e desnecessário para o trabalho em campo |

## Consequências

- **Positivas:** coordenação enxerga o campo em uma tela, decide reforço e abre a ficha sem
  trocar de página; a home da gestão passa a ter valor operacional; nenhuma migração.
- **Negativas:** mais um poll no cliente e um POST/min por plantão; bateria levemente maior;
  a coordenação precisa de treinamento para ler "sem sinal" em vez de assumir posição velha.
- **Dívida aceita:** mapa de situação não é "tempo real" de subsegundo — é supervisão com
  até 60 s de atraso, e a tela diz isso.
- **Testes:** consulta da equipe lista só plantão aberto e com lease; plantão encerrado some;
  posição velha marca `stale`; camadas ligam/desligam; clique centraliza e abre a ficha;
  `ModularityTests` verde (leitura por portas `api/`).