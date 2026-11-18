# Histórico de Entregas — Fatia 11

> Registro da entrega da Fatia 11 (issue 64), concluída em 2026-11-18. Para o
> estado operacional vigente, consulte `AGENTS.md`, `docs/Architecture.md` e
> `docs/Requirements.md`.

## Escopo entregue

- **Equipe real na coordenação (RF40).** `GET /api/v1/management/team`
  (`COORDINATOR`/`ADMIN`) substitui o `INITIAL_TEAM` da tela por uma consulta ao
  banco: plantões **abertos com lease vivo**, última posição (lat/lon,
  precisão, instante e `stale`), estado do lease, estrutura (posto fixo ou base
  móvel), praia/setor resolvidos por `ST_Contains` sobre a última posição e
  contadores do dia (ocorrências, atendimentos em campo, sinalizações e
  pendências no CIOSP). A resposta é `Cache-Control: no-store` +
  `Pragma: no-cache`; nome e matrícula vêm da nova porta pública
  `identity.api.OperatorProfiles`, porque o cadastro cifra o nome e só o módulo
  `identity` o decifra. Ordenação `last_position_at ASC NULLS FIRST`: quem está
  sem sinal há mais tempo encabeça a lista.
- **Mapa de situação como home de `/gestao` (ADR-026).** Mapa de tela cheia
  (`AreaShell flush`) com quatro camadas alternáveis em `useState` — sem
  `localStorage`/`sessionStorage`: guarda-vidas em plantão (poll de 15 s),
  postos/bases ativos (`GET /api/v1/stations`), sinalizações vigentes
  (`GET /api/v1/flags`) e ocorrências que ainda pedem ação
  (`GET /api/v1/occurrences?active=true`). Todo pin e cada item da lista
  lateral de equipe centralizam o mapa (`flyTo`) e abrem a ficha existente
  (`StationSheet`, `SignalSheet`, `OccurrenceSheet`). Sem nenhuma área de banho
  importada, a home mostra o convite a `/gestao/importar`.
- **Cadência da posição do plantão (RN17).** O console de campo passou a enviar
  `PUT /api/v1/dispatch/shifts/current/position` a cada **60 s** (era 5 min),
  com jitter de 5 s, somente com a aba visível; sem GPS, o batimento vazio de
  `204` renova o lease. É um único ponto sobrescrito — nenhuma tabela de
  trajeto, nenhum `watchPosition`, nenhuma coordenada no navegador. Encerrar o
  plantão (ou deixar o lease vencer) tira o guarda-vidas da lista e do mapa na
  leitura seguinte.
- **Aba de postos da coordenação (RF11).** `/gestao/postos` reusa
  `StationAdminTable` com `source="coordination"`: lê e ativa/desativa qualquer
  estrutura por `GET`/`PATCH /api/v1/operations/stations`, sem exigir o papel
  `ADMIN`. Desativar tira o pin do mapa público e o destino do seletor de
  plantão sem apagar plantões, ocorrências ou fotos.
- **Ocorrências ativas.** `GET /api/v1/occurrences` ganhou `active=true`
  (`status IN ('OPEN','IN_PROGRESS') OR ciosp_status = 'PENDING'`), mantendo o
  keyset `(occurred_at, number)`: uma ficha encerrada sem protocolo do CIOSP
  continua no mapa de situação; com protocolo, sai.
- **Mapa genérico.** `MapMarkerKind` ganhou `LIFEGUARD` e `OCCURRENCE`, e
  `MapFocus` virou união entre `bbox` (`BEACH`/`SECTOR`/`SCOPE`) e `POINT`.
  `MapView` centraliza com `flyTo` e desloca o ponto para a ficha não cobri-lo;
  o elemento raiz do marcador continua sem `transform`/`scale`/`transition`.

## Migrações

Nenhuma. `V21` continua a última aplicada e `V22` segue livre: `V8`/`V9`
(plantão e lease), `V15` (sinalização), `V17`/`V20` (ocorrência e
`responder_id`) já sustentam a consulta.

## Medição

`EXPLAIN (ANALYZE, BUFFERS)` da consulta da equipe no banco de dev (20
plantões, 13 ocorrências, 8 `flag_updates`): `seq scan` filtrado em
`shift_sessions`, `pkey` das estruturas, `idx_sector_geom` no `ST_Contains` e
`idx_occ_responder` no contador; execução ≈ 0,3 ms. Nenhum índice novo foi
criado — reavaliar com centenas de plantões simultâneos.

## Testes

- `ManagementTeamControllerTests`: lista com posição e contadores do dia,
  `stale` para posição velha, `no-store`/`Pragma`, remoção ao encerrar o
  plantão, plantão com lease vencido fora da lista e `403` para `LIFEGUARD`.
- `OccurrenceFromSosTests`: `active=true` mantém ficha encerrada com CIOSP
  pendente, remove a registrada e preserva o histórico completo.
- `ModularityTests` verde com a dependência `operations → identity.api`.
- `npm run lint`, `npm run build` e `./gradlew clean check` verdes.

## Documentação

`docs/Requirements.md` (RF40/RN17 já refletiam o alvo), `docs/Architecture.md`
(§7.11.1 e §13.2), `docs/adr/026-…` (Aceito + implementação), este histórico e
`AGENTS.md` (backlog 12–15).
