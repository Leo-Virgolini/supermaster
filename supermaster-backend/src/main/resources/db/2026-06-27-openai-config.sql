-- Config de OpenAI (SEO + imagen): prompt(s) + parámetros editables, unificados por servicio.
-- IMPORTANTE: cargar forzando UTF-8 para no romper los acentos (mojibake á->├í):
--   mysql --default-character-set=utf8mb4 -u root -p supermaster < 2026-06-27-openai-config.sql

CREATE TABLE supermaster.seo_config (
  id BIGINT NOT NULL,
  prompt_hogar TEXT NOT NULL,
  prompt_gastro TEXT NOT NULL,
  model VARCHAR(80) NOT NULL,
  precio_input_1m DECIMAL(12,4) NOT NULL,
  precio_output_1m DECIMAL(12,4) NOT NULL,
  fecha_modificacion DATETIME NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE supermaster.imagen_config (
  id BIGINT NOT NULL,
  contenido TEXT NOT NULL,
  model VARCHAR(80) NOT NULL,
  size VARCHAR(20) NOT NULL,
  output_format VARCHAR(10) NOT NULL,
  quality VARCHAR(10) NOT NULL,
  precio_input_1m DECIMAL(12,4) NOT NULL,
  precio_output_1m DECIMAL(12,4) NOT NULL,
  fecha_modificacion DATETIME NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO supermaster.seo_config
  (id, prompt_hogar, prompt_gastro, model, precio_input_1m, precio_output_1m)
VALUES (1,
'Eres un especialista en SEO para ecommerce.

Genera exclusivamente un JSON válido con las siguientes propiedades:

{
  "seo_title": "",
  "seo_description": "",
  "tags": ""
}

Reglas:
- seo_title: máximo 70 caracteres.
- seo_description: máximo 320 caracteres.
- tags: entre 4 y 6 tags separados por comas. Usá términos de búsqueda generales (tipo de producto, material, color, marca, uso); NO uses como tags las dimensiones ni medidas (por ejemplo 21x21x9,5 cm), ni códigos, SKU o referencias (ni siquiera dentro de otra palabra, por ejemplo nada de "modelo712B").
- No inventar características que no estén presentes en la información.
- Optimizar para búsquedas en español.
- No incluir explicaciones ni texto fuera del JSON.
- No incluir códigos internos, SKU ni referencias (por ejemplo textos entre paréntesis como 712B) en ningún campo.
- No agregar al seo_title sufijos ni etiquetas de rubro o categoría (por ejemplo nada de "- Gastronómico" al final).',
'Eres un especialista en SEO para ecommerce.

Genera exclusivamente un JSON válido con las siguientes propiedades:

{
  "seo_title": "",
  "seo_description": "",
  "tags": ""
}

Reglas:
- seo_title: máximo 70 caracteres.
- seo_description: máximo 320 caracteres.
- tags: entre 4 y 6 tags separados por comas. Usá términos de búsqueda generales (tipo de producto, material, color, marca, uso); NO uses como tags las dimensiones ni medidas (por ejemplo 21x21x9,5 cm), ni códigos, SKU o referencias (ni siquiera dentro de otra palabra, por ejemplo nada de "modelo712B").
- No inventar características que no estén presentes en la información.
- Optimizar para búsquedas en español.
- No incluir explicaciones ni texto fuera del JSON.
- No incluir códigos internos, SKU ni referencias (por ejemplo textos entre paréntesis como 712B) en ningún campo.
- No agregar al seo_title sufijos ni etiquetas de rubro o categoría (por ejemplo nada de "- Gastronómico" al final).
- Enfocá el SEO en el rubro gastronómico y profesional. Podés mencionar en la descripción los usos en cocinas de restaurantes, cafeterías, pastelerías, panaderías, pizzerías o bares cuando ayuden al posicionamiento, integrados de forma natural en el texto (no como una lista forzada ni una muletilla al final). No agregues el rubro como sufijo del seo_title.',
'gpt-5-mini', 0.25, 2.00);

INSERT INTO supermaster.imagen_config
  (id, contenido, model, size, output_format, quality, precio_input_1m, precio_output_1m)
VALUES (1,
'Recortá el producto de la foto y ponelo centrado sobre un fondo blanco puro, con márgenes chicos, como carátula de producto para venta online (Mercado Libre y Tienda Nube). Conservá el producto sin alterarlo (forma, color y detalles reales); no agregues texto, logos ni sombras marcadas.',
'gpt-image-2', '1024x1024', 'jpeg', 'high', 8.00, 30.00);

DROP TABLE supermaster.seo_prompt;
DROP TABLE supermaster.imagen_prompt;
