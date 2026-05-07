-- =============================================================
-- Carga de ConceptoCalculo, CanalConcepto y CanalConceptoRegla
-- para el canal NUBE (Tienda Nube / Kiosco Hogar) segun la
-- formula nueva del Excel.
--
-- Formula de referencia:
--   costoFinal  = COSTO * (1 + GAN.MIN.ML + KH_RELMKUP)
--   baseImp     = costoFinal * IMP        (IMP = 1 + IVA + IIBB)
--   gastosVenta = KH_MP + KH_MKT + KH_EMB + KH_COMI + KH_6C
--   PVP         = baseImp / (1 - gastosVenta) / (1 - KH_CO) / (1 - KH_CUPON)
--
-- Mapeo fuente (Excel) -> modelo backend:
--   GAN.MIN.ML  -> FLAG_USAR_MARGEN_MINORISTA  (MARGEN_MIN, canonico, reusado de ML)
--   KH_RELMKUP  -> AJUSTE_MARGEN_PUNTOS        (suma puntos al margen)
--   IVA         -> FLAG_APLICAR_IVA            (IVA, canonico, reusado de ML)
--   IIBB        -> IMPUESTO_ADICIONAL          (reusado de ML)
--   KH_MP       -> COMISION_SOBRE_PVP
--   KH_MKT      -> COMISION_SOBRE_PVP
--   KH_EMB      -> COMISION_SOBRE_PVP
--   KH_COMI     -> COMISION_SOBRE_PVP
--   KH_6C       -> COMISION_SOBRE_PVP          (esta HARDCODEADO en la formula)
--   KH_CO       -> RECARGO_CUPON               (bucket 2)
--   KH_CUPON    -> INFLACION_DIVISOR           (bucket 3)
--   PRECIO_INFLADO -> FLAG_APLICAR_PRECIO_INFLADO (canonico; aqui se crea por primera vez)
--
-- Mapeo de PROMO (Excel) -> PrecioInflado:
--   ""    -> sin asignacion (no se calcula pvpInflado)
--   "3X2" -> MULTIPLICADOR, valor 1.500
--   "2X1" -> MULTIPLICADOR, valor 2.000
--   numero (ej: 0.10) -> DIVISOR, valor 0.10  (PVP / (1 - x))
--
-- IMPORTANTE:
--   * Los porcentajes son PLACEHOLDERS. Ajustar con los valores reales del Excel.
--   * En NUBE NO se suma envio (a diferencia de ML).
--   * KH_6C se agrega como concepto fijo (no como opcion de cuota) porque la
--     formula lo aplica siempre. Si el negocio publica a precio de 6 cuotas, este
--     concepto representa el costo financiero incorporado al PVP base.
--   * El filtro del Excel "si TAG=MAQUINA o TAG=REPUESTO -> vacio" NO se modela
--     aqui como regla por concepto: el backend siempre calcula un PVP. Ver nota
--     al final (paso 4) si se quiere excluir estos productos del canal.
-- =============================================================

USE supermaster;

-- -------------------------------------------------------------
-- 1) ConceptoCalculo
--    Conceptos canonicos (IVA, MARGEN_MIN, IIBB) ya fueron creados
--    en ml-conceptos.sql. Aqui solo se crean los conceptos propios
--    de NUBE con porcentaje especifico, mas el canonico PRECIO_INFLADO
--    (que se crea por primera vez aqui).
-- -------------------------------------------------------------
INSERT INTO conceptos_calculo (nombre, porcentaje, aplica_sobre, descripcion) VALUES
  -- Canonico nuevo (FLAG, sin prefijo de canal): primera carga
  ('PRECIO_INFLADO', NULL, 'FLAG_APLICAR_PRECIO_INFLADO', 'Habilita la aplicacion de precios inflados (PrecioInflado asignado al producto-canal)'),
  -- Conceptos con porcentaje propio del canal NUBE
  ('KH_RELMKUP',     5.00,  'AJUSTE_MARGEN_PUNTOS',       'Ajuste: suma puntos al margen minorista para NUBE'),
  ('KH_MP',          6.00,  'COMISION_SOBRE_PVP',         'Comision medio de pago (MercadoPago) en NUBE'),
  ('KH_MKT',         5.00,  'COMISION_SOBRE_PVP',         'Gasto de marketing / publicaciones NUBE'),
  ('KH_EMB',         2.00,  'COMISION_SOBRE_PVP',         'Gasto de embalaje para envio NUBE'),
  ('KH_COMI',        8.00,  'COMISION_SOBRE_PVP',         'Comision de la plataforma Tienda Nube'),
  ('KH_6C',         18.00,  'COMISION_SOBRE_PVP',         'Costo fijo de publicar a precio 6 cuotas en NUBE'),
  ('KH_CO',          5.00,  'RECARGO_CUPON',              'Costo oculto NUBE (bucket 2)'),
  ('KH_CUPON',      10.00,  'INFLACION_DIVISOR',          'Cupon / inflacion NUBE (bucket 3)');

