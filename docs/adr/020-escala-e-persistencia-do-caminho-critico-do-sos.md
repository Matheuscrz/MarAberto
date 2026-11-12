# ADR-020: Escala e persistência do caminho crítico do SOS

- Status: **Aceito** (2026-11-11). Aprova o **envelope de capacidade, os gargalos e os gatilhos numéricos** deste documento. Não autoriza, por si só, mudança de infraestrutura: cada ação da tabela de gatilhos só é executada quando o número medido disparar. Nenhum item abaixo está implementado além do que já existe (`V10`, outbox, escalada, lease).
- Relacionadas: ADR-001 (SOS sem conta), ADR-003 (uma instalação por órgão), ADR-004 (broker opcional), ADR-010 (zonas temporais), ADR-012 (dados sensíveis), ADR-017 (geoindexação), ADR-018 (seleção e entrega do SOS).
- Requisitos que balizam a análise: RF07, RF21, RF42, RN03, RN19, **RNF01 (p95 do SOS < 3 s)**.

## Contexto

O SOS é o único fluxo do sistema em que **um erro custa vida**: o chamado nasce no celular de um banhista anônimo, passa por uma decisão espacial, vira uma notificação no console de um guarda-vidas e é assumido por exatamente um deles. Todo o resto da plataforma (mapa, marés, ocorrências, gestão) pode ficar 30 s desatualizado; o SOS não.

O desenho atual (ADR-018) concentra as garantias no **PostgreSQL + PostGIS**:

| Etapa do caminho crítico | O que acontece hoje | Onde paga |
| --- | --- | --- |
| 1. Gravação do chamado | `INSERT emergency_calls` com `client_request_id UNIQUE` (idempotência), coordenada em `double precision` + coluna gerada `geography` (GiST) | 1 linha, 1 índice B-tree + 1 GiST |
| 2. Decisão de quem notificar | `ST_DWithin` + `ST_Distance` sobre `shift_sessions` abertas (lease `last_seen_at`), `COALESCE(last_position, estrutura)`, teto de 15 km, `UNION ALL` posto/base, `ORDER BY distance_m LIMIT 50` | varredura dos plantões **abertos** (índice parcial), por chamado |
| 3. Prova do despacho | 1 linha em `emergency_dispatches` por destinatário + 1 linha na outbox **cifrada** (AES-256-GCM) por destinatário, tudo na mesma transação | N+1 `INSERT`s |
| 4. Entrega | `@TransactionalEventListener(AFTER_COMMIT)` publica o frame STOMP em `/user/{userId}/queue/sos`; sweep de 5 s recupera o que falhou | recurso de app (sessão WebSocket), não de banco |
| 5. Assunção | `UPDATE … WHERE status='DISPATCHED'` (CAS) + cancelamento dos demais despachos | 1 transação curta, vencedor único |
| 6. Escalonamento | `SosEscalationJob` (10 s) reivindica `DISPATCHED` vencidos com `FOR UPDATE SKIP LOCKED` e abre o próximo anel (5→20 km) | 1 índice parcial, batch de 20 |

**O que essa arquitetura compra e um store alternativo perderia:**

- **ACID na assunção.** "Um chamado, um atendente" é um compare-and-swap (`UPDATE … WHERE status='DISPATCHED'`) **junto** com o cancelamento dos demais despachos. Num store sem transação multi-documento/linha, dois guarda-vidas podem sair para o mesmo banhista (ou ninguém sair, se o cancelamento passar na frente).
- **Idempotência sem coordenação extra.** `client_request_id UNIQUE` resolve retry de rede no celular sem tabela de idempotência separada.
- **Consulta espacial exata e indexada.** `ST_DWithin`/`ST_Contains` sobre `geography` com GiST, distância em metros, polígono de praia para a geofence. É o motivo de o PostGIS estar no projeto desde a `V1`.
- **Estado consultável e auditável.** Quem foi notificado, quando, em qual rodada, com qual distância e se a posição estava velha. Isso é o que permite responder "por que ninguém foi?" depois do fato.

## Envelope de capacidade (matemática, não sensação)

