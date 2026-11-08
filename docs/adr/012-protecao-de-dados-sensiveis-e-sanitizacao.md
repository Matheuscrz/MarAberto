# ADR-012: Sanitização de entrada e criptografia de dados sensíveis

- Status: Aceito (2026-11-08)
- Contexto: o MarAberto recebe dados da web (texto livre, identificadores, e-mail, telefone, CPF, URL, ID de vídeo) e armazena dados sensíveis (localização precisa de SOS, payload da outbox, e futuramente CPF, e-mail e telefone de operacionais). O projeto precisa de uma regra única para os dados atuais e para os que ainda serão adicionados, sem criptografia caseira e sem depender de o frontend para validar.
- Decisão:

  **Sanitização (entrada)**
  - Usar **OWASP Java HTML Sanitizer** com allowlists explícitas. Texto livre usa `PLAIN_TEXT`; conteúdo editorial usa `RICH_TEXT` (p, br, strong, em, listas, blockquote, h2–h4 e links http/https com `rel="nofollow"`).
  - Cada tipo de dado tem política própria: `IDENTIFIER`, `EMAIL`, `PHONE`, `CPF`, `URL`, `YOUTUBE_ID`. A política mais restritiva que atende ao caso é obrigatória.
  - DTOs de request marcam campos String com `@Sanitized(policy, maxLength)`. O `SanitizedStringDeserializer` aplica a política **antes** do caso de uso.
  - Falha fechada: política de identificador/e-mail/CPF/URL inválida vira string vazia para o Bean Validation produzir erro de campo em RFC 9457; texto livre acima do limite é rejeitado com 400. Nada de aceitar o valor bruto e “corrigir depois”.
  - Normalização NFKC, remoção de caracteres de controle e limite de tamanho em toda política.
  - Sanitização **não substitui output encoding**: o React escapa texto por padrão e é proibido usar `dangerouslySetInnerHTML` com dado não confiável.

  **Criptografia (repouso)**
  - **AES-256-GCM** com IV aleatório de 12 bytes por operação e tag de 128 bits. Formato do token: `enc:v1:<keyId>:<base64url(iv || ciphertext || tag)>`.
  - **AAD por contexto** (`SensitiveDataProtector.AAD_*`): um ciphertext movido para outro contexto falha na decifragem.
  - Chave mestra de 32 bytes em base64, fora do repositório (`CRYPTO_MASTER_KEY`). O `keyId` permite **rotação**: chaves antigas ficam em `previous-keys` até a reescrita dos registros.
  - `CryptoConfiguration` falha no boot se a chave faltar, não tiver 32 bytes ou se a chave de desenvolvimento for usada no perfil `prod`/`production`.
  - **Índice cego** (blind index) para busca de igualdade sobre campo cifrado: HMAC-SHA256 com chave derivada da chave mestra e domínio separado (`maraberto:blind-index:v1`). Nunca SHA-256 simples em CPF/e-mail.
  - `EncryptedStringConverter` (`@Convert`) para colunas String sensíveis, com leitura de valores legados em claro durante a migração e gravação sempre cifrada. O AAD do converter é fixo por coluna; vínculo a um registro específico exige cifrar no serviço.
  - Localização precisa do SOS continua em `double precision` porque o despacho e o geofence precisam dela. A proteção é: acesso restrito, cifragem de volume/backup, minimização, proibição de log e cifragem do payload durável da outbox.
  - Logs e telemetria nunca recebem coordenadas precisas, CPF, e-mail ou telefone; usar `SensitiveDataProtector` (`maskCpf`, `maskEmail`, `maskPhone`, `redactLocation`, `redactIp`).

  **Frontend e borda**
  - CSP restritiva no Nginx (`default-src 'self'`, sem script inline/eval, `frame-src` apenas `youtube-nocookie.com`), além de `nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy` e COOP/CORP.
  - Vídeos carregam apenas após o toque, em `youtube-nocookie.com`.
  - Service worker **nunca** cacheia `/api/**`, requisições não-GET ou respostas com `Range`; o shell é network-first para permitir atualização.
  - SOS honesto: timeout de 5 s, `clientRequestId` reutilizado em retries, confirmação só após resposta do servidor e “Ligar 193” sempre visível. Nenhuma promessa de envio offline.
  - Dados de demonstração são sinalizados na interface até existirem endpoints públicos.

- Alternativas:
  - `pgcrypto` no PostgreSQL: manteria a chave no banco e misturaria responsabilidades; dificulta rotação e auditoria.
  - Vault/KMS gerenciado: melhor para produção de grande porte, mas adiciona custo operacional incompatível com o estágio atual; o formato com `keyId` permite migrar depois.
  - Hash simples (SHA-256) para CPF/e-mail: quebra por dicionário, já que o espaço de entrada é pequeno.
  - Sanitização apenas no frontend: insuficiente, pois qualquer cliente pode chamar a API direto.
  - Criptografar coordenadas na mesma coluna usada para geofence: inviabiliza consultas espaciais sem ganho real de segurança.

- Consequências:
  - A chave mestra passa a ser o segredo mais crítico da instalação: perdê-la significa perder os dados cifrados; vazá-la compromete todos eles. Backup, rotação e acesso precisam de procedimento documentado.
  - Campos cifrados não aceitam filtro por range, ordenação ou `LIKE`; igualdade exige índice cego. Consultas que precisem disso devem ser modeladas fora do campo cifrado (ex.: coluna derivada, view ou agregado).
  - Novos campos sensíveis **devem** usar `EncryptedStringConverter` ou `SensitiveDataProtector`; novos DTOs **devem** usar `@Sanitized`. Isso entra no checklist de revisão de PR.
  - Testes de integração usam a chave de desenvolvimento; o CI não precisa de segredo real, mas o perfil `prod` recusa essa chave.
  - Migrações futuras precisam reescrever registros legados em claro e só então remover a leitura tolerante.
  - O `clientRequestId` do SOS é validado como UUID, o que elimina identificadores arbitrários e reduz abuso.
