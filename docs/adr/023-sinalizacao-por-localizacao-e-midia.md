# ADR-023: Sinalização e estruturas ancoradas na posição do aparelho, com foto servida pelo backend

- Status: **Aceito** (2026-11-13) — implementado na branch de sinalização/postos com `V16`, módulo `media`, endpoints de campo, testes de integração e telas do console.
- Relacionadas: ADR-012 (dados sensíveis e sanitização), ADR-015 (frontend feature-based), ADR-016 (importação de mapeamento), ADR-019 (app com Capacitor), ADR-021 (área de banho), ADR-022 (ciclo de vida da sinalização e das estruturas).
- Requisitos: RF11, RF12, RF38, RF39; RN01, RN17; RNF04, RNF06.
- Emenda: [ADR-024](024-sinalizacao-fora-do-mapeamento-e-estrutura-em-campo.md) substitui a recusa fora do mapeamento pela fila de triagem e amplia a ativação/desativação e a foto para as estruturas.

## Contexto

A ADR-022 entregou o ciclo de vida da sinalização: validade, encerramento, pin por setor/área, estrutura ativa e base móvel dependente do plantão. Ficaram de fora três coisas que a operação de campo pede:

1. **Sinalizar de onde se está.** O console só sabia lançar por praia/setor escolhidos numa lista. Na areia, o guarda-vidas quer plantar a bandeira no ponto em que está, sem procurar o setor num `select`.
2. **Foto da sinalização.** RF12 pede "com foto da sinalização" e RF38 pede que a ficha do pin mostre a imagem do momento. A ADR-022 registrou a ausência de `media_id` como dívida; o módulo `media` só tinha a porta de storage.
3. **Cadastrar/reposicionar posto e base pela posição atual.** RF11/RF39 pedem que o próprio guarda-vidas em campo crie ou corrija o ponto da estrutura, com a acurácia do aparelho, sem esperar a TI.

Havia também uma restrição técnica do app empacotado: o HTTP nativo (`CapacitorHttp`, ADR-019) manda corpo como string reenviável e o backend não abre CORS. Multipart não passa por esse caminho e uma requisição com `FormData` não pode ser repetida depois de um `401`/refresh.

## Decisão

1. **Duas formas de lançar a bandeira, no mesmo endpoint.** `POST /api/v1/operations/flags` aceita `beachId` (+ `sectorId` opcional) **ou** `latitude`/`longitude` (+ `accuracyMeters`). As duas convivem porque resolvem casos diferentes: a sinalização geral da área (console) e a bandeira plantada no ponto (campo). Mandar só uma coordenada ou nenhuma forma é `400`.
2. **O servidor resolve o escopo pelo ponto.** `ST_Contains` em `beach_sectors` com `ORDER BY display_order, id LIMIT 1` (a mesma regra da amostra de ocupação em sobreposição) e, sem setor, em `beaches.boundary`. Ponto fora de área mapeada **não é recusado** desde a ADR-024: o lançamento vira registro em `unmapped_signals` (`outcome=UNMAPPED`, `201`) para a coordenação triar, sem publicar bandeira no mapa. A recusa `422 outside-mapped-area` descrita na versão original foi removida.
3. **O ponto é da sinalização, não do operador.** `flag_updates` ganha `signal_position`, `position_accuracy_m` e `position_source` (`SCOPE`/`DEVICE_GPS`). Guarda-se **um** ponto por lançamento — não há histórico de deslocamento, e a posição do guarda-vidas continua restrita a `shift_sessions.last_position` (RN17). O pin público é `COALESCE(signal_position, centroide do setor / ST_PointOnSurface(área))`.
4. **Estruturas usam o mesmo vocabulário de posição.** `lifeguard_posts`/`mobile_bases` ganham `position_source`, `position_accuracy_m` e `position_updated_at`. `POST /api/v1/operations/stations` cria a estrutura na posição atual e `PATCH /api/v1/operations/stations/{kind}/{id}/position` repõe o ponto; ambos para `LIFEGUARD`/`COORDINATOR`/`ADMIN`. A TI mantém só ativação/desativação (`/api/v1/admin/stations`); o painel mostra origem, precisão e data para julgar a confiança do ponto.
5. **A foto passa pelo backend e é re-codificada.** `POST /api/v1/media` recebe multipart (navegador/PWA) ou `{contentBase64}` (app empacotado). O `ImageNormalizer` abre com `ImageIO` (teto de 40 MP), re-codifica em JPEG com maior lado de 1600 px e qualidade 0,85 e **descarta EXIF/GPS**. O binário original nunca é guardado nem servido.
6. **A foto é do lançamento, não do operador.** `media_assets` + `flag_media` (até 3, com `display_order`). O `MediaCatalog` confere existência e dono antes de anexar: ninguém anexa a foto de outro operador. A leitura é pública (`GET /api/v1/media/{id}`) porque a foto aparece no mapa e na ficha, com `ETag` do SHA-256 e `Cache-Control: immutable` — o conteúdo de um `id` nunca muda.
7. **Base64 em vez de URL pré-assinada nesta fatia.** A normalização acontece antes de o binário existir em local público — com URL pré-assinada, o arquivo ficaria acessível sem saneamento até o _callback_. O caminho síncrono é mais simples e o `ObjectStoragePort` mantém a evolução possível (quarentena + saneamento assíncrono) sem mudar a API pública.