Premissas de uma instalação de **um estado** (ADR-003), temporada alta:

| Variável | Valor de projeto | Observação |
| --- | --- | --- |
| Chamados/dia | 5.000 | ~1 a cada 17 s; pico real concentrado em 4–6 h de sol |
| Pico instantâneo | **5 chamados/s sustentado**, **50/s em rajada de 60 s** | 25× e 250× a média — folga deliberada para Réveillon/afogamento múltiplo |
| Plantões abertos simultâneos | 500 | ~150 postos + 30 bases móveis, com troca de turno |
| Destinatários por chamado | 3 (média), **50 (teto)** | `max-recipients` é teto de segurança, não seleção |
| Anéis por chamado sem ack | 6 | 5, 7, 10, 12, 15, 20 km, 20 s cada |

Derivados:

- **Escritas por chamado:** `1 (call) + N (dispatches) + N (outbox)`. Com média de 3 destinatários → **7 linhas**; no teto → 101 linhas.
- **Escritas por segundo no pico sustentado (5/s):** ~35 linhas/s na média; **~505 linhas/s** no pior caso teórico (todos os chamados com 50 destinatários). Um Postgres em hardware modesto faz **milhares** de `INSERT`s simples por segundo; esse número só viraria gargalo em ordem de grandeza superior.
- **Volume anual:** 5.000 chamados/dia × 365 ≈ 1,8 M chamados/ano; com 3 destinatários ≈ 5,5 M linhas em `emergency_dispatches` e outras 5,5 M na outbox por ano. A **~250 B/linha com índices** → ~3 GB/ano nas duas tabelas somadas. Retenção total por 5 anos ≈ 15 GB: cabe em disco e em RAM de qualquer servidor atual.
- **Leitura espacial por chamado:** varredura dos plantões **abertos** (índice parcial da `V8`). Com 500 abertos × ~200 B = **~100 KB lidos por chamado**, em buffer cache. A 5 chamados/s são ~500 KB/s de leitura de cache — irrelevante. A 50/s, ~5 MB/s — ainda confortável.
- **Cifra do payload:** AES-256-GCM de ~300 B custa **microssegundos**. Não é gargalo em nenhum cenário plausível.
- **Lease do plantão:** ~12 requisições de ~200 B por hora por console aberto. Com 500 consoles → **1,7 req/s** e ~340 B/s. Não é carga; é ruído.

**Conclusão do envelope:** para uma instalação estadual, o desenho atual tem **duas a três ordens de grandeza de folga** antes de o banco ser o gargalo. O risco real não é volume — é **latência de uma consulta não indexável** e **contenção de pool** no dia em que a instalação inteira abrir o console ao mesmo tempo.

## Gargalos, em ordem de probabilidade

1. **`COALESCE(last_position, location)` não usa índice.** A elegibilidade precisa decidir pela posição do **plantão**, não pela estrutura (foi o bug real dos 207 km). Isso impede um `ST_DWithin` indexado por GiST — a consulta varre os plantões abertos e calcula a distância linha a linha. Com centenas de plantões é barato (é a decisão consciente da ADR-018). Com **milhares** de plantões abertos e rajada de chamados, vira o primeiro custo de CPU do banco.
   - *Mitigação sem trocar de banco:* índice funcional parcial — `CREATE INDEX … ON shift_sessions USING gist ((COALESCE(last_position, post_position))) WHERE ended_at IS NULL` **não** é possível porque a estrutura não está na linha; a saída é desnormalizar a posição efetiva para `shift_sessions.position_effective` (coluna gerada/manida na escrita do plantão) e indexar essa coluna com GiST. **Gatilho abaixo.**
