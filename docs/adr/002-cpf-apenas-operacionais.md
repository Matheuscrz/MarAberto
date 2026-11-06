# ADR-002: CPF apenas para perfis operacionais

- Status: Aceito (2026-11-06)
- Contexto: CPF digitado e validado por dígitos não comprova identidade, além de ser dado pessoal desnecessário para o banhista.
- Decisão: exigir CPF somente quando necessário para perfis operacionais e auditoria; não solicitar CPF aos banhistas. Uma integração gov.br pode validar identidade em uma etapa futura sem alterar o SOS sem conta.
- Alternativas: exigir CPF de todos; não aceitar CPF em nenhum fluxo.
- Consequências: menor coleta de dados e menor atrito para o público; perfis operacionais exigem proteção, controle de acesso e nunca devem expor CPF em logs.
