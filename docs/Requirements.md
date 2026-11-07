# MarAberto — Requisitos

> **Versão:** 0.1 (rascunho) · **Data:** 2026-11-06 · **Status:** para revisão
> **Documento relacionado:** [Architecture.md](./Architecture.md)

---

## 1. Visão

**MarAberto** é uma plataforma **open source** de prevenção e resposta a afogamentos em praias, pensada para ser **implantada por qualquer estado** (corpos de bombeiros, defesa civil, prefeituras) sem depender de serviços pagos obrigatórios.

Ela entrega três valores:

1. **Prevenção primária** para o banhista: onde é perigoso, qual a bandeira do dia, como está a maré, o que fazer.
2. **Resposta rápida**: pedido de socorro georreferenciado que chega ao guarda-vidas mais próximo em segundos.
3. **Inteligência operacional**: histórico e mapa de calor para o comando planejar contingente.

### Princípios do produto

| # | Princípio | Consequência prática |
|---|-----------|----------------------|
| P1 | **Vida primeiro** | O fluxo de SOS tem prioridade sobre qualquer outra funcionalidade (em desempenho, testes e alertas). |
| P2 | **Funciona na praia** | Rede 3G/4G instável, sol forte, uma mão ocupada: leve, offline parcial, botões grandes. |
| P3 | **Não substitui o 193** | O app complementa, nunca substitui, o acionamento oficial. O "Ligar 193" está sempre visível. |
| P4 | **Privacidade por padrão** | Coleta mínima, sem trilhas de localização, LGPD desde o desenho. |
| P5 | **Barato de operar** | Um `docker compose up` sobe tudo; roda em uma única VM. Estados pequenos precisam conseguir operar. |
| P6 | **Adaptável por região** | Praias, marés, números de emergência e textos são **configuração**, não código. |

---

## 2. Stakeholders e personas

| Persona | Descrição | Principais necessidades |
|---------|-----------|-------------------------|
| **Banhista** | Público geral, anônimo ou cadastrado, usa celular na praia. | Saber se é seguro, onde evitar, pedir socorro. |
| **Guarda-vidas** | Bombeiro militar ou guarda-vidas civil em posto fixo ou móvel. | Lançar bandeira, receber SOS, demarcar correntes, moderar reportes. |
| **Coordenador / Comando** | Chefia operacional do CBM ou da coordenação. | Gerir equipes, ver histórico, administrar conteúdo e praias. |
| **Administrador da instalação** | TI do órgão que hospeda a instância. | Instalar, atualizar, fazer backup, configurar a região. |
| **Contribuidor open source** | Dev de outro estado ou voluntário. | Entender, rodar localmente, contribuir. |

---

## 3. Escopo

### Dentro do escopo
- PWA do banhista (mapa, marés, fichas, vídeos, SOS, reportes, ocupação).
- PWA operacional (console do guarda-vidas e painel de comando).
- Backend monólito modular, banco geoespacial, armazenamento de mídia, tempo real e push.
- Configuração regional ("Region Pack") para qualquer UF.
- Observabilidade, auditoria e backup.

### Fora do escopo (por ora)
- Aplicativo nativo iOS/Android (a PWA cobre o MVP).
- Integração direta com sistemas de despacho do 193/CIOPS (previsto como **ponto de extensão**, ver D-07).
- Previsão própria de ondas/correntes por modelo oceanográfico.
- Pagamentos, publicidade e qualquer monetização.
- Substituição do acionamento telefônico oficial.

---

## 4. Requisitos funcionais

**Legenda de release:** **R1** = MVP (segurança de vida) · **R2** = colaboração e conteúdo · **R3** = inteligência e abertura de dados.

### 4.1 Módulo do Banhista

