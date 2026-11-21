# ADR-029: Alerta de sinalização fora do mapeamento nos mapas

- Status: **Aceito (2026-11-20)** — implementado com `V23`.
- Substitui: ADR-024 na parte em que "nada é publicado no mapa" enquanto a
  triagem não decide.
- Relacionadas: ADR-016 (importação de mapeamento), ADR-021 (área de banho),
  ADR-022 (ciclo de vida da sinalização), ADR-024 (fila de triagem),
  ADR-026 (mapa de situação).
- Requisitos: RF12, RF38, RF40; RN01, RN17.

## Contexto

A ADR-024 passou a aceitar o lançamento por localização que não cai em nenhuma
área ativa, mas o registro só existia na fila de triagem da coordenação. O
guarda-vidas via a confirmação no console e o banhista não via nada — nem no
ponto onde a bandeira foi plantada, nem no mapa da coordenação. Na prática, o
lançamento desaparecia para quem estava na praia, justamente no caso em que o
mapeamento está desatualizado e o alerta é mais necessário.

## Decisão

O lançamento fora do mapeamento passa a ser **alerta de área não mapeada** no
mapa público e no mapa de situação, sem virar bandeira de praia.

1. **Validade igual à da bandeira.** `unmapped_signals.valid_until` é gravado no
   lançamento com a mesma regra de `FlagService` (padrão 12 h; `validityHours`
   entre 0,5 e 24 h). O alerta nasce e vence sozinho — não espera decisão da
   triagem para aparecer nem para sair.
2. **O pin é o ponto do lançamento.** Não há `beachId`, setor, herança por setor
   nem ficha de praia. A cor é a que o guarda-vidas lançou; as fotos são as do
   lançamento; a ficha diz explicitamente que a área não está mapeada.
3. **Triagem continua sendo a fonte completa.** A fila em
   `/gestao/areas-nao-identificadas` mantém **todos** os registros, inclusive
   vencidos e já decididos. Decidir (`IDENTIFIED`, `NEEDS_MAPPING`, `DISCARDED`)
   ou vencer tira o alerta do mapa sem apagar a linha.
4. **Rota pública dedicada.**
   `GET /api/v1/unmapped-signals` devolve somente `PENDING` com
   `valid_until > now()`, do mais recente para o mais antigo, com cache público
   de 30 s. É uma rota separada de `/api/v1/flags`: o contrato de bandeira não
   ganha um caso "sem praia" e a ficha da praia não muda.
5. **Camada própria no mapa de situação.** A coordenação liga/desliga "Áreas não
   mapeadas" em memória, como as demais camadas (ADR-026), e a ficha do alerta
   abre a triagem correspondente.
6. **Marcador próprio.** `UNMAPPED_SIGNAL` tem símbolo (pino com interrogação) e
   borda tracejada, para não ser confundido com a bandeira vigente de uma praia
   mapeada.

### Migração `V23`

- `unmapped_signals.valid_until timestamptz NOT NULL DEFAULT now() + 12 h`.
- Backfill `created_at + 12 h` para os registros existentes.
- Índice parcial `(valid_until DESC, id) WHERE status = 'PENDING'`.

## Alternativas

| Alternativa | Por que não |
| :-- | :-- |
| **Publicar como bandeira em uma praia "mais próxima"** | Associa automaticamente um ponto a uma área que pode estar a quilômetros; a decisão é humana (ADR-024) |
| **Pin permanente até a triagem** | Um erro de GPS ou um registro duplicado ficaria no mapa indefinidamente; a bandeira também vence |
| **Só no mapa da coordenação** | O banhista é quem está no local e não veria o alerta; o pedido era justamente alcançá-lo |
| **Reusar `GET /api/v1/flags` com `beachId` nulo** | Quebra o contrato de bandeira e a ficha da praia; o alerta não tem área nem herança |
| **Publicar no mapa só após `NEEDS_MAPPING`** | Perde a janela em que o risco existe e a triagem pode demorar |

## Consequências

- **Positivas:** o alerta chega a quem está na praia; a coordenação enxerga o
  ponto no lugar geográfico, não só numa lista; a triagem preserva o histórico;
  nada muda no contrato de bandeira.
- **Negativas:** um ponto fora do mapeamento pode aparecer no mapa público sem
  uma praia formal por até 24 h (mitigado pela validade curta, pela triagem e
  pela possibilidade de desativar a estrutura/registro); a rota pública expõe a
  coordenada do lançamento (dado operacional, sem PII).
- **Testes:** alerta público antes da triagem, validade do lançamento, saída por
  vencimento e por decisão, fila preservando o registro.
- **Revisão:** reavaliar quando a triagem puder notificar a coordenação ou
  quando o alerta passar a ser publicado também no app.
