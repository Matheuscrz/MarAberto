# ADR-025: Ciclo de vida da base móvel

- Status: **Proposto (2026-11-13)** — decisão a confirmar ao abrir a branch
  `30-fix-console-de-campo-sos-e-ocorrencias`. O resto do documento assume a opção A.
- Relacionadas: ADR-016 (áreas), ADR-022 (ciclo de vida da sinalização e das estruturas),
  ADR-024 (estrutura em campo), ADR-026 (painel da coordenação).
- Requisitos: RF11, RF39, RN17; `shift_sessions.mobile_base_id`, `mobile_bases`.

## Contexto

A base móvel é cadastrada pelo guarda-vidas na posição atual (`V16`, RF39) e entra no mapa
público **somente enquanto existe plantão vivo nela** (ADR-022/ADR-024). Fora do mapa,
porém, ela **continua `active=true`** — e `GET /api/v1/dispatch/shifts/targets` lista toda
base ativa. Resultado observado em uso: o seletor de plantão acumula bases de meses atrás,
em pontos que não correspondem a lugar nenhum. Escolher uma delas abre o plantão em uma
coordenada fantasma, e é dessa coordenada que sai a distância do SOS.

O posto fixo é o oposto: tem endereço físico estável, é cadastrado uma vez e reutilizado.
Base móvel não tem endereço: tem **posição de hoje**.

## Decisão (opção A)

**A base móvel pertence ao plantão, não ao cadastro permanente.**

1. **Nasce com o plantão.** Abrir plantão em base móvel é "cadastrar a base na posição
   atual" — o mesmo fluxo da `V16`/RF39, não a escolha de uma linha antiga numa lista.
2. **Morre com o plantão.** Ao encerrar o plantão (`close`, logout ou job de inatividade),
   a base usada é desativada: `active=false`, `deactivated_at=now()`,
   `status_note='Desativada ao encerrar o plantão'`. É **soft delete**: nada é apagado e
   `shift_sessions.mobile_base_id`, ocorrências e mídia continuam apontando para a linha.
3. **Nunca desativar base em uso.** Se existe **outro** plantão aberto na mesma base
   (`shift_sessions.mobile_base_id = :id AND ended_at IS NULL`), a base permanece ativa e a
   desativação é ignorada. A regra roda na mesma transação do encerramento.
4. **O seletor de plantão lista o que pode ser usado hoje:** postos fixos ativos + bases
   móveis ativas. Como a base do plantão encerrado já está inativa, ela não volta ao
   seletor — o sintoma desaparece sem lista negra nem filtro por data.
5. **O console de campo continua completo** (`GET /api/v1/operations/stations`, ADR-024):
   a base desativada aparece na lista, mas agrupada em "estruturas fora do mapa", com
   posição, data, precisão e motivo. O que muda é a exibição, não a permissão.
6. **Reabrir no mesmo ponto:** o guarda-vidas cadastra uma base nova na posição atual. A
   chance de ser o mesmo ponto exato é baixa e, quando for, o custo é um cadastro de 10 s
   com GPS — menor que o custo de escolher a base errada no meio de um chamado.

### Onde a regra mora

- `operations.application.ShiftService` (encerramento manual, inatividade e logout) chama
  `StationService.deactivateMobileBaseIfUnused(mobileBaseId)`.
- `StationService` é o dono do ciclo de vida da estrutura (já tem `setActive`); a operação é
  idempotente e não recria linha.
- O job de plantão (`ShiftIdleExpiryJob`/equivalente) aplica a mesma regra quando encerra
  por `IDLE_TIMEOUT`.
- Sem migração: `active`, `deactivated_at` e `status_note` existem desde a `V15`.

## Alternativas avaliadas

| Alternativa | Por que não |
| :-- | :-- |
| **Manter como está** (toda base ativa é destino para sempre) | É o bug: base fantasma entra no SOS com distância errada e o seletor fica inutilizável |
| **Apagar a linha (`DELETE`)** | Quebra `shift_sessions.mobile_base_id`, ocorrências e fotos; perde o histórico de onde a equipe esteve — o oposto de RNF06 |
| **Filtrar o seletor por idade da última posição** | Esconde o sintoma sem resolver a causa; uma base de ontem no lugar errado continua oferecida e a regra é invisível para o operador |
| **Destino por proximidade do aparelho** | Depende de GPS para abrir plantão (hoje não é obrigatório) e ainda oferece a base errada quando o guarda está perto |
| **Base como recurso permanente com "unidade móvel" nomeada (viatura)** | Faz sentido para frota com identificação fixa; não é o modelo atual (a base é um ponto de operação, não um veículo) — reavaliar se o CBM passar a numerar viaturas |

## Consequências

- **Positivas:** seletor enxuto e confiável; fim da base fantasma na decisão espacial do
  SOS; histórico preservado; nenhuma migração.
- **Negativas:** reabrir no mesmo local exige novo cadastro (aceito); o console acumula
  bases desativadas a longo prazo (mitigado pelo agrupamento e por um filtro de período na
  lista, sem apagar linha).
- **Testes:** encerrar plantão desativa a base e ela sai de `targets`; base compartilhada por
  dois plantões não é desativada; inatividade e logout seguem a mesma regra; reativar à mão
  continua possível pela TI/coordenação (ADR-024).