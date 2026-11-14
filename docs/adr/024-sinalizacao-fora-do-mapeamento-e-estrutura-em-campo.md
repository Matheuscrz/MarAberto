# ADR-024: Sinalização fora do mapeamento em triagem e ciclo de vida da estrutura em campo

- Status: **Aceito** (2026-11-13) — implementado na branch de sinalização/postos com `V18` e `V19`, endpoints de triagem e de campo, testes de integração e telas do console.
- Relacionadas: ADR-012 (dados sensíveis e sanitização), ADR-013 (ocorrências), ADR-015 (frontend feature-based), ADR-016 (importação de mapeamento), ADR-021 (área de banho), ADR-022 (ciclo de vida da sinalização e das estruturas), ADR-023 (sinalização/estruturas ancoradas no aparelho e mídia).
- Requisitos: RF11, RF12, RF38, RF39, RF41; RN01, RN17; RNF04.
- Emenda: substitui a recusa `422 outside-mapped-area` da ADR-023 e amplia a ativação/desativação da ADR-022 para o guarda-vidas em campo.

## Contexto

A ADR-023 entregou as duas formas de dizer onde está a sinalização (`beachId`/`sectorId` no console ou `latitude`/`longitude` do aparelho) e o cadastro/reposição de posto e base pela posição atual. Três lacunas apareceram no uso real:

1. **Ponto fora do mapeamento era recusado com `422`.** O KML do CBM é reexportado de tempos em tempos; enquanto isso, o guarda-vidas pode estar numa praia nova, num trecho de rio não cadastrado ou numa barragem que passou a receber banhistas. Recusar apaga a informação de campo e desestimula o operador a sinalizar. Ao mesmo tempo, uma bandeira sem praia não tem pin nem ficha, então não pode ir para o mapa público.
2. **Desativar estrutura dependia da TI.** O guarda-vidas que precisa deixar o posto, ou que encontra a estrutura interditada, não podia tirar o pin do mapa na hora. Esperar a TI mantém no mapa uma estrutura sem ninguém — pior para o banhista que confia no pin.
3. **A população não via o posto.** O cadastro em campo guardava nome e ponto, mas não a foto da estrutura. Quem procura o posto no mapa quer reconhecer o lugar, não só a coordenada.

## Decisão

### 1. Fora do mapeamento não é erro: é fila de triagem

O lançamento por localização que não cai em nenhuma área ativa é **aceito** (`201`) e devolvido com `outcome=UNMAPPED`. A resposta não traz bandeira publicada; traz o registro da fila (`unmapped`). `PLACED` continua sendo o desfecho da bandeira que foi para o mapa.

- `unmapped_signals` (`V18`) guarda **um ponto por lançamento**: cor pretendida, observação, `location`/`location_accuracy_m`, quem reportou (matrícula resolvida na leitura, nome do operador não é exposto), posto do plantão e as até 3 fotos (`unmapped_signal_media`), na ordem de envio.
- **Nada é publicado no mapa.** `flag_updates` não recebe linha e `GET /api/v1/flags` não toca na tabela. A fila só é lida em `/api/v1/management/**` (`COORDINATOR`; `ADMIN` como superusuário técnico) e a coordenada é dado sensível (RN17).
- **A linha nunca é apagada.** A decisão é estado + autor + data: `IDENTIFIED` (associa área existente e, opcionalmente, setor dela), `NEEDS_MAPPING` (pede novo KML), `DISCARDED` (encerra sem ação) e `PENDING` (reabre). `IDENTIFIED` exige `beachId` ativo; setor, quando informado, precisa pertencer à área. As demais decisões não aceitam área — evita vínculo meio feito.
- **A área mais próxima é sugestão, nunca vínculo.** `ST_Distance` em tempo de consulta devolve a área ativa mais próxima e a distância, para a coordenação reconhecer o local; associar automaticamente seria decidir por proximidade um dado que só o humano sabe.
- A exceção `LocationOutsideMappedAreaException` e o problema `outside-mapped-area` foram **removidos**; o frontend trata os dois desfechos.

### 2. O guarda-vidas alterna a estrutura do próprio plantão

`PATCH /api/v1/operations/stations/{kind}/{id}` aceita `{active, statusNote}`:

