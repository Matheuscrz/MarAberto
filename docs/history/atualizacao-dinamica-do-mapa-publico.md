# Atualização automática de sinalizações e postos no mapa público

> Registro da entrega de 2026-11-25, posterior à administração de identidade e
> à trilha de auditoria. Para o estado operacional vigente, consulte
> `AGENTS.md`, `docs/Architecture.md` e `docs/Requirements.md`.

## Contexto

O mapa público já consultava sinalizações, postos e alertas de área não mapeada
em polling de 30 s, pausava com a aba escondida e revalidava ao voltar o foco e
à reconexão. A lacuna estava no caminho de escrita: `operations` não publicava
evento nenhum e as rotas públicas respondiam `Cache-Control: max-age=30`. Uma
bandeira lançada podia demorar até dois ciclos (≈ 60 s) para aparecer, e não
havia cache de leitura no servidor para amortizar o polling de vários
visitantes.

O objetivo desta entrega é fechar o caminho **escrita → evento → cache →
próximo polling**, sem abrir WebSocket para o banhista e sem recriar o mapa.

## 1. Eventos de domínio em `operations`

- `FlagChanged(flagId, beachId, sectorId)` é publicado no lançamento e no
  encerramento da bandeira.
- `PostChanged(stationId, kind, active)` é publicado no cadastro, na reposição,
  na ativação e na desativação de posto fixo ou base móvel.
- Os eventos saem na mesma transação da escrita. O consumo é
  `@TransactionalEventListener(AFTER_COMMIT)`: rollback não invalida cache, e a
  leitura concorrente não repovoa o cache com o estado anterior ao commit.
- `package-info` do módulo passa a listar somente os eventos que existem de
  fato.

## 2. Cache de leitura pública e HTTP

- `PublicMapCache` guarda a resposta pública por 10 s
  (`maraberto.public-map.cache-ttl`, `PT10S` por padrão). Os dois eventos limpam
  o cache inteiro; o TTL cobre o vencimento por relógio (bandeira que expira sem
  escrita) e a defasagem entre instâncias.
- `/api/v1/flags` e `/api/v1/stations` usam o cache; `/api/v1/unmapped-signals`
  continua lendo direto, porque o alerta não tem evento próprio.
- As três rotas passam a responder `Cache-Control: no-cache` em vez de
  `max-age=30`. Sem um canal aberto com o banhista não há como invalidar o cache
  HTTP; o navegador revalida no ciclo de 30 s e o cache de memória absorve a
  repetição entre visitantes.
- Em várias instâncias, a instância que não recebeu o evento pode servir a
  cópia antiga até o TTL de 10 s — ainda dentro da janela de 30 s do mapa.

## 3. Frontend

Nenhuma mudança de contrato foi necessária: `useSignals`, `useUnmappedAlerts` e
`usePublicStations` já faziam polling de 30 s, pausavam com a aba escondida e
revalidavam em `visibilitychange`/`online`. O `MapView` já reconciliava os
marcadores por `id` (`data-map-marker="true"`), atualizando posição, cor e foto
no lugar, sem recriar a instância do mapa nem resetar a câmera. A entrega
confirma esse comportamento como parte do critério de aceite: o pin novo entra
no próximo ciclo, sem F5 e sem perder zoom/posição.

## Migração

Nenhuma. A entrega é só de backend, documentação e testes; não há `V26` nesta
fatia.

## Testes e validação

- `PublicMapCacheTests`: o valor em cache é devolvido dentro do TTL e
  `FlagChanged`/`PostChanged` forçam a releitura; TTL zero desliga o cache.
- `FlagLifecycleIntegrationTests`: lançamento e encerramento publicam
  `FlagChanged` com o escopo correto.
- `PublicOperationsIntegrationTests`: desativar estrutura publica
  `PostChanged` com `kind` e `active` corretos.
- Nos testes de integração o cache fica desligado
  (`maraberto.public-map.cache-ttl=PT0S`), porque várias classes limpam o banco
  por SQL direto, fora dos serviços.
