# Política de Segurança

O MarAberto lida com **localização de pessoas**, **dados pessoais** (como CPF e e-mail) e **pedidos de socorro**. Levamos a segurança a sério e agradecemos a quem nos ajuda a mantê-la.

---

## 📦 Versões suportadas

O projeto está em desenvolvimento ativo. Correções de segurança são aplicadas **somente** à versão mais recente:

| Versão                           | Suporte a correções de segurança |
| :------------------------------- | :------------------------------: |
| Branch `main` / última _release_ |                ✅                |
| Versões anteriores               |                ❌                |

Se você opera uma instância, mantenha-a atualizada.

---

## 🔐 Como reportar uma vulnerabilidade

**Não abra uma issue pública, um Pull Request ou uma discussão pública** para relatar vulnerabilidades. Isso exporia usuários e instâncias em operação antes de existir uma correção.

Use um destes canais privados:

1. **GitHub Private Vulnerability Reporting** (preferencial): na aba **Security** do repositório, clique em **Report a vulnerability**.
2. **E-mail:** `matheuslcz@outlook.com` (use o assunto `[SECURITY] maraberto`).

> 📝 **Mantenedor:** habilite o _Private vulnerability reporting_ nas configurações do repositório (Settings → Code security) e substitua o e-mail acima por um endereço real antes de publicar este arquivo.

### O que incluir no relato

- Descrição do problema e do **impacto** (o que um atacante consegue fazer).
- Passos para **reproduzir** (ou prova de conceito mínima).
- Componente afetado (backend, frontend, configuração de implantação) e versão/commit.
- Sugestão de correção, se tiver.
- Como prefere ser citado nos créditos (ou se prefere anonimato).

---

## ⏱️ O que você pode esperar de nós

O projeto é mantido por voluntários, então estes prazos são **metas de melhor esforço**:

| Etapa                                               | Meta                                                                                           |
| :-------------------------------------------------- | :--------------------------------------------------------------------------------------------- |
| Confirmação de recebimento                          | até **5 dias úteis**                                                                           |
| Triagem e avaliação inicial (gravidade, reprodução) | até **10 dias úteis**                                                                          |
| Correção de vulnerabilidades críticas/altas         | o mais rápido possível, com prioridade sobre novas funcionalidades                             |
| Divulgação pública coordenada                       | após a correção disponível, em comum acordo com você; **máximo de 90 dias** a partir do relato |

Manteremos você informado(a) do andamento. Com sua permissão, daremos crédito no aviso de segurança (_security advisory_).

---

## 🎯 Escopo

### Dentro do escopo

- Código deste repositório (backend, frontend/PWA, scripts, migrações).
- Configurações de implantação fornecidas (Docker Compose, Nginx etc.).
- Falhas de **autenticação, autorização, sessão e tokens**.
- **Exposição de dados pessoais** (CPF, e-mail, localização) ou de dados de ocorrências.
- Falhas no fluxo de **SOS** que permitam falsificar, suprimir, atrasar ou redirecionar chamados.
- Injeção (SQL, XSS, SSRF etc.), upload malicioso, execução remota, quebra de controle de acesso.
- Enfraquecimento ou burla da **trilha de auditoria**.
- Dependências com vulnerabilidade conhecida **e explorável** no contexto do projeto.

### Fora do escopo

- Instâncias operadas por terceiros: reporte também ao operador da instância, além de a nós, se quiser.
- Ataques de **negação de serviço volumétrica**, _spam_ e engenharia social contra mantenedores ou usuários.
- Vulnerabilidades que exijam acesso físico ao dispositivo da vítima ou dispositivo já comprometido.
- Relatórios gerados por ferramentas automáticas sem demonstração de impacto real.
- Ausência de cabeçalhos ou boas práticas sem impacto de segurança demonstrável.
- Falhas em serviços de terceiros (YouTube, provedores de maré, serviços de _push_): reporte ao respectivo fornecedor.

---

