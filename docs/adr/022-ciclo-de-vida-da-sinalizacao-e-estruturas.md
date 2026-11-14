# ADR-022: Ciclo de vida da sinalização e das estruturas operacionais

- Status: **Aceito** (2026-11-13) — implementado na branch de sinalização/postos com `V15`, testes de integração e telas reais (mapa, ficha, console do operador e painel da TI).
- Relacionadas: ADR-010 (zonas temporais), ADR-012 (dados sensíveis), ADR-013 (ocorrências), ADR-014? não — ver ADR-015 (frontend), ADR-016 (importação), ADR-021 (área de banho).
- Requisitos: RF01, RF07, RF12, RF38, RF40; RN01, RN03, RN17; RNF06.

## Contexto

A sinalização e as estruturas operacionais existiam só como dado de demonstração no frontend: o mapa mostrava bandeiras e postos mocados, sem validade, sem autoria e sem relação com o plantão. O banco já tinha `lifeguard_posts`/`mobile_bases` e `shift_sessions`, mas não respondia a três perguntas do banhista e da coordenação:

1. **A bandeira que está no mapa ainda vale?** A RN01 exige que a UI mostre "desatualizada" depois da validade (padrão 12 h) — nunca a cor como se fosse atual.
2. **O posto que aparece no mapa está operando?** Um posto desativado não pode continuar recebendo banhista nem plantão.
3. **A base móvel ainda está em campo?** Ela só existe no mapa enquanto houver plantão vivo; o plantão pode ser encerrado ou expirar por falta de sinal (lease).

Havia ainda duas restrições de privacidade: a posição do operador não pode virar pin público (RN17), e a coordenada do banhista nunca é persistida. E o histórico não pode ser destruído por expiração: ocorrências e plantões precisam continuar apontando para a mesma linha.

## Decisão

1. **Vigência é decidida na consulta, não por mutação.** Nenhuma bandeira é apagada nem "desativada" por tempo, e nenhuma estrutura é removida: `flag_updates` é append-only, `lifeguard_posts`/`mobile_bases` ganham `active`, `deactivated_at` e `status_note`. O predicado público é explícito: `closed_at IS NULL AND valid_until > now()` para a bandeira; `active` e, para a base móvel, plantão aberto com `last_seen_at` dentro do lease para a estrutura.
2. **A sinalização vencida sai do mapa e continua na ficha.** `GET /api/v1/flags` devolve só o que está vigente. `GET /api/v1/flags?beachId=&includeExpired=true` devolve o **último lançamento de cada escopo**, vigente ou não, para a ficha da praia mostrar a última cor com o selo "desatualizada" e a data em que venceu. `includeExpired` sem `beachId` é `400` (RFC 9457). A ficha nunca troca "sem sinalização" por "sinalização vencida".
3. **Bandeira por setor, com fallback para a área.** `sector_id` nulo vale para a praia inteira; com setor, vale só para ele. Setor sem lançamento próprio herda a cor da praia. Nome de setor é rótulo, não chave: dois setores podem ter o mesmo nome e o escopo é o `sector_id`.
4. **Lançamento no mesmo escopo fecha o anterior.** `flag_updates` guarda `closed_at`/`closed_by`; o fechamento e a criação acontecem sob `pg_advisory_xact_lock(hashtextextended(beach_id || ':' || sector_id))`, para dois consoles não deixarem duas bandeiras vigentes no mesmo escopo. O anterior mantém a validade original registrada.
5. **O pin da bandeira não é a posição do operador.** Lançamento de setor usa o centroide do setor; lançamento da área usa `ST_PointOnSurface(boundary)`. A posição do guarda-vidas continua restrita à coordenação (RN17), e a bandeira não grava coordenada — não há `latitude`/`longitude` em `flag_updates`.
6. **Estrutura desativada some do mapa e da seleção do plantão, mas não do histórico.** `active=false` tira o posto/base da consulta pública e de `GET /dispatch/shifts/targets`; `deactivated_at` é carimbado na primeira desativação e `status_note` guarda o motivo. Reativar limpa a data e **preserva** a nota, para o painel não perder o motivo de uma parada anterior.
7. **Base móvel viva = plantão vivo.** A consulta pública usa `COALESCE(shift_sessions.last_position, mobile_bases.location)` e exige `ended_at IS NULL` e lease dentro de `maraberto.dispatch.shift-idle-timeout` (15 min). Sem turno ou com lease vencido, a base desaparece do mapa — encerrar o plantão é a forma explícita de tirá-la de lá.
8. **Nada de job que "vence" dado de negócio.** Os jobs existentes só carimbam o motivo do encerramento do plantão (`IDLE_TIMEOUT`) e apontam o que precisa de revisão. A validade da bandeira e a visibilidade da estrutura são sempre calculadas no instante da leitura.
9. **Frontend sem mock e com polling curto.** As features `signals` e `stations` leem a API real; o mapa consulta estações/sinalização a cada 30 s e zonas de risco/ficha a cada 60 s, pausa com a aba escondida e revalida ao voltar. Nenhum token, coordenada ou posição é persistido no cliente.

