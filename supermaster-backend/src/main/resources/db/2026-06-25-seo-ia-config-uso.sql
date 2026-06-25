-- SEO IA (config y uso): prompts editables por canal + consumo acumulado de OpenAI.
CREATE TABLE supermaster.seo_prompt (
  id BIGINT NOT NULL AUTO_INCREMENT,
  canal VARCHAR(10) NOT NULL,
  contenido TEXT NOT NULL,
  fecha_modificacion DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_seo_prompt_canal (canal)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE supermaster.seo_uso (
  id BIGINT NOT NULL AUTO_INCREMENT,
  consultas BIGINT NOT NULL DEFAULT 0,
  tokens_entrada BIGINT NOT NULL DEFAULT 0,
  tokens_salida BIGINT NOT NULL DEFAULT 0,
  costo_usd DECIMAL(14,6) NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed: prompt HOGAR = SYSTEM_BASE actual.
INSERT INTO supermaster.seo_prompt (canal, contenido) VALUES ('HOGAR',
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
- No agregar al seo_title sufijos ni etiquetas de rubro o categoría (por ejemplo nada de "- Gastronómico" al final).');

-- Seed: prompt GASTRO = SYSTEM_BASE + REGLA_GASTRO actual.
INSERT INTO supermaster.seo_prompt (canal, contenido) VALUES ('GASTRO',
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
- Enfocá el SEO en el rubro gastronómico y profesional. Podés mencionar en la descripción los usos en cocinas de restaurantes, cafeterías, pastelerías, panaderías, pizzerías o bares cuando ayuden al posicionamiento, integrados de forma natural en el texto (no como una lista forzada ni una muletilla al final). No agregues el rubro como sufijo del seo_title.');

-- Seed: fila singleton de uso en 0.
INSERT INTO supermaster.seo_uso (id, consultas, tokens_entrada, tokens_salida, costo_usd) VALUES (1, 0, 0, 0, 0);
