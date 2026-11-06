# ADR-005: PMTiles para mapas de produção

- Status: Aceito (2026-11-06)
- Contexto: servidores públicos do OpenStreetMap não são uma dependência adequada para tráfego de produção.
- Decisão: usar PMTiles ou vector tiles próprios, gerados a partir de dados com licença compatível. Servidores públicos podem ser usados somente em protótipos, respeitando suas políticas.
- Alternativas: depender dos tiles públicos; contratar um provedor externo.
- Consequências: maior controle de disponibilidade e custos previsíveis; será necessário gerar, versionar ou publicar os recortes regionais e documentar suas fontes.
