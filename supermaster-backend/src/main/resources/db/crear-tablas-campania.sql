-- Tablas para campañas a partir de categorías de Tienda Nube.
-- Schema supermaster. Ejecutar manualmente antes de levantar (ddl-auto=validate).

CREATE TABLE supermaster.campania (
    id                 INT AUTO_INCREMENT PRIMARY KEY,
    tn_categoria_id    BIGINT       NOT NULL,
    nombre             VARCHAR(150) NOT NULL,
    id_canal           INT          NOT NULL,
    fecha_desde        DATE         NULL,
    fecha_hasta        DATE         NULL,
    activa             TINYINT(1)   NOT NULL DEFAULT 0,
    fecha_ultima_sync  DATETIME     NULL,
    observaciones      VARCHAR(255) NULL,
    CONSTRAINT uq_campania_tn_categoria UNIQUE (tn_categoria_id),
    CONSTRAINT fk_campania_canal FOREIGN KEY (id_canal)
        REFERENCES supermaster.canales (id_canal)
);

CREATE TABLE supermaster.campania_producto (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    id_campania    INT            NOT NULL,
    id_producto    INT            NOT NULL,
    precio_manual  DECIMAL(15,2)  NULL,
    fecha_sync     DATETIME       NULL,
    observaciones  VARCHAR(255)   NULL,
    CONSTRAINT uq_campania_producto UNIQUE (id_campania, id_producto),
    CONSTRAINT fk_campprod_campania FOREIGN KEY (id_campania)
        REFERENCES supermaster.campania (id) ON DELETE CASCADE,
    CONSTRAINT fk_campprod_producto FOREIGN KEY (id_producto)
        REFERENCES supermaster.productos (id_producto) ON DELETE CASCADE
);

CREATE INDEX idx_campania_producto_campania ON supermaster.campania_producto (id_campania);
