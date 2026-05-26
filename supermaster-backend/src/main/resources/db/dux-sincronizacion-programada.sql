-- =============================================================
-- Migracion: sincronizacion programada con DUX
--
-- Solo crea la tabla de horarios. La importacion existente actualiza
-- directamente la tabla `productos`, y el cursor del sync incremental
-- (fecha del ultimo sync exitoso) se reusa de `config_automatizacion`
-- con la clave `ultima_fecha_dux`, que ya gestiona el flujo de
-- "Automatizacion Precios KT". De esa forma ambos puntos de entrada
-- comparten el mismo cursor y avanzan en consistencia.
--
-- Sin filas en dux_horario_sync => no hay sync automatica (solo manual).
-- =============================================================

USE supermaster;

CREATE TABLE IF NOT EXISTS dux_horario_sync (
    id      BIGINT  AUTO_INCREMENT PRIMARY KEY,
    hora    INT     NOT NULL,
    minuto  INT     NOT NULL,
    UNIQUE KEY uk_dux_horario_sync (hora, minuto),
    INDEX idx_dux_horario_sync_orden (hora, minuto),
    CONSTRAINT ck_dux_horario_sync_hora   CHECK (hora   BETWEEN 0 AND 23),
    CONSTRAINT ck_dux_horario_sync_minuto CHECK (minuto BETWEEN 0 AND 59)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Verificacion:
-- SELECT * FROM dux_horario_sync ORDER BY hora, minuto;
-- SELECT * FROM config_automatizacion WHERE clave = 'ultima_fecha_dux';
