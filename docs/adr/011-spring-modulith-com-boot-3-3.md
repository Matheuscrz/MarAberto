# ADR-011: Spring Modulith 1.2.x com Spring Boot 3.3.x

- Status: Aceito (2026-11-07)
- Contexto: a Etapa 1 precisa fechar a arquitetura modular antes do walking skeleton do SOS. O backend já está em Spring Boot 3.3.4 e a stack recomendada no Architecture.md aponta para Boot 4.1.x, mas a migração envolve uma matriz de compatibilidade maior (Spring Framework, Hibernate, Testcontainers e o ecossistema de observabilidade) e não deve bloquear a entrega atual.
- Decisão: usar **Spring Modulith 1.2.13**, linha compatível com Spring Boot 3.3.x, e manter o Boot 3.3.4 nesta etapa. Adicionar `spring-modulith-starter-core`, `spring-modulith-starter-test` e o teste `ApplicationModules.verify()`. A migração para Boot 4.1.x fica registrada como issue futura.
- Alternativas: migrar imediatamente para Boot 4.1.x; adotar uma versão mais nova do Modulith incompatível com o Boot atual; adiar o teste de modularidade.
- Consequências: a Etapa 1 fecha sem risco de regressão de framework; o teste de modularidade passa no CI e as fronteiras dos módulos ficam protegidas desde o início. A dívida de versão permanece explícita e deve ser resolvida antes de a linha 3.3 sair de suporte. Redis, WebSocket e `ObjectStoragePort` entram sem acoplar o domínio a implementações concretas.
