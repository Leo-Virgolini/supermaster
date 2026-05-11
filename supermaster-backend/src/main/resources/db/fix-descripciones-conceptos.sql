-- =============================================================
-- Migración: actualizar descripciones de conceptos para que sean claras,
-- coherentes con la fórmula y el comportamiento contable.
-- =============================================================
-- Razón: varias descripciones en la BD estaban desactualizadas (ej: KG_MKT
-- decía "REPUESTO/MENAJE" pero ahora aplica a los 3 tags), usaban jerga
-- interna (ej: "bucket 2"), o no aclaraban si el concepto representa una
-- retención de la plataforma o un gasto real del dueño.
--
-- Este script NO cambia comportamiento de cálculo, solo descripciones.
-- =============================================================

USE supermaster;

-- ---- KT GASTRO ----
UPDATE conceptos_calculo SET descripcion =
    'Ajuste proporcional al margen minorista para productos REPUESTO en KT GASTRO. Porcentaje negativo reduce el margen.'
  WHERE nombre = 'KG_RELMKUP_REP';

UPDATE conceptos_calculo SET descripcion =
    'Ajuste proporcional al margen minorista para productos MENAJE en KT GASTRO. Porcentaje negativo reduce el margen.'
  WHERE nombre = 'KG_RELMKUP_MEN';

UPDATE conceptos_calculo SET descripcion =
    'Marketing / publicaciones KT GASTRO (aplica a los 3 tags: MAQUINA, REPUESTO y MENAJE)'
  WHERE nombre = 'KG_MKT';

UPDATE conceptos_calculo SET descripcion =
    'Embalaje KT GASTRO (solo REPUESTO y MENAJE; MAQUINA no usa embalaje en esta formula)'
  WHERE nombre = 'KG_EMB';

UPDATE conceptos_calculo SET descripcion =
    'Comision adicional KT GASTRO (solo MENAJE / productos sin tag)'
  WHERE nombre = 'KG_COMI';

UPDATE conceptos_calculo SET descripcion =
    'Costo operativo KT GASTRO (solo MENAJE / productos sin tag)'
  WHERE nombre = 'KG_CO';

-- ---- ML ----
-- Nota: ML_COMI fue migrado a COMISION_ML (FLAG_COMISION_ML) en BD productiva.
-- COMISION_ML ya tiene su descripcion correcta, no se actualiza desde aqui.

UPDATE conceptos_calculo SET descripcion =
    'Gasto de marketing / publicaciones del canal ML (gasto real del dueno)'
  WHERE nombre = 'ML_MKT';

UPDATE conceptos_calculo SET descripcion =
    'Gasto de embalaje del envio ML (gasto real del dueno; aplica a todos salvo MAQUINA sin envio)'
  WHERE nombre = 'ML_EMB';

UPDATE conceptos_calculo SET descripcion =
    'Cargo de servicio tecnico ML que se retiene en ventas de tag=MAQUINA'
  WHERE nombre = 'ML_SERTEC';

UPDATE conceptos_calculo SET descripcion =
    'Costo oculto ML para MAQUINA con envio: retencion adicional. Divisor separado que infla el PVP y reduce ingreso del dueno'
  WHERE nombre = 'ML_CO_MAQCENV';

UPDATE conceptos_calculo SET descripcion =
    'Costo oculto ML para REPUESTO: retencion adicional. Divisor separado que infla el PVP y reduce ingreso del dueno'
  WHERE nombre = 'ML_CO_REP';

UPDATE conceptos_calculo SET descripcion =
    'Costo oculto ML para MENAJE / sin tag: retencion adicional. Divisor separado que infla el PVP y reduce ingreso del dueno'
  WHERE nombre = 'ML_CO_MENAJE';

UPDATE conceptos_calculo SET descripcion =
    'Inflacion cosmetica del PVP ML para mostrar precio tachado al cliente. ML no retiene esto: el dueno se queda con la plata extra (no es costo)'
  WHERE nombre = 'ML_PRTACHADO';

