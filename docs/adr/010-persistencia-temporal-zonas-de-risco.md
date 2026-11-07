# ADR-010: Persistência temporal das zonas de risco

- Status: Aceito (2026-11-07)
- Contexto: zonas críticas permanentes não representam todas as correntes de retorno. Correntes fixas (píeres, costões, foz de rio) são estruturais; correntes topográficas duram dias ou meses; correntes migratórias mudam em horas.
- Decisão: classificar toda zona em `PERMANENT`, `SEMI_PERMANENT` ou `EPHEMERAL`. `critical_zones` aceita apenas `PERMANENT` (sem validade) e `SEMI_PERMANENT` (com `valid_from`/`valid_until` e `last_confirmed_at`). Correntes `EPHEMERAL` são demarcações dinâmicas em `rip_current_markings` (módulo `operations`), com validade curta.
- Alternativas: tratar toda corrente como zona permanente; criar uma tabela única sem distinção temporal; guardar correntes efêmeras apenas em cache.
- Consequências: o mapa e o GeoJSON precisam filtrar por `active` e janela de validade; zonas vencidas entram em revalidação/expurgo; cache de zonas efêmeras exige TTL curto e invalidação por `RipCurrentMarked`. O modelo deixa de prometer que todo risco delimitado é permanente.
