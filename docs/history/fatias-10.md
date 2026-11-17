# Histórico de Entregas — Fatia 10

> Registro da entrega da Fatia 10 (issue 63), concluída em 2026-11-17. Para o
> estado operacional vigente, consulte `AGENTS.md`, `docs/Architecture.md` e
> `docs/Requirements.md`.

## Escopo entregue

- **SOS do mais novo para o mais antigo (RF15).** `DispatchService` ordena a
  fila aberta por `created_at DESC, id DESC` e o atendimento em curso por
  `acknowledged_at DESC, id DESC`, no servidor. O cliente não reordena.
- **Encerrar o atendimento na tela do SOS (RF15).** O card "Em atendimento por
  você" ganhou **"Encerrar atendimento"**: o console localiza a ficha pareada
  por `GET /api/v1/occurrences?emergencyCallId=…&limit=1` e abre o mesmo
  `OccurrenceSheet` do console de ocorrências. O `close` grava relato/desfecho e
  fecha o chamado do SOS na mesma transação (`V20`, `SosCallCloser`). Chamado
  ainda sem ocorrência mostra mensagem e não trava a tela; a reconciliação de
  10 s tira o card sem F5.
- **Ciclo de vida da base móvel (RF11, ADR-025).** A base móvel pertence ao
  turno: o console cadastra uma base nova na posição atual ao abrir o plantão
  (`POST /api/v1/operations/stations`, `kind=MOBILE`) e não oferece bases antigas
  no seletor. Encerrar o plantão — manual, logout ou `IDLE_TIMEOUT` (expiração
  preguiçosa e sweep) — chama
  `StationService.deactivateMobileBaseIfUnused`: a base recebe `active=false`,
  `deactivated_at` e `status_note`, e sai de
  `GET /api/v1/dispatch/shifts/targets`. Nada é apagado; a linha permanece em
  `GET /api/v1/operations/stations`, e plantões, ocorrências e fotos continuam
  apontando para ela. Se outro plantão aberto usa a mesma base, a desativação é
  ignorada.
- **Paginação de ocorrências por cursor (RF35).** `GET /api/v1/occurrences`
  devolve `items`, `nextCursor` (`occurredAt` + `number`) e `hasMore`, com
  `limit` padrão 20 e teto 100, filtro `status` e `emergencyCallId`, e keyset
  `(occurred_at, number) < (beforeOccurredAt, beforeNumber)`. Encerrar uma
  ocorrência entre duas páginas muda o `status`, não a chave: a lista não repete
  nem pula registros. Cursor incompleto, instante fora do ISO-8601 ou número
  inválido respondem `400` RFC 9457 (`invalid-request`). As telas do operador e
  da coordenação ganharam "Carregar mais antigas".

## Migrações

Nenhuma. `V21` continua a última aplicada e `V22` segue livre. O keyset usa o
prefixo `occurred_at DESC` de `idx_occ_responder` (escopo do guarda-vidas) e o
plano do escopo global deve ser medido com `EXPLAIN (ANALYZE, BUFFERS)` em
volume real antes de abrir uma migration de índice.

## Testes

- `EmergencyDispatchAckTests`: ordem `DESC` da fila e do atendimento.
- `ShiftControllerTests`: base desativada ao encerrar, presença em
  `operations/stations` e desativação apenas quando o último plantão fecha.
- `ShiftIdleExpiryTests`: expiração preguiçosa e sweep com base móvel.
- `OccurrenceFromSosTests`: cursor estável com ocorrência encerrada entre
  páginas e `400` para cursor incompleto.
- `./gradlew clean check`, `npm run lint` e `npm run build` verdes.

## Documentação

`docs/Requirements.md` (RF15/RF35), `docs/Architecture.md` (§7.9.1, §7.11,
console SOS e encerramento) e `AGENTS.md` refletem o comportamento final.
