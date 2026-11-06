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

## 🛡️ Recomendações para quem implanta uma instância

O projeto fornece uma base; a operação segura é responsabilidade de quem implanta.

**Segredos e credenciais**

- **Nunca use as credenciais do `docker-compose` de desenvolvimento em produção** (usuário/senha do banco, console do armazenamento etc.).
- Gere segredos fortes e exclusivos; mantenha-os **fora do repositório** (variáveis de ambiente ou gerenciador de segredos).
- Defina um plano de **rotação** das chaves de assinatura de tokens e de cifragem.

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