- `LIFEGUARD` só alterna a estrutura do **plantão aberto dele** (`post_id`/`mobile_base_id` em `shift_sessions` com `ended_at IS NULL`); qualquer outra devolve `403` `station-outside-shift` em RFC 9457.
- `COORDINATOR`/`ADMIN` alternam qualquer estrutura. A TI continua com `/api/v1/admin/stations` para o cadastro administrativo.
- Desativar carimba `deactivated_at` e a nota; reativar limpa a data e **preserva** a nota (histórico do painel). **Nada é apagado**: plantões e ocorrências continuam apontando para a linha; o que muda é a visibilidade no mapa e na lista de destinos do plantão.
- `GET /api/v1/operations/stations/{kind}/{id}` devolve a estrutura mesmo desativada, para o console do operador mostrar o estado e oferecer "Reativar".
- `GET /api/v1/operations/stations` lista **todas** as estruturas no console de campo, inclusive a base móvel sem plantão vivo e as desativadas, com posição, origem, precisão, data e fotos. É leitura: não amplia a permissão de escrita. No seletor de plantão (`GET /api/v1/dispatch/shifts/targets`), cada destino traz `positionUpdatedAt`/`positionAccuracyM` e o console mostra a distância da leitura atual do aparelho — a base móvel deixa de ser só um nome na lista.
- A base móvel segue a regra da ADR-022: só aparece no mapa com plantão vivo e lease válido. Desativar é decisão explícita; encerrar o plantão já a retira. Encerrar o turno **não** apaga o cadastro: a base continua no console de campo, com a posição registrada, até ser desativada pela coordenação/TI.

### 3. Foto do posto e base, com a mesma normalização da sinalização

- `lifeguard_post_media` e `mobile_base_media` (`V19`), até 3 fotos por estrutura, reutilizando `media_assets` e o `ImageNormalizer` (JPEG, maior lado 1600 px, EXIF/GPS descartados). Duas tabelas de ligação porque `lifeguard_posts` e `mobile_bases` são cadastros distintos e a integridade referencial fica no banco.
- `CreateStationRequest.mediaIds` recebe as fotos já enviadas por `POST /api/v1/media`; o `StationService` confere existência e dono antes de anexar. `PublicStation.photoUrls` e `StationAdminView.photoUrls` expõem os caminhos; a leitura do binário continua pública e imutável (`GET /api/v1/media/{id}`, `ETag`).
- A foto é da **estrutura**, não do operador. O ponto público é o da estrutura/último plantão; a imagem não carrega coordenada nem autoria.

### 4. Galeria única no frontend

`shared/components/PhotoGallery.tsx` é o visualizador de fotos de sinalização, posto e base: a miniatura abre uma tela cheia com a **imagem inteira** (`object-contain`), anterior/próxima, contador, miniaturas, setas do teclado e `Escape`. `SignalPhotos` é um wrapper com o rótulo da sinalização. O componente usa `useOverlay` (foco preso, `Escape`, retorno de foco) e `<dialog open>`, como o resto do app.

## Alternativas

- **Manter `422` e pedir para o operador registrar depois:** rejeitada. Perde a informação no momento em que ela existe e o operador não tem onde anotar.
- **Publicar a bandeira fora do mapeamento num pin "solto":** rejeitada. Sem praia/setor não há ficha, escopo de ocorrência nem herança de bandeira; o pin solto viraria dado público sem contexto.
- **Associar automaticamente à área mais próxima:** rejeitada. Proximidade não identifica o local (um ponto de rio pode estar a 300 m de uma praia oceânica) e a decisão precisa de responsável.
- **Deixar a desativação só na TI:** rejeitada. O pin de uma estrutura sem ninguém engana o banhista; o guarda-vidas é quem sabe que está saindo.
- **Permitir que o guarda-vidas altere qualquer estrutura:** rejeitada. O escopo do plantão é o que impede um operador de tirar do mapa o posto de outro.
- **Galeria diferente por feature:** rejeitada. Três cópias do mesmo diálogo divergem em acessibilidade e teclado; uma galeria compartilhada resolve.

## Consequências

- **Positivas:** nenhum lançamento de campo é perdido; a coordenação enxerga onde o mapeamento está desatualizado e decide com distância à área mais próxima; o mapa não mostra estrutura sem gente; a população reconhece o posto pela foto; a galeria é uma só e acessível.
- **Negativas / dívidas:** a fila de áreas não identificadas é uma tela a mais para a coordenação (não há notificação de novo registro); a foto do posto não pode ser trocada depois do cadastro nesta fatia (só um novo cadastro/reposição anexa mídia); foto enviada e não anexada continua órfã até a rotina de retenção; a galeria não tem zoom por gesto.
- **Privacidade:** a coordenada da fila e a do operador são sensíveis (RN17) e só aparecem na rota autenticada da coordenação. A coordenada da estrutura e da sinalização é operacional e pública; a foto não revela o aparelho nem o autor.
- **Revisão:** reabrir quando houver notificação de fila, edição de fotos da estrutura ou importação de novas áreas direto da triagem.

## Referências

- `docs/Architecture.md` §7.9, §7.11, §8.2, §8.6
- `docs/Requirements.md` RF11, RF12, RF38, RF39, RF41; RN01, RN17
- `backend/src/main/resources/db/migration/V18__unmapped_signals.sql`
- `backend/src/main/resources/db/migration/V19__station_media.sql`
- `backend/src/main/java/com/maraberto/operations/application/UnmappedSignalService.java`
- `backend/src/main/java/com/maraberto/operations/application/StationService.java`
- `web/src/shared/components/PhotoGallery.tsx`