## Alternativas

- **Expirar a bandeira com `UPDATE`/job:** rejeitada. Perde o valor histórico ("a bandeira era amarela às 14h") e cria corrida entre o job e o lançamento. A validade é atributo da linha, não estado mutável.
- **Apagar a sinalização vencida:** rejeitada. A ficha precisa mostrar a última cor conhecida e a coordenação precisa auditar quem sinalizou o quê (RNF06).
- **Desativar apagando a estrutura:** rejeitada. Plantões e ocorrências têm FK; apagar quebraria o histórico ou exigiria `ON DELETE SET NULL`, perdendo a referência do local.
- **Sumir com o posto inativo do painel também:** rejeitada. A TI precisa reativar a estrutura e ver o motivo da parada.
- **Posição da bandeira = posição do aparelho:** rejeitada. Transformaria o lançamento em rastreamento do operador e exporia posição sensível no mapa público (RN17).
- **Uma sinalização por área apenas:** rejeitada. Uma praia com 5 setores pode ter corrente de retorno em um trecho e mar calmo no resto; a setorização já existe desde a ADR-021.
- **Cache de 5 min para o mapa:** rejeitada. RF12 pede reflexo em até 60 s; 30 s no pin e 60 s na ficha atendem com baixo custo e sem Redis como fonte da verdade.

## Consequências

- **Positivas:** mapa e ficha ficam coerentes com a RN01; o banhista distingue ausência de sinalização de sinalização vencida; o posto inoperante não recebe procura; a base móvel some quando o turno acaba; plantões e ocorrências continuam íntegros; a posição do operador não vaza.
- **Negativas / dívidas:** `flag_updates` ainda não tem `media_id` — a foto da sinalização entra na fatia de imagens. O cadastro de postos/bases pela TI ainda é só ativação/desativação; a criação/edição de estrutura e a posição oficial ficam para uma fatia própria. O painel da coordenação ainda precisa dos contadores de sinalização por operador/praia/setor (RF40).
- **Privacidade:** a bandeira não persiste coordenada; a posição do operador permanece em `shift_sessions.last_position`, restrita à coordenação (ADR-012/RN17). A consulta pública de estruturas expõe só a posição da estrutura/base, nunca o trajeto.
- **Revisão:** reabrir se a RN01 mudar o padrão de validade por tipo de área ou se a foto da sinalização passar a ser obrigatória para publicar a bandeira.

## Referências

- `docs/Architecture.md` §7.3 (DDL), §7.11 (bandeira e estruturas), §7.12 (geoindexação)
- `docs/Requirements.md` RF01, RF12, RF38, RF40; RN01, RN03, RN17; RNF06
- ADR-010, ADR-012, ADR-016, ADR-021
- `backend/src/main/resources/db/migration/V15__flag_lifecycle_and_station_status.sql`

## Emenda (2026-11-13)

A [ADR-023](023-sinalizacao-por-localizacao-e-midia.md) fecha as duas dívidas registradas acima e ajusta o item 5 desta decisão:

- `flag_updates` **passou a guardar** `signal_position`/`position_accuracy_m`/`position_source` (`V16`), porque a bandeira pode ser plantada no ponto em que o guarda-vidas está. O ponto é da sinalização, não do operador: não há histórico de trajeto e a posição do plantão continua restrita à coordenação (RN17).
- A **foto da sinalização** chegou: `media_assets` + `flag_media` (até 3), upload pelo backend com re-codificação (EXIF/GPS descartados) e leitura pública com `ETag`.
- O **cadastro/reposição de posto e base** pela posição atual está implementado em `/api/v1/operations/stations`.