| ID | Requisito | Rel. | Critérios de aceite (resumo) |
|----|-----------|:----:|------------------------------|
| RF01 | **Mapa interativo multicamadas**: centralizar no usuário e alternar camadas (postos fixos/móveis, bandeira do dia, zonas de risco, alertas). | R1 | Abre centrado no usuário ou na praia padrão se o GPS for negado. Camadas ligam/desligam sem recarregar. Cada camada só é buscada quando ativada. |
| RF02 | **Áreas críticas permanentes**: polígonos destacados (foz de rio, pedrais, rebojos, fossas). | R1 | Polígono com nível (MODERADO/ALTO/EXTREMO). Toque abre ficha com descrição, orientações e vídeo vinculado. |
| RF03 | **Painel de tábua de marés**: preamar/baixa-mar do dia, altura, tendência (subindo/descendo), destaque de sizígia. | R1 | Exibe 4 eventos do dia e a tendência atual. Funciona com dado em cache se o provedor externo estiver fora. |
| RF04 | **Ficha da praia**: tipo, perigos predominantes, infraestrutura (postos, acessibilidade, chuveiros). | R1 | Disponível offline após a primeira visita (RNF05). |
| RF05 | **Central educativa**: vídeos do YouTube sobre corrente de retorno, foz de rio, bandeiras, primeiros socorros. | R1 | Thumbnail estática; o iframe só carrega após o toque (RNF02). Vídeo contextual aparece no card de zona de risco. |
| RF06 | **Cadastro e autenticação** com e-mail e confirmação de contato. | R1 | Autenticação **própria do sistema** (sem IdP externo) para funcionalidades que exigem conta. CPF não é solicitado aos banhistas e é reservado aos perfis operacionais; quando usado, é validado pelos dígitos verificadores. E-mail confirmado por token de uso único; senha com Argon2id. Ver **D-01** e **D-02**. |
| RF07 | **SOS / botão de pânico** georreferenciado, com transmissão ao posto e rondas mais próximos. | R1 | Ver RF21 (ciclo de vida) e RNF01 (latência). Idempotente contra duplo toque. |
| RF08 | **Reporte colaborativo** de incidente (animal marinho, óleo, corrente forte etc.) com foto opcional. | R2 | Limite de 5 reportes/hora por usuário. Foto passa por saneamento (RNF09). |
| RF09 | **Validação de alertas comunitários**: moderação manual **ou** quórum automático (≥ 3 usuários distintos, mesmo tipo, ≤ 100 m, ≤ 30 min). | R2 | Parâmetros configuráveis (RN02). Alerta por quórum exibe selo "comunitário — aguardando confirmação oficial". |
| RF10 | **Indicador de ocupação** anônimo e aproximado. | R2 | Detecção no cliente; servidor só recebe incremento anônimo (RNF04). Exibe "dados insuficientes" abaixo da amostra mínima (RN06). |

### 4.2 Módulo Operacional (Guarda-vidas)

| ID | Requisito | Rel. | Critérios de aceite (resumo) |
|----|-----------|:----:|------------------------------|
| RF11 | **Gestão de postos e bases móveis**: início/fim de plantão, localização da base móvel, fotos da estrutura e do mar. | R1 | Plantão ativo torna o posto elegível a receber SOS. Fim de plantão remove da elegibilidade. |
| RF12 | **Lançamento de bandeiramento**: Verde, Amarela, Vermelha, Roxa e Preta, com foto da sinalização. | R1 | Bandeira tem validade (RN01). Mapa do banhista reflete a mudança em até 60 s. |
| RF13 | **Demarcação dinâmica de correntes de retorno**: desenhar polígonos temporários no mapa. | R2 | Desenho com poucos toques; expira automaticamente (validade configurável). |
| RF14 | **Moderação e triagem de reportes**: aprovar, rejeitar, encerrar. | R2 | Toda decisão grava auditoria (RNF06) e notifica o autor do reporte. |
| RF15 | **Receptor de SOS com telemetria**: alerta visual **e sonoro**, coordenadas exatas, distância ao posto, botão "Assumir". | R1 | Alerta toca até ser reconhecido. Console funciona em tela bloqueada via push (RF23). |