2. **Fan-out de entrega no processo.** Cada frame STOMP é serializado e escrito na sessão WebSocket. O custo cresce com destinatários × tamanho do frame, **e não é do banco**: é memória e CPU da instância que hospeda a sessão. Com 500 consoles numa única instância, um chamado com 50 destinatários gera 50 frames; uma rajada de 50 chamados gera 2.500 frames. Aqui a saída é **mais instâncias** (com fan-out pub/sub, ADR-004/Rabbit ou Redis) — não NoSQL.
3. **Contenção de pool de conexões.** O `POST /emergency-calls` compete por conexão com o sweep da outbox (5 s), o job de escalonamento (10 s) e o job de plantão (10 min). Pool pequeno + pico = chamado esperando conexão. *Mitigação:* pool separado por finalidade (`hikari` do request vs. jobs) ou jobs com `maximum-pool-size` próprio. Não requer infraestrutura nova.
4. **Crescimento das tabelas de histórico.** `emergency_dispatches`, `emergency_calls` e `emergency_outbox_events` crescem para sempre hoje — **não há purga nem arquivamento**. O efeito não é lentidão de consulta (todas usam índice parcial de status/`next_escalation_at`), é **autovacuum, bloat e backup**: uma tabela de 10 M linhas com 5 índices pesa no `VACUUM`, no `pg_dump` e na restauração. *Mitigação:* retenção + particionamento (abaixo). É manutenção, não escala de leitura.
5. **Outbox como fila única.** Eventos `PENDING`/`FAILED` compartilham a tabela com o histórico publicado. Sem purga, o índice de status incha. *Mitigação:* apagar publicados com mais de N dias (mantendo o histórico de despacho em `emergency_dispatches`, que é a prova que interessa).
6. **Escalonamento e assunção disputando as mesmas linhas.** Já mitigado por `SKIP LOCKED` + índice parcial; só vira problema com batch grande e muitas instâncias (hoje: 20 e uma instância de teste).
7. **Backup e restauração (RNF de continuidade).** Um banco de 15 GB restaura em minutos; 150 GB, em horas. É o argumento mais forte a favor de particionar/arquivar — e o mais fraco a favor de NoSQL (que também precisa de backup).

## Alternativas avaliadas (com critério de aceitação)

### A. Escala vertical (mais CPU/RAM/disco) — **primeira opção sempre**
- **Vence quando:** o gargalo é CPU (`EXPLAIN` mostra cálculo de distância) ou I/O de cache.
- **Custo:** dinheiro, zero mudança de código/operador.
- **Critério para adotar:** p95 do `POST /emergency-calls` > 1 s (um terço do orçamento de 3 s) com CPU da instância do banco > 70% em pico, e o `EXPLAIN (ANALYZE, BUFFERS)` mostrando que o tempo está no banco.
- **Depois disso:** desnormalizar `position_effective` + GiST antes de qualquer troca de tecnologia.

### B. Réplica de leitura — **não resolve o caminho crítico**
- O SOS é **escrita + leitura na mesma transação**. Uma réplica atrasada escolheria destinatários com plantões desatualizados (quem saiu ainda elegível; quem entrou ainda invisível) — exatamente o erro que o lease existe para evitar.
- **Uso legítimo:** painel da gestão, relatórios de ocorrências, analytics. **Critério:** consultas analíticas da coordenação passando de 1 s e competindo com o SOS no primário.

### C. Particionamento (declarativo, por mês, em `created_at`) — **manutenção, não velocidade**
- `emergency_calls` e `emergency_dispatches` particionadas por `RANGE (created_at)`, com retenção (ex.: 24 meses quentes + arquivo). `emergency_outbox_events` purgada por status publicado.
- **Critério:** qualquer tabela do SOS > **10 M linhas**, ou backup/restore acima de 30 min, ou autovacuum de `emergency_dispatches` acima de 60 s.
- **Custo:** migração com `ACCESS EXCLUSIVE` em tabela viva (janela de manutenção), chave primária composta com a coluna de partição, `detach`/`drop` de partições antigas.
- **Ganho:** consultas do SOS continuam iguais (índices locais menores), backup incremental por partição e retenção trivial.

### D. Redis na decisão — **recusado para o caminho crítico**
- Colocar "quem está de plantão" no Redis trocaria uma comparação de timestamp transacional por uma cópia eventualmente consistente. A ausência de sinal (lease) e a exclusão de já-notificados precisam da **mesma** transação do ack.
- **Uso legítimo:** cache de GeoJSON/busca/bandeira (ADR-014/§7.6) e fan-out de frames entre instâncias. **Critério para posição em Redis:** > 50 batimentos/s sustentados (≈ 15.000 consoles abertos ao mesmo tempo) — cenário em que o lease passaria a ser *write-behind* com o banco como registro durável, e a validade ainda seria decidida por timestamp.