## 🧪 Regras para pesquisa de segurança (boa-fé)

Para testar com segurança e legalidade, **pedimos que você**:

- Teste **apenas em instância sua** (ambiente local ou de teste), nunca em sistemas de produção de terceiros sem autorização por escrito.
- **Nunca dispare SOS reais** nem crie alertas falsos em uma instância em produção: isso pode acionar guarda-vidas e desviar socorro de quem precisa. Use ambiente de teste.
- **Não acesse, baixe, altere ou divulgue dados de terceiros.** Se encontrar dados pessoais expostos, pare, não copie e nos avise.
- Não degrade o serviço (evite testes de carga e ataques de força bruta em produção).
- Dê-nos um tempo razoável para corrigir antes de divulgar.

Se você seguir estas regras e agir de boa-fé, **não tomaremos nem apoiaremos ação legal** contra a sua pesquisa. Esta política não vincula terceiros nem operadores de instâncias.

---

## 🔒 Proteções implementadas no código

Esta seção descreve o modelo adotado para **todos** os dados vindos da web e para os dados sensíveis em repouso. A decisão está registrada em [ADR-012](docs/adr/012-protecao-de-dados-sensiveis-e-sanitizacao.md).

### Sanitização de entrada

- Todo texto recebido da web passa por uma política explícita em `com.maraberto.shared.security.sanitization`:
  - `PLAIN_TEXT` — remove markup e caracteres de controle, normaliza Unicode e limita o tamanho.
  - `RICH_TEXT` — permite apenas `p`, `br`, `strong`, `b`, `em`, `i`, listas, `blockquote`, `h2`–`h4` e links `http`/`https` com `rel="nofollow"`.
  - `IDENTIFIER`, `EMAIL`, `PHONE`, `CPF`, `URL`, `YOUTUBE_ID` — allowlists específicas.
- DTOs de request usam `@Sanitized(policy, maxLength)`. O `SanitizedStringDeserializer` aplica a política antes do caso de uso.
- **Falha fechada:** identificador, e-mail, telefone, CPF ou URL inválidos viram string vazia para o Bean Validation gerar erro de campo em RFC 9457; texto livre acima do limite é rejeitado com 400. O valor bruto nunca chega ao domínio.
- Sanitização é **defesa em profundidade**, não substitui _output encoding_: o React escapa texto por padrão e é proibido usar `dangerouslySetInnerHTML` com dado não confiável.

### Criptografia de dados sensíveis em repouso

- **AES-256-GCM**, IV aleatório de 12 bytes por operação, tag de 128 bits e **AAD por contexto**. Formato: `enc:v1:<keyId>:<base64url(iv || ciphertext || tag)>`.
- Chave mestra de 32 bytes em base64 (`CRYPTO_MASTER_KEY`), mantida fora do repositório. `keyId` + `previous-keys` permitem **rotação** sem reescrever todos os registros de uma vez.
- `CryptoConfiguration` **falha no boot** se a chave faltar, não tiver 32 bytes ou se a chave de desenvolvimento for usada no perfil `prod`/`production`.
- **Índice cego** (HMAC-SHA256 com chave derivada e domínio separado) para busca de igualdade sobre campo cifrado. Nunca use SHA-256 simples para CPF ou e-mail: o espaço de entrada é pequeno e permite ataque de dicionário.
- `EncryptedStringConverter` (`@Convert`) cifra colunas String sensíveis. Valores legados em claro continuam legíveis durante a migração; novas gravações sempre saem cifradas.
- O payload durável da outbox do SOS é cifrado com AAD `maraberto:emergency-outbox:v1`.
- Localização precisa do SOS permanece em `double precision` por exigência de despacho e geofence; a proteção é acesso restrito, cifragem de volume/backup, minimização, proibição de log e cifragem do payload da outbox.
- **Logs e telemetria** não recebem coordenadas precisas, CPF, e-mail ou telefone. Use `SensitiveDataProtector` (`maskCpf`, `maskEmail`, `maskPhone`, `redactLocation`, `redactIp`).

