# Contribuindo com o MarAberto

Obrigado por querer ajudar! O MarAberto é um projeto aberto e comunitário, e toda contribuição é bem-vinda: código, documentação, dados geográficos, revisão, testes, tradução e feedback de quem trabalha com salvamento aquático.

Ao participar, você concorda em seguir o [Código de Conduta](CODE_OF_CONDUCT.md).

> ⚠️ **Contexto importante:** este sistema lida com **localização de pessoas** e com **pedidos de socorro**. Alguns trechos (SOS, bandeiramento, autenticação, privacidade) têm exigências mais rígidas de qualidade e de revisão. Elas estão descritas na seção [Áreas sensíveis](#-áreas-sensíveis).

---

## 📋 Sumário

- [Contribuindo com o MarAberto](#contribuindo-com-o-maraberto)
  - [📋 Sumário](#-sumário)
  - [🧭 Antes de começar](#-antes-de-começar)
  - [💡 Formas de contribuir](#-formas-de-contribuir)
    - [Reportando um bug](#reportando-um-bug)
    - [Sugerindo uma funcionalidade](#sugerindo-uma-funcionalidade)
  - [🛠️ Ambiente de desenvolvimento](#️-ambiente-de-desenvolvimento)
  - [🔀 Fluxo de trabalho](#-fluxo-de-trabalho)
  - [🧱 Padrões de código](#-padrões-de-código)
    - [Backend (Java / Spring Boot)](#backend-java--spring-boot)
    - [Frontend (PWA)](#frontend-pwa)
    - [Geral](#geral)
  - [🚨 Áreas sensíveis](#-áreas-sensíveis)
    - [SOS e despacho (`emergency`)](#sos-e-despacho-emergency)
    - [Privacidade e LGPD](#privacidade-e-lgpd)
    - [Autenticação e autorização](#autenticação-e-autorização)
    - [Auditoria](#auditoria)
    - [Dados geográficos e conteúdo](#dados-geográficos-e-conteúdo)
  - [✅ Checklist de Pull Request](#-checklist-de-pull-request)
  - [🔍 Revisão e merge](#-revisão-e-merge)
  - [📄 Licença das contribuições](#-licença-das-contribuições)
  - [🙋 Dúvidas?](#-dúvidas)

---

## 🧭 Antes de começar

1. Leia o [`README.md`](README.md) e os documentos em `docs/`:
   - [`docs/Requirements.md`](docs/Requirements.md): o que o sistema deve fazer e por quê.
   - [`docs/Architecture.md`](docs/Architecture.md): como o sistema é organizado e quais decisões já foram tomadas.
2. **Procure uma issue existente** antes de abrir outra.
3. Para mudanças grandes (novo módulo, nova dependência relevante, mudança de arquitetura), **abra uma issue para discutir antes** de escrever código. Isso evita retrabalho.
4. **Vulnerabilidades de segurança não vão em issue pública.** Veja o [`Security.md`](Security.md).

### Uso de inteligência artificial

Contribuições desenvolvidas com apoio de ferramentas de inteligência artificial
são bem-vindas. A pessoa que envia o PR continua responsável por revisar o código,
verificar a licença do conteúdo gerado, executar os testes e garantir que a
contribuição não contenha segredos, dados pessoais ou código incompatível com a
licença do projeto.

---

## 💡 Formas de contribuir

| Tipo | Exemplos |
| :--- | :--- |
| **Código** | Correção de bugs, novas funcionalidades, melhorias de desempenho, testes. |
| **Documentação** | Correções, exemplos, guias de implantação, tradução. |
| **Dados geográficos** | Polígonos de praias e zonas de risco, postos, estações de maré de outras regiões. |
| **Conteúdo educativo** | Sugestões de vídeos e textos de prevenção revisados por profissionais. |
| **Revisão e testes** | Revisar PRs, testar em dispositivos reais, relatar problemas de usabilidade na praia. |
| **Experiência de campo** | Feedback de guarda-vidas, bombeiros e especialistas sobre o fluxo operacional. |

### Reportando um bug

Abra uma issue com:

- O que você esperava e o que aconteceu.
- Passos para reproduzir.
- Ambiente (navegador/dispositivo, versão do Java, sistema operacional).
- Logs ou capturas de tela, **sem dados pessoais** (CPF, e-mail, localização exata de pessoas).

### Sugerindo uma funcionalidade

Descreva o **problema** que ela resolve (e para quem), antes da solução. Se possível, relacione com um requisito de `docs/Requirements.md`.

---

## 🛠️ Ambiente de desenvolvimento

**Pré-requisitos:** Java 21 LTS, Node.js 20+, Docker e Docker Compose.

```bash
# 1) Infraestrutura de apoio (PostGIS, Redis, armazenamento S3-compatível)
docker compose up -d

# 2) Backend
cd backend
./gradlew bootRun

# 3) Frontend
cd frontend
npm install
npm run dev
```

Rodar os testes e as verificações:

```bash
cd backend && ./gradlew clean check   # build + testes
```

> As credenciais do `docker-compose` são **apenas para desenvolvimento local**.

---

## 🔀 Fluxo de trabalho

1. Faça um **fork** do repositório e clone o seu fork.
2. Crie uma branch a partir da `main`:

   ```bash
   git checkout -b feature/nome-curto
   ```

   Prefixos sugeridos: `feature/`, `fix/`, `docs/`, `refactor/`, `test/`, `chore/`.
3. Faça commits pequenos e focados, seguindo [**Conventional Commits**](https://www.conventionalcommits.org/pt-br/):

   ```
   feat(emergency): reconhecer SOS com compare-and-set
   fix(beaches): validar polígono antes de salvar
   docs: atualizar guia de execução
   ```

4. Mantenha sua branch atualizada com a `main` (`rebase` ou `merge`, o que preferir).
5. Rode `./gradlew clean check` (e o lint do frontend) antes de enviar.
6. Abra um **Pull Request** explicando **o que** mudou e **por quê**, vinculando a issue (`Closes #123`).

Prefira PRs pequenos. Um PR que faz uma coisa bem feita é revisado mais rápido do que um PR gigante.

---

## 🧱 Padrões de código

### Backend (Java / Spring Boot)

- Respeite as **fronteiras dos módulos/contextos** (`identity`, `region`, `beaches`, `operations`, `emergency`, `safety`, `tides`, `content`, `occupancy`, `media`, `realtime`, `audit`, `analytics`, `shared`). Um módulo não deve acessar o interior de outro; use a API pública do módulo ou eventos de domínio. O teste `ApplicationModules.verify()` falha o build em caso de violação.
- Regras de negócio ficam no **domínio**, não em controllers. Controllers apenas traduzem HTTP para casos de uso.
- **Migrações de banco** (Flyway) são imutáveis depois de integradas: para corrigir, crie uma nova migração.
- Consultas geoespaciais: use `geography` para distâncias em metros e `geometry` para polígonos; cuide dos índices GiST e valide a geometria.
- Escreva **testes** junto com o código: unitários para regras de negócio e de integração (preferencialmente com Testcontainers) para banco, cache e armazenamento.
- Trate erros de forma explícita e use mensagens que não vazem detalhes internos.

### Frontend (PWA)

- Priorize **desempenho em rede ruim** (3G/4G) e legibilidade sob sol forte: alto contraste, alvos de toque grandes.
- Vídeos do YouTube usam carregamento sob demanda (*facade*); não carregue iframes antes do clique.
- Acessibilidade conta: rótulos, contraste e navegação por teclado.

### Geral

- Nomes de código em inglês e comentários/documentação em português ou inglês, de forma consistente dentro do arquivo.
- Sem código comentado, `TODO` sem issue vinculada ou *logs* de depuração esquecidos.
- Formate o código conforme as ferramentas do projeto (linter/formatter configurados).

---

## 🚨 Áreas sensíveis

Mudanças nas áreas abaixo têm exigências adicionais e podem receber revisão mais rigorosa.

### SOS e despacho (`emergency`)

- Mantenha a **idempotência** (um mesmo pedido não pode gerar dois SOS).
- Preserve a **persistência antes da notificação** (nunca avisar sem ter gravado).
- Operações concorrentes (ex.: dois postos assumindo o mesmo SOS) precisam de **teste de concorrência**.
- Falhas de infraestrutura (ex.: Redis fora) **não podem impedir** a criação de um SOS.
- A interface **nunca** deve indicar que o socorro está a caminho sem confirmação real.

### Privacidade e LGPD

- **Nunca** registre em log CPF, senha, token, e-mail completo ou coordenadas de pessoas.
- **Não** crie mecanismos que permitam reconstruir o deslocamento de uma pessoa (a estimativa de ocupação é anônima e agregada por desenho).
- Colete o mínimo de dados necessário e documente qualquer novo dado pessoal na descrição do PR.

### Autenticação e autorização

- Não implemente criptografia ou protocolos por conta própria: use as bibliotecas e primitivas padrão do projeto.
- Toda nova rota precisa de **regra de autorização explícita** e de teste cobrindo os perfis que **não** devem ter acesso.
- Alterações nessa área exigem revisão de pelo menos um mantenedor.

### Auditoria

- Atos de sinalização, fechamento de área crítica e atendimento a chamados devem ser auditados. Não remova nem enfraqueça registros de auditoria.

### Dados geográficos e conteúdo

- Ao contribuir com polígonos, postos ou estações de maré, **informe a fonte e a licença** dos dados. Não envie dados cuja redistribuição não seja permitida (por exemplo, tábuas ou bases com termos restritivos).
- Conteúdo educativo (vídeos, textos) deve ser tecnicamente correto e, de preferência, revisado por profissional de salvamento.
- **Não publique** imagens de vítimas, ocorrências reais ou pessoas identificáveis.

---

## ✅ Checklist de Pull Request

Antes de pedir revisão, confirme:

- [ ] O PR faz **uma coisa** e a descrição explica o **porquê**.
- [ ] Há issue vinculada (ou justificativa para não haver).
- [ ] Testes novos ou atualizados; `./gradlew clean check` passa.
- [ ] Nenhum segredo, credencial real ou dado pessoal no código, nos testes ou nos logs.
- [ ] Migrações de banco novas (não alterei migrações antigas).
- [ ] Documentação atualizada (README, `docs/`) quando o comportamento mudou.
- [ ] Novas dependências: **licença compatível com a MIT**, mantida ativamente, versão fixada e justificativa na descrição.
- [ ] Se toca em área sensível, expliquei o impacto (SOS, privacidade, autenticação, auditoria).
- [ ] Interface: testei em tela pequena e com rede lenta, quando aplicável.

---

## 🔍 Revisão e merge

- Um mantenedor revisará o PR assim que possível. O projeto é mantido por voluntários: pode levar alguns dias.
- Podemos pedir ajustes. Isso faz parte do processo e não é uma crítica pessoal.
- O merge exige **CI verde** e pelo menos **uma aprovação** (duas para áreas sensíveis, quando houver mais de um mantenedor).
- Preferimos **squash merge** com uma mensagem em Conventional Commits.
- PRs sem atividade por muito tempo podem ser fechados, e você pode reabri-los quando quiser.

---

## 📄 Licença das contribuições

Ao enviar uma contribuição, você concorda que ela será licenciada sob a [Licença MIT](LICENSE), a mesma do projeto, e declara que tem o direito de contribuí-la.

---

## 🙋 Dúvidas?

Abra uma issue com a etiqueta `pergunta` ou inicie uma discussão no repositório. Se for sua primeira contribuição, procure issues com a etiqueta `good first issue`. Será um prazer ajudar.