-- ---- NUBE ----
UPDATE conceptos_calculo SET descripcion =
    'Ajuste de margen NUBE: suma puntos al margen minorista del producto'
  WHERE nombre = 'KH_RELMKUP';

UPDATE conceptos_calculo SET descripcion =
    'Comision del medio de pago (MercadoPago) que se retiene sobre el PVP'
  WHERE nombre = 'KH_MP';

UPDATE conceptos_calculo SET descripcion =
    'Gasto de marketing / publicaciones NUBE (gasto real del dueno)'
  WHERE nombre = 'KH_MKT';

UPDATE conceptos_calculo SET descripcion =
    'Gasto de embalaje NUBE (gasto real del dueno)'
  WHERE nombre = 'KH_EMB';

UPDATE conceptos_calculo SET descripcion =
    'Comision de la plataforma Tienda Nube sobre el PVP'
  WHERE nombre = 'KH_COMI';

UPDATE conceptos_calculo SET descripcion =
    'Costo financiero fijo: NUBE publica a precio de 6 cuotas (incorporado al PVP base)'
  WHERE nombre = 'KH_6C';

UPDATE conceptos_calculo SET descripcion =
    'Costo oculto NUBE: retencion adicional de la plataforma. Divisor separado que infla el PVP y reduce ingreso del dueno'
  WHERE nombre = 'KH_CO';

UPDATE conceptos_calculo SET descripcion =
    'Inflacion cosmetica del PVP NUBE para mostrar un cupon/descuento al cliente. El dueno se queda con la plata extra (no es costo)'
  WHERE nombre = 'KH_CUPON';

-- ---- LINEA GE ----
UPDATE conceptos_calculo SET descripcion =
    'Logistica LINEA GE: gasto real del dueno (transportista). Amplifica costo+ganancia (LGELOG).'
  WHERE nombre = 'LG_LOG';

UPDATE conceptos_calculo SET descripcion =
    'Marketing LINEA GE: gasto real del dueno (publicidad). Amplifica costo+ganancia (LGEMKT).'
  WHERE nombre = 'LG_MKT';

UPDATE conceptos_calculo SET descripcion =
    'Costo financiero LINEA GE (intereses por pago diferido / medio de pago). Divisor que infla PVP y se cuenta como costo del dueno.'
  WHERE nombre = 'LG_FIN';

UPDATE conceptos_calculo SET descripcion =
    'Inflacion cosmetica del PVP LINEA GE. Divisor separado: el cliente paga el sobrecargo y queda como ganancia del dueno (no es costo).'
  WHERE nombre = 'LG_INF';

-- ---- LIZZY ----
UPDATE conceptos_calculo SET descripcion =
    'LIZZY GASTRO: descuento mayorista sobre el PVP del canal base (Excel: PVP_LINEA_GE * 0,72). El ingreso del dueno se calcula en este punto, antes del markup del reseller.'
  WHERE nombre = 'LZ_GASTRO_DESC';

UPDATE conceptos_calculo SET descripcion =
    'LIZZY GASTRO: markup que el reseller agrega para llegar al consumidor final (Excel: * 1,5). No afecta el ingreso del dueno (solo el del reseller).'
  WHERE nombre = 'LZ_GASTRO_MUP';

UPDATE conceptos_calculo SET descripcion =
    'LIZZY HUDSON: descuento mayorista sobre el PVP del canal base (Excel: PVP_LINEA_GE * 0,65). El ingreso del dueno se calcula en este punto, antes del markup del reseller.'
  WHERE nombre = 'LZ_HUDSON_DESC';

UPDATE conceptos_calculo SET descripcion =
    'LIZZY HUDSON: markup que el reseller agrega para llegar al consumidor final (Excel: * 1,7). No afecta el ingreso del dueno (solo el del reseller).'
  WHERE nombre = 'LZ_HUDSON_MUP';

-- =============================================================
-- POST-MIGRACIÓN:
--   No hace falta recálculo masivo: solo se actualizaron descripciones,
--   no porcentajes ni aplicaSobre. Las descripciones nuevas se ven en la
--   tabla "Conceptos de Cálculo" y en los tooltips de otras pantallas.
-- =============================================================
