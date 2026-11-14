# ADR-013: Ocorrências operacionais como entidade própria

- Status: Aceito (2026-11-09); gatilho e encerramento emendados em 2026-11-13
- Contexto: o SOS é um alerta de segundos (criação, despacho, ack). O trabalho do guarda-vidas, porém, gera uma **ocorrência** que precisa ser registrada no CIOSP, e nem toda ocorrência passa pelo sistema: algumas chegam do 193, por rádio ou presencialmente. Misturar tudo em `emergency_calls` colocaria dados de pessoas e fluxo administrativo no caminho crítico do SOS e não serviria aos registros externos.
- Decisão: criar o módulo `occurrences` com a tabela `occurrences` (1:1 opcional com `emergency_calls`). Todo SOS gera uma ocorrência **por evento** (`EmergencyCallCreated`, idempotente por `emergency_call_id UNIQUE`), com job de reconciliação; o `emergency` não depende de `occurrences`. Ocorrências externas (`CBM_193`, `EXTERNAL_OTHER`) são registradas manualmente. Cada ocorrência tem `ciosp_status` (`PENDING`/`REGISTERED`/`NOT_REQUIRED`) e uma **ficha de transcrição** para o CIOSP. Dados de pessoas são opcionais, cifrados (ADR-012) e mascarados por padrão.
- Alternativas: (a) estender `emergency_calls` com origem e campos de ocorrência — rejeitada: acopla o caminho crítico e mistura ciclos de vida; (b) criar a ocorrência na mesma transação do SOS — rejeitada: qualquer falha nela atrasaria ou derrubaria o alerta; (c) integrar direto ao CIOSP — fora do escopo (ADR-007), depende de contrato do órgão.
- Consequências: o SOS permanece isolado e rápido; há duas entidades para manter consistentes (mitigado por evento idempotente + reconciliação + alerta); a ficha do CIOSP depende de configuração por instalação (campos e ordem do formulário do CBM); dados de vítima exigem controle de acesso, auditoria de revelação e política de retenção (D-08).

## Emenda (2026-11-13)

O gatilho da ocorrência de SOS mudou de `EmergencyCallCreated` para **`SosAcknowledged`**. Um chamado que ninguém assumiu não é atendimento: não tem responsável, não tem posto e não deveria ocupar a fila do CIOSP. A ficha nasce `IN_PROGRESS` no aceite, com praia/setor, a coordenada do chamado e `ciosp_status = PENDING`; um SOS que termina sem ack não gera ocorrência.

O restante da decisão vale igualmente: criação por evento, fora do caminho crítico (`AFTER_COMMIT` + `REQUIRES_NEW`), idempotência por `emergency_call_id UNIQUE`, job de reconciliação para chamado **assumido** sem ficha e `emergency` sem depender de `occurrences`. O evento `SosAcknowledged` vive em `emergency/api` (`@NamedInterface("api")`), que é o contrato público que o módulo `occurrences` consome.

Ver `docs/Architecture.md` §8.7 e a migração `V17__occurrences.sql`.

## Emenda (2026-11-13) — encerramento, visibilidade e revelação

A ficha nascia e era lida, mas não acabava: o atendimento ficava `IN_PROGRESS` para sempre e o chamado continuava em "Em atendimento por você" no console do SOS. Faltava a transição de saída, e ela é do **atendimento**, não do alerta.

- **Encerramento é uma operação da ocorrência** (`POST /api/v1/occurrences/{id}/close`): desfecho obrigatório (`RESOLVED`, `REFERRED`, `FALSE_ALARM`, `CANCELLED`), relato obrigatório, observações do desfecho, protocolos (canal e CIOSP) e dados de vítima opcionais. `RESOLVED`/`REFERRED` levam a `CLOSED`; `FALSE_ALARM`/`CANCELLED` levam ao status homônimo. Encerrar de novo responde 409.
- **Encerrar a ocorrência encerra o chamado do SOS na mesma transação.** A transição de estado do chamado fica atrás da porta `emergency/api/SosCallCloser` (implementada em `emergency`): `occurrences` já depende de `emergency/api` e `emergency` continua sem depender de `occurrences`. O `UPDATE` é compare-and-set por estado — chamado já resolvido não é reescrito. `emergency_calls` ganhou `resolved_at`/`resolved_by`.
- **A visibilidade do guarda-vidas passa a ser o que ele atendeu** (`responder_id`), não o posto/praia (emenda da RN15). O responsável é quem deu o ack do chamado, gravado na abertura e retro-preenchido pelo despacho `ACKNOWLEDGED`. O escopo antigo fica como fallback para fichas criadas antes da `V20`. Coordenação e TI continuam vendo tudo.
- **Completar depois é edição, não reabertura** (`PATCH /api/v1/occurrences/{id}`): a coordenação (ou quem atendeu) informa o protocolo do CIOSP, a vítima que só foi identificada depois ou corrige o relato. Campo nulo não altera o valor atual; `REGISTERED` sem protocolo responde 409 `ciosp-protocol-required`. Cada alteração grava `EDITED`/`CIOSP_REGISTERED` em `occurrence_events`.
- **Dados de vítima nunca saem em claro na ficha.** Nome, idade, contato e condição são cifrados com AAD próprio (`maraberto:occurrence-victim:v1`); o detalhe devolve só `recorded`. Ver o conteúdo exige `POST /{id}/victim/reveal` com motivo obrigatório, que grava `PERSON_REVEALED` — e o histórico nunca recebe o dado em claro.

Alternativas rejeitadas: (a) encerrar pelo console do SOS e a ocorrência apenas espelhar — o desfecho e o relato são do atendimento, e a ocorrência precisa existir sem SOS; (b) manter o escopo por posto/praia — dois guardas do mesmo posto passariam a poder encerrar o atendimento um do outro; (c) reabrir a ocorrência para completar o CIOSP — sujaria o histórico e a contagem de "em atendimento".

Ver `docs/Architecture.md` §7.9/§8.7, `docs/Requirements.md` (RF28/RF32/RF34/RF35, RN11/RN15) e a migração `V20__occurrence_closure_and_victim.sql`.