-- -------------------------------------------------------------
-- 2) Asignar conceptos al canal NUBE (canal_concepto)
--    Incluye IIBB (ya existente) y todos los conceptos KH_* nuevos.
-- -------------------------------------------------------------
INSERT INTO canal_concepto (id_canal, id_concepto)
SELECT c.id_canal, cc.id_concepto
FROM canales c
CROSS JOIN conceptos_calculo cc
WHERE c.nombre = 'NUBE'
  AND cc.nombre IN (
    'IVA', 'IIBB', 'MARGEN_MIN', 'KH_RELMKUP',
    'KH_MP', 'KH_MKT', 'KH_EMB', 'KH_COMI',
    'KH_CO', 'KH_CUPON',
    'PRECIO_INFLADO'
  );

-- -------------------------------------------------------------
-- 3) Reglas por TAG (canal_concepto_regla)
--
--    La formula NUBE no tiene condiciones por tag a nivel concepto
--    (todos los conceptos aplican igual a MENAJE). Por eso esta
--    seccion queda vacia.
--
--    El unico "condicional" de la formula es el filtro superior
--    "si TAG=MAQUINA o TAG=REPUESTO -> vacio", que se trata en el
--    paso 4.
-- -------------------------------------------------------------

-- -------------------------------------------------------------
-- 4) [OPCIONAL] Excluir productos MAQUINA y REPUESTO del canal NUBE
--
--    El Excel muestra "" para estos productos: el negocio decidio no
--    publicarlos en NUBE. Para replicar ese comportamiento en el backend
--    hay dos opciones:
--
--    A) Agregar reglas EXCLUIR tag=MAQUINA y EXCLUIR tag=REPUESTO en
--       CADA concepto del canal. Para un producto MAQUINA/REPUESTO no
--       aplicara ningun concepto y el PVP calculado sera el costo base
--       (identificable como "no publicable").
--
--    B) Decidirlo a nivel de presentacion/import: simplemente no generar
--       ProductoCanalPrecio para MAQUINA/REPUESTO en NUBE, o ignorarlos
--       en el frontend.
--
--    La opcion A se puede habilitar descomentando el bloque siguiente.
--    Son 11 conceptos x 2 reglas = 22 filas.
-- -------------------------------------------------------------

-- INSERT INTO canal_concepto_regla (id_canal, id_concepto, tipo_regla, tag)
-- SELECT c.id_canal, cc.id_concepto, 'EXCLUIR', 'MAQUINA'
-- FROM canales c
-- CROSS JOIN conceptos_calculo cc
-- WHERE c.nombre = 'NUBE'
--   AND cc.nombre IN (
--     'IVA', 'IIBB', 'MARGEN_MIN', 'KH_RELMKUP',
--     'KH_MP', 'KH_MKT', 'KH_EMB', 'KH_COMI', 'KH_6C',
--     'KH_CO', 'KH_CUPON'
--   );
--
-- INSERT INTO canal_concepto_regla (id_canal, id_concepto, tipo_regla, tag)
-- SELECT c.id_canal, cc.id_concepto, 'EXCLUIR', 'REPUESTO'
-- FROM canales c
-- CROSS JOIN conceptos_calculo cc
-- WHERE c.nombre = 'NUBE'
--   AND cc.nombre IN (
--     'IVA', 'IIBB', 'MARGEN_MIN', 'KH_RELMKUP',
--     'KH_MP', 'KH_MKT', 'KH_EMB', 'KH_COMI', 'KH_6C',
--     'KH_CO', 'KH_CUPON'
--   );

-- -------------------------------------------------------------
-- 5) Cuotas del canal NUBE (canal_concepto_cuota) - OPCIONAL
--
--    Si ademas del KH_6C hardcoded se quieren ofrecer otras cuotas,
--    cargarlas aca (ejemplo, ajustar porcentajes):
--
--   INSERT INTO canal_concepto_cuota (id_canal, cuotas, porcentaje, descripcion) VALUES
--     ( (SELECT id_canal FROM canales WHERE nombre='NUBE'),  3, 12.00, 'KH_3C'),
--     ( (SELECT id_canal FROM canales WHERE nombre='NUBE'),  9, 22.00, 'KH_9C'),
--     ( (SELECT id_canal FROM canales WHERE nombre='NUBE'), 12, 28.00, 'KH_12C');
-- -------------------------------------------------------------

-- -------------------------------------------------------------
-- 6) Reglas de PrecioInflado para PROMO 3X2 y 2X1
--    (idempotente: chequea por codigo unico)
--
--    Estas reglas son COMPARTIDAS entre canales. Si ya existen porque
--    fueron creadas para otro canal (ML, etc.), no se duplican.
--
--    NOTA: las asignaciones por producto/canal van en
--    producto_canal_precio_inflado y se gestionan via API/UI, no aqui.
-- -------------------------------------------------------------
INSERT INTO precios_inflados (codigo, tipo, valor)
SELECT '3X2', 'MULTIPLICADOR', 1.500
WHERE NOT EXISTS (SELECT 1 FROM precios_inflados WHERE codigo = '3X2');

INSERT INTO precios_inflados (codigo, tipo, valor)
SELECT '2X1', 'MULTIPLICADOR', 2.000
WHERE NOT EXISTS (SELECT 1 FROM precios_inflados WHERE codigo = '2X1');