### E. NoSQL (Cassandra/ScyllaDB, MongoDB, DynamoDB) — **somente com gatilho medido**
Trocar o Postgres no caminho crítico significa abrir mão de três coisas que o SOS usa de propósito:

| Necessidade do SOS | PostgreSQL/PostGIS | Cassandra/ScyllaDB | MongoDB |
| --- | --- | --- | --- |
| Distância exata em metros (`geography`) | `ST_DWithin`/`ST_Distance` com GiST | **não tem** geoespacial nativo | `2dsphere`/`$geoWithin` (distância em geoJSON, sem `geography`/polígono temporal) |
| CAS + cancelamento atômicos ("um atendente") | `UPDATE … WHERE status=…` + `UPDATE` dos demais na mesma transação | LWT (`IF`) por partição, **sem transação multi-partição** | transação multi-documento desde 4.0 (com custo e limite de tamanho) |
| Idempotência por `client_request_id` | `UNIQUE` | tabela de idempotência manual (LWT) | índice único |
| Consulta "todos os plantões abertos dentro do raio" + lease | 1 SELECT | modelagem por partição geográfica feita à mão | `$geoNear` + filtro |
| Auditoria "por que ninguém foi?" | SQL, `EXPLAIN`, `pg_stat_statements` | varredura por partição | aggregation |

- **O que NoSQL resolveria bem:** ingestão de **eventos** em volume muito alto (trilha de ocorrências, telemetria de posição, analytics) e escrita multi-região ativa-ativa.
- **O que ele não resolveria:** a decisão espacial exata, o CAS da assunção e a prova transacional do despacho. Manter dois stores (Postgres para o SOS + NoSQL para o log) **aumenta** o número de modos de falha — só se justifica quando o log realmente crescer além do que a partição resolve.
- **Critérios objetivos para reabrir esta ADR com NoSQL:**
  1. `emergency_dispatches` + `emergency_calls` > **50 M linhas** com particionamento já aplicado **e** p95 de escrita > 1 s; **ou**
  2. necessidade de **escrita ativa-ativa em duas regiões** para o mesmo chamado (requisito de continuidade que o CBM exigir por escrito); **ou**
  3. ingestão de **telemetria contínua** (posição de todos os plantões a cada poucos segundos, > 5.000 escritas/s) virando requisito de produto — aí a telemetria vai para um store de séries/NoSQL **e o SOS continua no Postgres**.
- **Nunca:** trocar PostGIS por store sem geoespacial exato "para escalar" sem medição. Seria pagar em correção (a decisão que salva vida) por um ganho que o `EXPLAIN` nunca mostrou.

### F. Kafka / RabbitMQ como barramento do SOS — **entrega, não decisão**
- Já registrado na ADR-018 e mantido: o broker **entrega** o que o banco decidiu. Kafka só entra por requisito de **streaming/replay** (novo consumidor precisar reler o histórico de eventos, auditoria em escala, analytics em tempo real).
- **Critério:** > 5.000 eventos/s sustentados ou necessidade real de replay por um consumidor novo. Rabbit (perfil `rabbit`, ADR-004) continua sendo o candidato natural para fan-out entre instâncias e Web Push.

### G. Virtual threads — **já habilitadas, não são o gargalo**
- O Spring Boot roda com virtual threads; o `POST /emergency-calls` bloqueia em JDBC, o que já é absorvido. Elas não aumentam a vazão do banco: se o gargalo é conexão/CPU do Postgres, mais threads só enfileiram. Os jobs (outbox, escalonamento, plantão) são **sequenciais de propósito** — transação curta, sem I/O externo.

## Gatilhos de decisão (tabela única)

