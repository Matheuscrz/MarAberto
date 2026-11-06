# ADR-001: Permitir SOS sem conta

- Status: Aceito (2026-11-06)
- Contexto: uma pessoa em perigo ou testemunha pode não ter conta, e exigir cadastro adiciona atrito ao fluxo crítico.
- Decisão: permitir SOS sem conta, protegido por rate limit, geofence e mecanismos de triagem/registro de abuso. O acionamento oficial pelo 193 continua visível.
- Alternativas: exigir conta; permitir apenas usuários autenticados.
- Consequências: maior acessibilidade e menor tempo para pedir ajuda; será necessário controlar abuso, registrar o contexto mínimo e testar idempotência.
