-- Carátula IA: prompt único editable + consumo acumulado de OpenAI (imágenes).
CREATE TABLE supermaster.imagen_prompt (
  id BIGINT NOT NULL,
  contenido TEXT NOT NULL,
  fecha_modificacion DATETIME NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE supermaster.imagen_uso (
  id BIGINT NOT NULL,
  consultas BIGINT NOT NULL DEFAULT 0,
  tokens_entrada BIGINT NOT NULL DEFAULT 0,
  tokens_salida BIGINT NOT NULL DEFAULT 0,
  costo_usd DECIMAL(14,6) NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO supermaster.imagen_prompt (id, contenido) VALUES (1,
'Recortá el producto de la foto y ponelo centrado sobre un fondo blanco puro, con márgenes chicos, como carátula de producto para venta online (Mercado Libre y Tienda Nube). Conservá el producto sin alterarlo (forma, color y detalles reales); no agregues texto, logos ni sombras marcadas.');

INSERT INTO supermaster.imagen_uso (id, consultas, tokens_entrada, tokens_salida, costo_usd) VALUES (1, 0, 0, 0, 0);