### Frontend e borda

- CSP restritiva no Nginx: `default-src 'self'`, sem script inline/eval, `frame-src` apenas `https://www.youtube-nocookie.com`. Somados a isso: `nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy`, COOP e CORP.
- O service worker **nunca** cacheia `/api/**`, requisições não-GET ou respostas com `Range`. O shell é network-first para permitir atualização.
- Vídeos só carregam após o toque, em `youtube-nocookie.com`.
- O SOS é honesto: timeout de 5 s, `clientRequestId` (UUID) reutilizado em retries, confirmação **somente** após a resposta do servidor e “Ligar 193” sempre visível. Não há promessa de envio offline.
- Dados de demonstração são sinalizados na interface até existirem endpoints públicos.

### Regras para novos dados

- Todo campo String sensível novo **deve** usar `EncryptedStringConverter` ou `SensitiveDataProtector`; todo DTO novo **deve** usar `@Sanitized` nos campos de texto.
- Campos cifrados não aceitam `LIKE`, ordenação ou filtro por range; igualdade exige índice cego. Modelar consultas que precisem disso fora do campo cifrado.
- Nunca grave PII em claro em outbox, fila, cache, log ou métrica.

---

## 🛡️ Recomendações para quem implanta uma instância

O projeto fornece uma base; a operação segura é responsabilidade de quem implanta.

**Segredos e credenciais**

- **Nunca use as credenciais do `docker-compose` de desenvolvimento em produção** (usuário/senha do banco, console do armazenamento etc.).
- Gere segredos fortes e exclusivos; mantenha-os **fora do repositório** (variáveis de ambiente ou gerenciador de segredos).
- **Defina `CRYPTO_MASTER_KEY`** com 32 bytes aleatórios em base64 e um `CRYPTO_KEY_ID` próprio. A aplicação recusa subir no perfil `prod` com a chave de desenvolvimento do repositório.
- Guarde a chave mestra em gerenciador de segredos com backup seguro: **perdê-la significa perder os dados cifrados**. Defina um plano de **rotação** das chaves de assinatura de tokens e de cifragem (use `previous-keys` durante a transição).
- Restrinja quem consegue ler as variáveis de ambiente do backend e os backups do banco.

**Rede e borda**

- Sirva tudo via **HTTPS (TLS 1.2+)**; habilite HSTS e cabeçalhos de segurança (CSP, `X-Content-Type-Options`, etc.).
- **Não exponha** banco, Redis e armazenamento de objetos à internet; restrinja por rede/firewall.
- Aplique _rate limiting_ na borda (Nginx) e limites de tamanho nas requisições e uploads.

**Contas e acesso**

- Exija **MFA** para perfis operacionais e administrativos.
- Convide operacionais por canal controlado; desative contas de quem sai da corporação.
- Aplique o princípio do menor privilégio no banco (a aplicação não precisa de privilégios de administrador).

**Dados e privacidade (LGPD)**

- Defina quem é o **controlador** dos dados, a **base legal**, a **política de retenção** e mantenha um RIPD quando aplicável.
- Proteja os **backups** (cifragem, acesso restrito) e **teste a restauração** periodicamente.
- Não registre dados pessoais em logs.

**Manutenção**

- Mantenha **sistema operacional, imagens Docker, Java e dependências atualizados**; acompanhe os avisos de segurança do repositório.
- Fixe versões das imagens e acompanhe o fim do suporte de cada componente (frameworks e serviços de armazenamento incluídos).
- Monitore: alertas para SOS sem reconhecimento, falhas de autenticação anômalas e erros 5xx.

---

## 📣 Avisos de segurança

Correções relevantes serão publicadas como **GitHub Security Advisories** e descritas no _changelog_ das versões. Recomendamos **acompanhar (_Watch_) o repositório** com a opção de alertas de segurança.

---

Obrigado por ajudar a manter o MarAberto seguro. 🌊