### 4.3 Módulo de Gestão e Comando

| ID | Requisito | Rel. | Critérios de aceite (resumo) |
|----|-----------|:----:|------------------------------|
| RF16 | **Painel tático e histórico**: mapa de calor de afogamentos, resgates e ações preventivas. | R3 | Filtros por período, praia, tipo. Dados agregados em grade, nunca coordenadas individuais de cidadãos. |
| RF17 | **Administração de conteúdo educativo** (CMS simples de vídeos, títulos, tags, ordem). | R2 | Valida o ID do YouTube; publica/despublica; invalida cache. |
| RF18 | **Gestão de praias e zonas críticas**: polígonos, relevo, riscos permanentes e semipermanentes, referência a pontos da Marinha/DHN. | R1 | Persistência explícita (`PERMANENT`/`SEMI_PERMANENT`), validade e revalidação de zonas topográficas; edição de polígono com `ST_IsValid`. Correntes efêmeras usam a demarcação dinâmica (RF13). Mudança invalida cache do GeoJSON. |

### 4.4 Novos requisitos (adicionados nesta revisão)

| ID | Requisito | Rel. | Justificativa |
|----|-----------|:----:|---------------|
| RF19 | **Configuração regional**: UF(s), fuso horário, estação de maré, centro do mapa, números de emergência, textos legais, identidade visual. | R1 | Base para ser adotável por qualquer estado (P6). |
| RF20 | **Gestão de usuários operacionais**: convite pelo coordenador, papéis, ativação/desativação, MFA. | R1 | Operacionais **não** se autocadastram; evita acesso indevido ao console. |
| RF21 | **Ciclo de vida do SOS**: `DISPATCHED → ACKNOWLEDGED → EN_ROUTE → ON_SCENE → RESOLVED` (+ `CANCELLED`, `FALSE_ALARM`). Reconhecimento obrigatório e **escalonamento automático** se ninguém reconhecer em N segundos (padrão 20 s). | R1 | Sem isso, um SOS pode ficar sem dono sem ninguém perceber. |
| RF22 | **Fallback de emergência**: botão "Ligar 193" (configurável) sempre presente; se o envio falhar ou a rede cair, a tela orienta ligar. | R1 | P3: o app não pode criar falsa sensação de socorro enviado. |
| RF23 | **Notificações push** (Web Push) para operacionais, com polling de reconciliação. | R1 | Console em segundo plano ou tela bloqueada precisa alertar. |
| RF24 | **Login via gov.br** (cliente OIDC embutido no backend), opcional. | R2 | Valida identidade de fato; CPF digitado só valida dígitos (ver D-02). |
| RF25 | **Region Pack**: importação de GeoJSON/YAML com praias, zonas, postos e parâmetros de uma região. | R1 | Reduz o custo de adoção por outros estados. |
| RF26 | **Direitos do titular (LGPD)**: exportar e excluir dados da conta. | R2 | Obrigação legal. |
| RF27 | **Termos e aviso legal versionados**, com registro de aceite. | R1 | Deixa claro que o app não substitui o 193. |
| RF28 | **Registro de desfecho da ocorrência**: tipo, vítima atendida, encaminhamento. | R2 | Alimenta o histórico (RF16). |
| RF29 | **Dados abertos agregados**: exportação CSV/GeoJSON anonimizada e agregada. | R3 | Alinha-se ao propósito público e à transparência. |
| RF30 | **Condições meteorológicas**: clima atual e previsão resumida para a praia. | R1 | Consulta o provider no backend, usa cache local e informa quando o dado estiver desatualizado ou indisponível. |
| RF31 | **Cadastro operacional de marés**: criação e correção de eventos de preamar e baixa-mar enquanto não houver provider automático validado. | R1 | Exige perfil autorizado, registra auditoria, fonte, horário de atualização e validade; a interface identifica o dado como manual. |