## Alternativas

- **Só localização, sem escopo no console:** rejeitada. A coordenação precisa sinalizar uma área inteira ou um setor específico de um lugar que não é o da bandeira (ex.: sala de operação), e o GPS nem sempre está disponível.
- **Só escopo, inferindo praia/setor do texto da foto:** rejeitada. Não é confiável, exige visão computacional e não resolve o caso "quero a bandeira aqui".
- **Guardar a posição do aparelho como histórico de trajeto:** rejeitada. Vira rastreamento contínuo do operador e viola RN17.
- **Usar EXIF/GPS da foto como posição:** rejeitada. Metadado é fácil de falsificar, vem de outro aparelho e seria dado pessoal sem necessidade.
- **Upload direto com URL pré-assinada + quarentena:** adiada. Exige estado `PENDING`, _callback_ de saneamento e varredura de órfãos; o ganho de banda não compensa agora.
- **Multipart também no app nativo:** rejeitada. `CapacitorHttp` só envia `data` string e o `FormData` não é reenviável depois de um refresh de sessão.
- **Mídia em tabela `media_id` única na bandeira:** rejeitada. Uma foto só não cobre "mar, bandeira e placa"; `flag_media` ordenado resolve sem repetir a linha.

## Consequências

- **Positivas:** o guarda-vidas sinaliza em pé, no ponto exato, e a ficha mostra a foto do momento; a coordenação consegue auditar o posicionamento das estruturas pela origem/precisão; o binário servido é sempre produzido pelo servidor (sem EXIF/GPS); a bandeira não aumenta a superfície de exposição da posição do operador.
- **Negativas / dívidas:** upload base64 cresce ~33% no app nativo; o limite de multipart subiu para 12 MB (arquivo) / 14 MB (requisição). Foto enviada e não anexada fica órfã (inofensiva, sem URL em lançamento) até uma rotina de retenção. Ajuste fino do pino no mapa e desenho da bandeira na foto continuam para uma fatia de UX. Encerramento com desfecho e protocolo CIOSP seguem na fatia de ocorrências.
- **Privacidade:** a coordenada da sinalização é pública (é o pin da bandeira, dado operacional); a coordenada do operador não. A foto não carrega EXIF/GPS e não revela quem enviou: a autoria fica no banco, não na imagem.
- **Revisão:** reabrir se a foto passar a ser obrigatória, se o volume exigir upload direto com saneamento assíncrono ou se o CBM pedir mais de 3 fotos por lançamento.

## Referências

- `docs/Architecture.md` §7.3 (DDL), §7.11 (operação de campo), §8.2 (bandeiramento), §8.6 (upload de foto)
- `docs/Requirements.md` RF11, RF12, RF38, RF39; RN01, RN17
- `backend/src/main/resources/db/migration/V16__signal_position_and_media.sql`
- `backend/src/main/java/com/maraberto/media/` e `com/maraberto/operations/application/FlagService.java`
