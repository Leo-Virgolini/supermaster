-- Filtro "solo productos activos" para los catálogos PDF (manual y automatizados).
-- Por defecto activado (1): los catálogos solo incluyen productos activos salvo que se
-- desmarque explícitamente. Aplica a las configuraciones automatizadas (catalogo_pdf_config);
-- la generación manual envía el flag por query param y no se persiste.
ALTER TABLE supermaster.catalogo_pdf_config
    ADD COLUMN solo_activos TINYINT(1) NOT NULL DEFAULT 1;