---

## 5. Regras de negócio

| ID | Regra |
|----|-------|
| RN01 | A **bandeira vigente** é o último lançamento válido do posto/praia. Passada a validade (padrão 12 h, configurável), a UI exibe **"desatualizada"**, nunca a cor como se fosse atual. |
| RN02 | **Quórum comunitário:** ≥ 3 usuários distintos, mesmo tipo, raio ≤ 100 m, janela ≤ 30 min → alerta passa a `ACTIVE` automaticamente com selo de origem comunitária. Todos os parâmetros são configuráveis. |
| RN03 | **Roteamento de SOS:** notifica os postos com plantão ativo dentro do raio de cobertura (ordenados por distância). Se não houver nenhum, notifica a coordenação central e orienta o 193. |
| RN04 | **Precisão de localização:** se a acurácia do GPS for > 100 m, o console exibe o raio de incerteza em vez de um ponto exato. |
| RN05 | **Abuso:** SOS limitado a 3 por 10 min por usuário/dispositivo; reportes a 5/hora; contas com reportes falsos reincidentes são suspensas por decisão de moderador. |
| RN06 | **Ocupação:** janela móvel de 15 min; abaixo de uma amostra mínima *k* (padrão 5) exibe "dados insuficientes" (protege privacidade e evita falso sinal). |
| RN07 | **Sizígia:** marcada quando a amplitude do dia estiver entre as maiores do ciclo lunar (±2 dias de lua nova/cheia). |
| RN08 | **Fuso horário:** persistir em UTC; exibir no fuso da instalação (o Brasil tem mais de um). |
| RN09 | **Clima:** o backend consulta o provider configurado e armazena a resposta normalizada em cache; clientes nunca chamam o provider diretamente. |
| RN10 | **Maré manual:** eventos cadastrados manualmente devem mostrar fonte, autor, horário da última atualização e validade; após a validade, a UI exibe "desatualizada" e não apresenta o dado como atual. |

---

## 6. Requisitos não funcionais

