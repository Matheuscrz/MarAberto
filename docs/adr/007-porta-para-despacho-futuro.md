# ADR-007: Porta para despacho oficial futuro

- Status: Aceito (2026-11-06)
- Contexto: integrações com 193/CIOPS dependem do órgão operador e de contratos/ protocolos específicos.
- Decisão: definir uma porta `DispatchGateway`, sem tornar uma integração específica obrigatória no MVP.
- Alternativas: implementar um conector específico agora; não prever extensão.
- Consequências: o núcleo permanece independente; uma integração futura precisará de contrato, autenticação, observabilidade e testes de falha próprios.
