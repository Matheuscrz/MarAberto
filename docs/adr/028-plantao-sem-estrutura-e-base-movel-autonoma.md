# ADR-028: Plantão sem estrutura e base móvel autônoma

- Status: **Aceito (2026-11-20)** — implementado com `V23`.
- Substitui: ADR-025 (ciclo de vida da base móvel) na parte que amarrava a base
  ao turno e exigia posto/base para abrir plantão.
- Relacionadas: ADR-017 (geoindexação do SOS), ADR-018 (seleção de
  destinatários), ADR-024 (estrutura em campo), ADR-026 (mapa de situação).
- Requisitos: RF11, RF39, RF40; RN17.

## Contexto

O plantão nasceu acoplado a uma estrutura: `shift_sessions` exigia exatamente um
`post_id` ou `mobile_base_id`, e a base móvel era criada e desativada pelo
próprio turno (ADR-025). O uso em campo mostrou dois problemas:

1. **Nem todo plantão tem estrutura.** Guarda-vidas em patrulha, apoio a evento,
   operação em área nova ou deslocamento entre postos precisam estar disponíveis
   para o SOS sem "inventar" uma base na posição atual.
2. **A base móvel é uma estrutura, não um turno.** Ela tem nome, fotos e um ponto
   de operação; precisa continuar no mapa e no seletor entre turnos. Criar uma
   base nova a cada plantão enche o cadastro de linhas descartáveis e apaga a
   identidade da estrutura usada pela equipe.

## Decisão

### 1. O plantão pode existir sem posto ou base

`POST /api/v1/dispatch/shifts` aceita zero ou um destino (`num_nonnulls(post_id,
mobile_base_id) <= 1`). Sem destino, a **posição do aparelho é obrigatória** e
vira a `last_position` do turno. É ela que entra na seleção espacial do SOS, com
o raio padrão da instalação (`maraberto.dispatch.default-coverage-radius-m`),
já que não há `coverage_radius_m` de estrutura para herdar.

- O console oferece três opções: **posto fixo**, **base móvel** e **sem
  estrutura**.
- Dois destinos ao mesmo tempo continuam recusados (`400`).
- Sem estrutura e sem posição também é recusado: um turno invisível para o
  PostGIS não pode receber chamado nem aparecer no mapa de situação.

### 2. A base móvel tem ciclo próprio

- Cadastro em `/operador/postos` (ou `/admin/postos`) com **nome, tipo e até 3
  fotos**, na posição atual.
- A base ativa aparece no mapa público e no mapa de situação **na posição
  cadastrada**, com ou sem plantão aberto. A posição do turno é do guarda-vidas e
  aparece separada, como pin de plantão.
- Encerrar o turno (manual, logout ou inatividade) **não desativa** a base.
  Desativar é decisão explícita do guarda-vidas na estrutura ou da
  coordenação/TI.
- Duas pessoas podem usar a mesma base; nada muda na linha por causa disso.
- `POST /api/v1/dispatch/shifts/targets` continua listando bases ativas, agora
  como estruturas reutilizáveis.

### 3. Seleção do SOS e painel da coordenação

`JdbcEligibleRespondersRepository` ganhou um terceiro ramo para plantões livres:
distância medida de `shift_sessions.last_position`, raio padrão da instalação e
`position_stale` quando não há posição recente. `ManagementTeamService` já
tolerava estação nula; a lista mostra "Sem estrutura" e o pin usa a posição do
turno. O despacho grava `recipient_user_id` sem posto/base — a constraint
`num_nonnulls(recipient_post_id, recipient_base_id, recipient_user_id) >= 1`
continua satisfeita.

### 4. Migração `V23`

- `ck_shift_target`: de `= 1` para `<= 1`.
- Nenhuma coluna nova em `shift_sessions`.
- Nenhuma mudança em `station_catalog` (`V22`).

## Alternativas

| Alternativa | Por que não |
| :-- | :-- |
| **Criar uma base sintética para cada plantão livre** | Polui o cadastro, exige nome automático e some ao encerrar — o oposto de uma estrutura com identidade |
| **Manter a posição do turno como posição da base** | Mistura duas entidades: o guarda-vidas pode estar a quilômetros da base, e o mapa passaria a mentir sobre onde a estrutura está |
| **Exigir posto/base e usar um "posto virtual"** | Continua impedindo patrulha e operação em área nova, que é o caso real |
| **Desativar a base ao encerrar o turno** | Foi a ADR-025; na prática apaga a identidade da estrutura e obriga recadastro diário |

## Consequências

- **Positivas:** qualquer plantão entra na seleção do SOS; o mapa separa
  guarda-vidas de estrutura; a base mantém nome, fotos e histórico; menos
  cadastros descartáveis.
- **Negativas:** plantão livre depende de GPS e usa um raio único; a base pode
  ficar ativa no mapa até alguém desativá-la (mitigado pela lista de ativos e
  pelo botão de desativar em campo).- **Testes:** abertura livre com e sem posição, recusa de dois destinos, base
  ativa sem plantão, encerramento/inatividade sem desativar, elegibilidade do
  plantão livre dentro e fora do raio.