| ID | Categoria | Requisito | Meta / métrica |
|----|-----------|-----------|----------------|
| RNF01 | Latência do SOS | Alerta chega ao console do guarda-vidas no raio de atendimento. | **p95 < 3 s** e p99 < 5 s, do toque até alerta visual/sonoro, em 4G estável. Medido por sonda sintética contínua. |
| RNF02 | Eficiência de dados | Vídeos com *facade* e carregamento sob demanda; mapa vetorial MapLibre GL; tiles servidos por infraestrutura própria. | Carga inicial < 300 KB (gzip) sem tiles; nenhum iframe antes do toque. |
| RNF03 | Resiliência da maré | Ingestão automática quando houver provider validado ou cadastro operacional manual, cache local, retenção anual. | Sempre ≥ 48 h de previsão futura em banco quando houver dados disponíveis; alerta se cair abaixo disso e identifica a fonte/atualização. |
| RNF04 | Privacidade da ocupação | Detecção no cliente; envio de incremento anônimo; **sem trilhas**. | Nenhum identificador persistente de dispositivo; token efêmero rotativo diário; IP não persistido nesses endpoints. |
| RNF05 | PWA offline parcial | Instalável; fichas, orientações e maré do dia disponíveis sem rede. | Service worker com cache versionado. **SOS não promete envio offline**: orienta ligar 193. |
| RNF06 | Auditoria | Registro imutável de bandeiramento, fechamento de área, atendimento a SOS, moderação. | UTC, ID do operador (CPF cifrado), geolocalização do dispositivo, tabela *append-only* com encadeamento de hash. Retenção definida pelo órgão (sugestão: 5 anos). |
| RNF07 | Disponibilidade | Serviço geral e fluxo de SOS. | Geral: **99,5 %/mês**. SOS na alta temporada: **99,9 %**. Janela de alta temporada configurável. |
| RNF08 | Desempenho e escala | Leituras públicas servidas majoritariamente por cache. | p95 < 300 ms nas leituras cacheadas; dimensionado para 6.000 conexões ativas simultâneas e 300 req/s sustentados (pico 1.000 req/s, ≥ 90 % cache). |
| RNF09 | Segurança | OWASP ASVS nível 2 como referência. | TLS 1.2+, cabeçalhos de segurança/CSP, rate limiting, segredos fora do repositório, SCA e SAST no CI, upload com validação de tipo e remoção de EXIF/GPS. |
| RNF10 | Observabilidade | Logs estruturados, métricas, tracing, dashboards, alertas. | Alerta de SOS sem reconhecimento > 20 s; alerta de evento pendente > 60 s. |
| RNF11 | Portabilidade | Sem *lock-in* de nuvem; nenhum serviço pago é obrigatório para o núcleo. | `docker compose up` sobe o ambiente; clima externo é um recurso configurável que pode exigir `OPENWEATHER_API_KEY`; perfil mínimo roda em 1 VM (≈ 4 vCPU / 8 GB, a validar em teste de carga). |
| RNF12 | Manutenibilidade | Fronteiras de módulos verificadas por teste; ADRs para decisões. | Falha de build se um módulo violar dependência permitida; cobertura mínima de 70 % no domínio. |
| RNF13 | Acessibilidade e i18n | WCAG 2.1 AA, legível sob sol, alvos de toque ≥ 48 px, i18n preparado. | pt-BR no lançamento; textos externalizados. |
| RNF14 | Backup e recuperação | Backup diário e arquivamento de WAL. | **RPO ≤ 15 min**, **RTO ≤ 2 h** no perfil mínimo; restauração testada trimestralmente. |
| RNF15 | LGPD | Retenção por tabela, minimização, base legal documentada. | Política de retenção versionada no repositório; RIPD (relatório de impacto) mantido em `docs/`. |
| RNF16 | Comunidade open source | Documentação, licença e governança claras. | LICENSE, CONTRIBUTING, SECURITY, CODE_OF_CONDUCT, ADRs. |

---

## 7. Estimativa de escala

| Dimensão | Estimativa |
|----------|------------|
| Operacionais simultâneos | 50 a 300 por estado |
| Banhistas — dia útil | 1.000 a 5.000 visualizações/dia |
| Banhistas — verão/feriado | 20.000 a 80.000 usuários ativos/dia; pico de ~6.000 conexões simultâneas à tarde |
| Perfil de tráfego | ~95 % leitura / ~5 % interações ativas |
| SOS | ordem de dezenas/dia no pico (volume baixo, criticidade máxima) |
| Crescimento | por **adoção de novos estados** (instâncias independentes), não por usuários numa instância |

**Classificação:** porte **médio**, com **forte sazonalidade** e **criticidade de vida** no componente de SOS. O desafio não é volume, é **confiabilidade e latência** de um fluxo pequeno e vital, mais o **pico de leitura** concentrado.

---

## 8. Requisitos de privacidade e conformidade

- **Controlador:** cada instalação tem seu controlador (o órgão que a opera). O projeto open source não coleta dados de ninguém.
- **Base legal (a validar com o jurídico do órgão):** proteção da vida e da incolumidade física (LGPD, art. 7º VII e art. 11 II "e") para o SOS; consentimento para os demais usos.
- **Dados de ocorrência** podem envolver saúde; tratar como sensíveis (acesso restrito, cifragem).
- **CPF:** armazenado cifrado (AES-GCM) com índice cego (HMAC) para unicidade; nunca em logs.
- **Localização:** somente no instante do SOS/reporte; sem histórico de deslocamento.
- **Vídeos:** usar `youtube-nocookie.com` para reduzir rastreamento de terceiros.
- Este documento **não é parecer jurídico**.

