-- =============================================================
-- Migracion: tabla canal_regla
--
-- Proposito:
--   Permite excluir (o incluir selectivamente) productos de un canal
--   mediante condiciones por tag, tipo, marca, clasif_gral,
--   clasif_gastro, tiene_envio o producto individual.
--
-- Semantica de evaluacion (por canal, por producto):
--   - Si hay reglas INCLUIR: el producto debe cumplir AL MENOS UNA
--     (caso contrario queda excluido del canal).
--   - Luego se evaluan reglas EXCLUIR: si cumple alguna, queda excluido.
--   - Dentro de una regla, las condiciones se evaluan con AND.
--
-- Efecto:
--   Cuando un producto queda excluido de un canal, el cálculo no genera
--   ProductoCanalPrecio, y el recálculo masivo borra los que ya existían
--   para esa combinación.
-- =============================================================

USE supermaster;

-- -------------------------------------------------------------
-- 1) Crear tabla
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS canal_regla (
    id                  BIGINT       AUTO_INCREMENT PRIMARY KEY,
    id_canal            INT          NOT NULL,
    tipo_regla          ENUM('INCLUIR','EXCLUIR') NOT NULL DEFAULT 'EXCLUIR',
    tag                 VARCHAR(20)  NULL,
    id_tipo             INT          NULL,
    id_marca            INT          NULL,
    id_clasif_gral      INT          NULL,
    id_clasif_gastro    INT          NULL,
    id_producto         INT          NULL,
    tiene_envio         BOOLEAN      NULL,
    CONSTRAINT fk_canal_regla_canal
        FOREIGN KEY (id_canal) REFERENCES canales(id_canal)
        ON DELETE CASCADE,
    CONSTRAINT fk_canal_regla_tipo
        FOREIGN KEY (id_tipo) REFERENCES tipos(id_tipo)
        ON DELETE SET NULL,
    CONSTRAINT fk_canal_regla_marca
        FOREIGN KEY (id_marca) REFERENCES marcas(id_marca)
        ON DELETE SET NULL,
    CONSTRAINT fk_canal_regla_clasif_gral
        FOREIGN KEY (id_clasif_gral) REFERENCES clasif_gral(id_clasif_gral)
        ON DELETE SET NULL,
    CONSTRAINT fk_canal_regla_clasif_gastro
        FOREIGN KEY (id_clasif_gastro) REFERENCES clasif_gastro(id_clasif_gastro)
        ON DELETE SET NULL,
    CONSTRAINT fk_canal_regla_producto
        FOREIGN KEY (id_producto) REFERENCES productos(id_producto)
        ON DELETE CASCADE,
    INDEX idx_canal_regla_canal (id_canal),
    INDEX idx_canal_regla_producto (id_producto)
);

-- -------------------------------------------------------------
-- 2) Seed inicial: excluir productos MAQUINA y REPUESTO del canal NUBE
--    (idempotente: si ya existe la regla no la duplica)
-- -------------------------------------------------------------
INSERT INTO canal_regla (id_canal, tipo_regla, tag)
SELECT c.id_canal, 'EXCLUIR', 'MAQUINA'
FROM canales c
WHERE c.nombre = 'NUBE'
  AND NOT EXISTS (
      SELECT 1 FROM canal_regla cr
      WHERE cr.id_canal = c.id_canal
        AND cr.tipo_regla = 'EXCLUIR'
        AND cr.tag = 'MAQUINA'
        AND cr.id_tipo IS NULL
        AND cr.id_marca IS NULL
        AND cr.id_clasif_gral IS NULL
        AND cr.id_clasif_gastro IS NULL
        AND cr.id_producto IS NULL
        AND cr.tiene_envio IS NULL
  );

INSERT INTO canal_regla (id_canal, tipo_regla, tag)
SELECT c.id_canal, 'EXCLUIR', 'REPUESTO'
FROM canales c
WHERE c.nombre = 'NUBE'
  AND NOT EXISTS (
      SELECT 1 FROM canal_regla cr
      WHERE cr.id_canal = c.id_canal
        AND cr.tipo_regla = 'EXCLUIR'
        AND cr.tag = 'REPUESTO'
        AND cr.id_tipo IS NULL
        AND cr.id_marca IS NULL
        AND cr.id_clasif_gral IS NULL
        AND cr.id_clasif_gastro IS NULL
        AND cr.id_producto IS NULL
        AND cr.tiene_envio IS NULL
  );

-- -------------------------------------------------------------
-- 3) Limpieza: borrar ProductoCanalPrecio de combinaciones ahora excluidas
--    (productos MAQUINA/REPUESTO en canal NUBE)
--
--    Si despues se agregan mas seeds de exclusion, repetir el patron.
--    Ejecutar UNA sola vez despues de aplicar las reglas.
-- -------------------------------------------------------------
DELETE pcp FROM producto_canal_precios pcp
JOIN productos p ON p.id_producto = pcp.id_producto
JOIN canales   c ON c.id_canal    = pcp.id_canal
WHERE c.nombre = 'NUBE'
  AND p.tag IN ('MAQUINA','REPUESTO');

-- -------------------------------------------------------------
-- 4) Verificacion
-- -------------------------------------------------------------
-- SELECT cr.id, c.nombre AS canal, cr.tipo_regla, cr.tag,
--        cr.id_tipo, cr.id_marca, cr.id_clasif_gral,
--        cr.id_clasif_gastro, cr.id_producto, cr.tiene_envio
-- FROM canal_regla cr
-- JOIN canales c ON c.id_canal = cr.id_canal
-- ORDER BY c.nombre, cr.id;