| Sinal medido | Limiar | Ação |
| --- | --- | --- |
| p95 do `POST /emergency-calls` | > 1 s | investigar `EXPLAIN (ANALYZE, BUFFERS)`; escalar verticalmente |
| p95 do `POST /emergency-calls` | > 3 s (**RNF01**) | incidente: escalar, desnormalizar `position_effective`, revisar pool |
| CPU do banco em pico | > 70% sustentado | escala vertical; depois índice funcional/desnormalização |
| Tempo do sweep da outbox (pending → published) | p95 > 2 s | investigar publicação, não o banco |
| Plantões abertos por instalação | > 2.000 | desnormalizar `position_effective` + GiST antes de tudo |
| Linhas em `emergency_dispatches` | > 10 M | particionar por mês + retenção |
| Backup/restore completo | > 30 min | particionar + arquivar frio |
| Batimentos de plantão | > 50/s | avaliar Redis como cache de posição (banco continua durável) |
| Eventos/s na outbox | > 5.000 | avaliar broker (Rabbit primeiro) para entrega |
| Escrita do SOS com particionamento | p95 > 1 s **e** > 50 M linhas | reabrir esta ADR para NoSQL (com as 3 condições da seção E) |

## Plano de medição (antes de qualquer troca)

1. **Habilitar `pg_stat_statements`** na instalação real e revisar semanalmente: top 20 por `total_exec_time`, com atenção ao `SELECT` de elegibilidade (`ST_DWithin`/`COALESCE`), ao `INSERT` da outbox e ao sweep de escalonamento.
2. **`EXPLAIN (ANALYZE, BUFFERS)`** das consultas da §7.5 e da §7.13, documentando o plano antes/depois de cada índice novo. Índice sem consulta medida não entra.
3. **Carga sintética com k6** (ainda não existe no repositório — item de roadmap): cenário "temporada" = 500 consoles com heartbeat a cada 5 min, 5 `POST /emergency-calls`/s por 10 min, 10% dos chamados sem ack para exercitar a escada inteira, e rajada de 50/s por 60 s. Medir p50/p95/p99 do POST, o lag do job de escalonamento (prazo vencido → anel aberto) e o tempo `outbox.created_at → published_at`.
4. **Sonda sintética** em produção (um chamado de teste com `clientRequestId` fixo, cancelado em seguida): mede o caminho inteiro de minuto em minuto sem depender de alguém pedir socorro.
5. **Registrar o resultado nesta ADR.** Nenhuma tecnologia nova entra sem o número que a justifique — e nenhuma fica de fora por preferência.

## Consequências

- **Positivas:** o desenho atual fica com envelope documentado e critérios de troca explícitos; a decisão por NoSQL passa a ter gatilho numérico (e nunca "porque é moderno"); fica registrado o que se perde ao sair do PostGIS (decisão espacial exata, CAS transacional, idempotência por constraint).
- **Negativas / dívidas assumidas:** sem retenção/particionamento hoje, as três tabelas do SOS crescem indefinidamente (item 4 acima); sem carga k6, os limiares são estimativas de projeto, não medições — e a primeira medição pode mover os gatilhos.
- **A decidir pelo usuário (dono do produto), sem bloquear o envelope aprovado:** (a) política de retenção do histórico do SOS (proposta: 24 meses quentes + arquivo, alinhado à ADR-008); (b) se a continuidade do CBM exige escrita ativa-ativa multi-região — só esse requisito, por escrito, reabre a discussão de NoSQL; (c) se telemetria contínua de plantão entra no escopo (hoje a posição é sob demanda, por decisão de privacidade da ADR-012).
- **Revisão:** ao final da primeira temporada com dados reais, ou antes, se um gatilho da tabela disparar.

## Referências

- `docs/Architecture.md` §7.5 (elegibilidade), §7.6 (índices/cache/idempotência), §7.11 (plantão/lease), §7.12 (geoindexação), §7.13 (despacho, escada `V10`)
- `docs/Requirements.md` RF07, RF21, RF42, RN03, RN19, RNF01
- ADR-004 (broker opcional), ADR-008 (retenção), ADR-012 (dados sensíveis), ADR-017 (geoindexação), ADR-018 (seleção e entrega)