---

## 9. Decisões registradas e pendências

| ID | Questão | Decisão ou pendência |
|----|---------|-------------------------|
| **D-01** | O SOS deve exigir conta (conforme especificação original) ou permitir uso por qualquer pessoa? | **Decidido: permitir SOS sem conta**, com rate limit, geofence, proteção contra abuso e registro do operador. O botão "Ligar 193" permanece visível. Ver [ADR-001](adr/001-sos-sem-conta.md). |
| **D-02** | CPF digitado só valida dígitos verificadores, não identidade. Manter? | **Decidido: CPF apenas para perfis operacionais e auditoria.** O banhista não precisa informar CPF; login via gov.br pode ser adicionado em R2. Ver [ADR-002](adr/002-cpf-apenas-operacionais.md). |
| **D-03** | Instância única por estado (single-tenant) ou SaaS multi-estado? | **Decidido: single-tenant por instalação.** Soberania de dados, LGPD e operação mais simples. Ver [ADR-003](adr/003-single-tenant-por-instalacao.md). |
| **D-04** | Fonte de dados de maré e sua licença. | **Decidido: manter `TideProvider` abstrato e usar cadastro manual autorizado inicialmente.** Uma fonte automática só entra após validação técnica e dos termos. O clima será separado em `WeatherProvider`, com OpenWeather como provider inicial e cache no backend. Ver [ADR-004](adr/004-provider-abstrato-de-mareas.md). |
| **D-05** | Fonte dos tiles do mapa. | **Decidido: PMTiles/vector tiles próprios** para produção; OSM pode ser fonte dos dados conforme sua licença. Ver [ADR-005](adr/005-pmtiles-para-mapas.md). |
| **D-06** | Licença do projeto. | **Decidido: manter MIT**, já presente no arquivo `LICENSE`, por simplicidade e máxima adoção. Ver [ADR-006](adr/006-licenca-mit.md). |
| **D-07** | Integração com despacho oficial (193/CIOPS)? | **Decidido: manter uma porta de extensão (`DispatchGateway`)**, sem integração obrigatória no MVP. Ver [ADR-007](adr/007-porta-para-despacho-futuro.md). |
| **D-08** | Retenção de dados de SOS e auditoria. | **Decidido: política configurável por instalação**, com padrão provisório de 5 anos para auditoria. A validação jurídica e os prazos definitivos continuam pendentes. Ver [ADR-008](adr/008-retencao-configuravel.md). |
| **D-09** | Identidade: provedor externo ou embutida? | **Decidido: autenticação embutida** no sistema, com possibilidade de integração opcional futura. Ver [ADR-009](adr/009-autenticacao-embutida.md). |

---

## 10. Premissas

1. Guarda-vidas possuem smartphone com dados móveis nos postos (ou o órgão fornece).
2. Cada estado fornece (ou valida) os polígonos de praias/zonas críticas e as tábuas de maré.
3. O órgão responsável assume a operação e a resposta ao SOS; o software é uma ferramenta.
4. Uma instalação atende um estado; instâncias não se comunicam entre si.

---

## 11. Glossário

| Termo | Significado |
|-------|-------------|
| **Corrente de retorno (rip current)** | Fluxo concentrado de água em direção ao mar. |
| **Rebojo** | Redemoinho/turbilhonamento da água, comum em barras e fozes. |
| **Sizígia** | Marés de maior amplitude (lua nova/cheia). |
| **Preamar / baixa-mar** | Maré cheia / maré baixa. |
| **DHN** | Diretoria de Hidrografia e Navegação (Marinha do Brasil). |
| **Region Pack** | Conjunto de dados e configuração que adapta a plataforma a uma região. |
| **PWA** | Progressive Web App: site instalável com recursos offline. |
| **Quórum** | Número mínimo de reportes independentes para validar um alerta. |