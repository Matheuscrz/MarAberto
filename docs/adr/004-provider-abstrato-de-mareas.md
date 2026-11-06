# ADR-004: Providers separados para clima e maré

- Status: Aceito (2026-11-06)
- Contexto: o OpenWeather oferece clima e previsão, mas sua documentação do One Call API 3.0 não apresenta uma tábua de marés com preamar, baixa-mar e altura. Fontes de maré também podem ter diferenças de cobertura e termos de redistribuição.
- Decisão: separar `WeatherProvider` e `TideProvider`. O OpenWeather será o provider inicial de clima quando a instalação configurar `OPENWEATHER_API_KEY`, sempre chamado pelo backend e com cache local. Enquanto uma fonte de maré adequada não for validada, os dados de maré serão cadastrados manualmente por perfis operacionais autorizados. Uma fonte automática futura deverá implementar `TideProvider` após validação técnica e dos termos de uso.
- Alternativas: usar OpenWeather como fonte de marés; acoplar diretamente à DHN; acoplar a uma API comercial de marés; remover o painel de marés.
- Consequências: os banhistas recebem clima sem uma chamada por usuário; a instalação precisa configurar uma chave OpenWeather para habilitar o clima; a maré exige fluxo de cadastro, validade, auditoria e indicação explícita de atualização manual até existir um provider automático.

Referência: [OpenWeather One Call API 3.0](https://openweathermap.org/api/one-call-3), consultada em 2026-11-06. A página informa clima atual, previsões e 1.000 chamadas/dia gratuitas no produto One Call, mas não documenta eventos de maré.
