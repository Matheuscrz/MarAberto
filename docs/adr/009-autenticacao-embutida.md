# ADR-009: Autenticação embutida

- Status: Aceito (2026-11-06)
- Contexto: o projeto deve funcionar sem depender de um provedor de identidade externo, embora integrações possam ser adicionadas depois.
- Decisão: manter registro, login e autorização no módulo `identity`, usando Spring Security e primitivas padrão; OIDC/gov.br permanece opcional.
- Alternativas: exigir um IdP externo; suportar somente gov.br.
- Consequências: instalação simples e controle do fluxo de identidade; a equipe assume responsabilidade por segurança, recuperação de conta, rotação de tokens e revisão contínua.
