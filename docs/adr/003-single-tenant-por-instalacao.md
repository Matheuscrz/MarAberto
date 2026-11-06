# ADR-003: Uma instalação por estado ou órgão

- Status: Aceito (2026-11-06)
- Contexto: o projeto precisa preservar soberania de dados e manter a operação simples para equipes pequenas.
- Decisão: o núcleo será single-tenant; cada estado ou órgão opera sua própria instância.
- Alternativas: SaaS multi-tenant desde o início.
- Consequências: isolamento e implantação mais simples; compartilhamento entre instalações não fará parte do núcleo inicial.
