# Alertas de área não mapeada e plantão sem estrutura

> Registro das entregas de 2026-11-20/21, posteriores às correções pós-Fatia 11.
> Para o estado operacional vigente, consulte `AGENTS.md`,
> `docs/Architecture.md` e `docs/Requirements.md`.

## Contexto

Dois pedidos vieram do uso real:

1. **A sinalização lançada fora do mapeamento desaparecia.** O guarda-vidas era
   aceito (`201`, `outcome=UNMAPPED`), o registro chegava à fila de triagem da
   coordenação — mas não aparecia em mapa nenhum. Na prática, o alerta existia
   justamente onde o KML está desatualizado e ninguém na praia o via.
2. **O plantão estava amarrado a uma estrutura.** `shift_sessions` exigia
   exatamente um `post_id` ou `mobile_base_id`, e a base móvel era criada e
   desativada pelo turno (ADR-025). Patrulha, apoio a evento e operação em área
   nova precisavam "inventar" uma base; e a base real perdia nome, fotos e
   histórico a cada encerramento.

O diagnóstico confirmou que o problema dos alertas **não era persistência**:
havia registros `PENDING` em `unmapped_signals`, mas nenhuma consulta de mapa
os lia.

## 1. Alerta de área não mapeada nos mapas (ADR-029)

- `V23` adiciona `unmapped_signals.valid_until` (backfill `created_at + 12 h`,
  default `now() + 12 h`, `NOT NULL`) e o índice parcial
  `idx_unmapped_signals_alert (valid_until DESC, id) WHERE status = 'PENDING'`.
- `FlagService` grava a validade do registro com a **mesma regra da bandeira**
  (`validityHours`, padrão 12 h, faixa 0,5–24 h).
- `GET /api/v1/unmapped-signals` (público, `Cache-Control: public, max-age=30`)
  devolve só `PENDING` e `valid_until > now()`, do mais recente para o mais
  antigo. Contrato próprio: não é `/api/v1/flags`, não tem praia/setor/ficha de
  área.
- A triagem (`GET /api/v1/management/unmapped-signals`) passa a devolver
  `validUntil` e a tela avisa até quando o alerta fica no mapa. Decidir
  (`IDENTIFIED`, `NEEDS_MAPPING`, `DISCARDED`) ou vencer tira o pin sem apagar
  a linha — a fila continua sendo a fonte completa.
- Frontend: `PublicUnmappedSignal`, `fetchUnmappedAlerts`,
  `useUnmappedAlerts` (polling de 30 s, pausado com a aba escondida),
  `UnmappedSignalSheet` (cor, validade, observação, fotos e aviso de área não
  mapeada) e o marcador `UNMAPPED_SIGNAL` (pino com interrogação e borda
  tracejada). Entra no mapa público (`RootLayout`) e na camada "Áreas não
  mapeadas" do mapa de situação, com atalho para
  `/gestao/areas-nao-identificadas`.

## 2. Plantão sem estrutura e base móvel autônoma (ADR-028)

- `V23` relaxa `ck_shift_target` de `num_nonnulls(post_id, mobile_base_id) = 1`
  para `<= 1`.
- `ShiftService.open` aceita nenhum destino — e então **exige** a posição do
  aparelho — e continua recusando posto + base simultâneos. `close`,
  `expireIfIdle` e `closeIdleShifts` **não desativam mais** a base;
  `StationService.deactivateMobileBaseIfUnused` foi removido.
- `JdbcEligibleRespondersRepository` ganhou um terceiro ramo para plantão livre:
  distância de `shift_sessions.last_position`, raio padrão da instalação e
  `position_stale`. O despacho grava só `recipient_user_id`, sem estrutura.
- `STATIONS_SQL` público mostra a base móvel ativa pela **posição cadastrada**,
  com ou sem plantão aberto. A posição do guarda-vidas continua separada
  (`/api/v1/management/team`, RN17).
- `ManagementTeamService` já tolerava estrutura nula; a linha mostra "Sem
  estrutura vinculada" e o pin usa a posição do turno.
- Frontend: `ShiftPanel` com três opções (posto fixo, base móvel, **sem
  estrutura**), base escolhida da lista de ativas (fim do `createStation` e do
  nome automático), casos vazios com link para `/operador/postos`;
  `ShiftStationCard` mostra um card informativo no plantão livre; textos de
  estações e do console deixam de dizer que a base some sem plantão vivo.

## Migração

`V23__unmapped_signal_alerts_and_free_shift.sql` — validade do alerta, índice
parcial e relaxamento de `ck_shift_target`. Nenhuma coluna nova em
`shift_sessions`.

## Testes e validação

- `ShiftControllerTests`: abertura livre (com e sem posição), recusa de dois
  destinos, base permanece ativa após encerrar/inatividade e base compartilhada.
- `ShiftIdleExpiryTests`: o lease e o sweeper não desativam a base.
- `PublicOperationsIntegrationTests`: base ativa no mapa pela posição cadastrada.
- `FlagLocationAndMediaTests`: alerta público antes da triagem, validade gravada
  e saída por decisão/vencimento.
- `SosDispatchEligibilityTests`: plantão livre dentro do raio padrão recebe SOS
  (sem posto/base no despacho) e fora do raio não recebe.
- `./gradlew clean check`: 254 testes, 0 falhas.
- `npm run lint` e `npm run build` verdes.

## Como validar localmente

1. Rebuild do backend (`docker compose build backend && docker compose up -d
   backend`) aplica a `V23`.
2. `GET /api/v1/unmapped-signals` deve devolver os registros `PENDING` dentro da
   validade (os antigos ganharam `created_at + 12 h`; se já venceram, só
   aparecem na triagem).
3. `/operador` → "Entrar de plantão" → **Sem estrutura** com a localização
   atual; conferir o pin do guarda-vidas em `/gestao`.
4. Cadastrar uma base móvel com nome e fotos em `/operador/postos`, abrir turno
   nela, encerrar e confirmar que a base continua ativa e no mapa.

## Decisões relacionadas

- `docs/adr/028-plantao-sem-estrutura-e-base-movel-autonoma.md`
- `docs/adr/029-alerta-de-sinalizacao-fora-do-mapeamento.md`
- ADR-024 (emenda 2) e ADR-025 (substituída) atualizadas.
