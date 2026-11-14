# ADR-013: Ocorrências operacionais como entidade própria

- Status: Aceito (2026-11-09); gatilho emendado em 2026-11-13
- Contexto: o SOS é um alerta de segundos (criação, despacho, ack). O trabalho do guarda-vidas, porém, gera uma **ocorrência** que precisa ser registrada no CIOSP, e nem toda ocorrência passa pelo sistema: algumas chegam do 193, por rádio ou presencialmente. Misturar tudo em `emergency_calls` colocaria dados de pessoas e fluxo administrativo no caminho crítico do SOS e não serviria aos registros externos.
- Decisão: criar o módulo `occurrences` com a tabela `occurrences` (1:1 opcional com `emergency_calls`). Todo SOS gera uma ocorrência **por evento** (`EmergencyCallCreated`, idempotente por `emergency_call_id UNIQUE`), com job de reconciliação; o `emergency` não depende de `occurrences`. Ocorrências externas (`CBM_193`, `EXTERNAL_OTHER`) são registradas manualmente. Cada ocorrência tem `ciosp_status` (`PENDING`/`REGISTERED`/`NOT_REQUIRED`) e uma **ficha de transcrição** para o CIOSP. Dados de pessoas são opcionais, cifrados (ADR-012) e mascarados por padrão.
- Alternativas: (a) estender `emergency_calls` com origem e campos de ocorrência — rejeitada: acopla o caminho crítico e mistura ciclos de vida; (b) criar a ocorrência na mesma transação do SOS — rejeitada: qualquer falha nela atrasaria ou derrubaria o alerta; (c) integrar direto ao CIOSP — fora do escopo (ADR-007), depende de contrato do órgão.
- Consequências: o SOS permanece isolado e rápido; há duas entidades para manter consistentes (mitigado por evento idempotente + reconciliação + alerta); a ficha do CIOSP depende de configuração por instalação (campos e ordem do formulário do CBM); dados de vítima exigem controle de acesso, auditoria de revelação e política de retenção (D-08).

## Emenda (2026-11-13)

O gatilho da ocorrência de SOS mudou de `EmergencyCallCreated` para **`SosAcknowledged`**. Um chamado que ninguém assumiu não é atendimento: não tem responsável, não tem posto e não deveria ocupar a fila do CIOSP. A ficha nasce `IN_PROGRESS` no aceite, com praia/setor, a coordenada do chamado e `ciosp_status = PENDING`; um SOS que termina sem ack não gera ocorrência.

O restante da decisão vale igualmente: criação por evento, fora do caminho crítico (`AFTER_COMMIT` + `REQUIRES_NEW`), idempotência por `emergency_call_id UNIQUE`, job de reconciliação para chamado **assumido** sem ficha e `emergency` sem depender de `occurrences`. O evento `SosAcknowledged` vive em `emergency/api` (`@NamedInterface("api")`), que é o contrato público que o módulo `occurrences` consome.

Ver `docs/Architecture.md` §8.7 e a migração `V17__occurrences.sql`.
