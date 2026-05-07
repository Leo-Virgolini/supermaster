package ar.com.leo.super_master_backend.dominio.concepto_calculo.entity;

/**
 * Enum que representa los valores posibles para el campo aplica_sobre
 * en la tabla conceptos_calculo.
 *
 * Los valores están agrupados por etapa del cálculo de precio.
 * El nombre de cada valor indica su función y en qué etapa se aplica.
 */
public enum AplicaSobre {

    // ===== ETAPA: COSTO =====
    /**
     * Gasto que multiplica el costo base: COSTO × (1 + %/100)
     * Ejemplo: Embalaje +2%
     */
    GASTO_SOBRE_COSTO(Etapa.COSTO, NaturalezaConcepto.INFLACION),

    /**
     * Flag: usa proveedor.porcentaje para financiación.
     * Se aplica como multiplicador sobre el COSTO: COSTO × (1 + %FIN/100)
     * El concepto actúa como marcador, el valor real viene de proveedor.porcentaje.
     */
    FLAG_FINANCIACION_PROVEEDOR(Etapa.COSTO, NaturalezaConcepto.COSTO_PRODUCTO),

    // ===== ETAPA: MARGEN =====
    /**
     * Ajusta el margen sumando/restando puntos porcentuales: GAN.MIN.ML + porcentaje
     * El signo del porcentaje determina si aumenta (+) o reduce (-).
     * Ejemplo: Si GAN.MIN.ML = 60% y porcentaje = +25%, entonces ganancia = 85%
     */
    AJUSTE_MARGEN_PUNTOS(Etapa.MARGEN, NaturalezaConcepto.MARKUP),

    /**
     * Ajusta el margen proporcionalmente: GAN.MIN.ML × (1 + porcentaje/100)
     * El signo del porcentaje determina si aumenta (+) o reduce (-).
     * Ejemplo: Si GAN.MIN.ML = 60% y porcentaje = +10%, entonces ganancia = 66%
     */
    AJUSTE_MARGEN_PROPORCIONAL(Etapa.MARGEN, NaturalezaConcepto.MARKUP),

    /**
     * Flag: usa productoMargen.margenMinorista para el canal.
     * Solo actúa como marcador, el porcentaje del concepto se ignora.
     */
    FLAG_USAR_MARGEN_MINORISTA(Etapa.MARGEN, NaturalezaConcepto.MARKUP),

    /**
     * Flag: usa productoMargen.margenMayorista para el canal.
     * Solo actúa como marcador, el porcentaje del concepto se ignora.
     * Si un canal no tiene ninguno de estos conceptos, se usa margenMinorista por defecto.
     */
    FLAG_USAR_MARGEN_MAYORISTA(Etapa.MARGEN, NaturalezaConcepto.MARKUP),

    /**
     * Gasto después de calcular ganancia, pero antes de impuestos.
     * Multiplica: COSTO_CON_GANANCIA × (1 + concepto/100)
     * Ejemplo: LGELOG, LGEMKT se aplican con este tipo.
     */
    GASTO_POST_GANANCIA(Etapa.MARGEN, NaturalezaConcepto.INFLACION),

    // ===== ETAPA: IMPUESTOS =====
    /**
     * Flag: habilita la aplicación del IVA del producto para el canal.
     * Si existe este concepto, se aplica el IVA del producto.
     * Si NO existe, NO se aplica IVA (0%).
     * El porcentaje del concepto se ignora, solo actúa como habilitador.
     */
    FLAG_APLICAR_IVA(Etapa.IMPUESTOS, NaturalezaConcepto.IMPUESTO),

    /**
     * Impuesto que se suma al factor IMP: (IMP = 1 + IVA/100 + concepto/100)
     * Ejemplo: IIBB se suma directamente al factor IMP.
     */
    IMPUESTO_ADICIONAL(Etapa.IMPUESTOS, NaturalezaConcepto.IMPUESTO),

    /**
     * Gasto después de aplicar impuestos: costoConImp × (1 + %/100)
     */
    GASTO_POST_IMPUESTOS(Etapa.IMPUESTOS, NaturalezaConcepto.INFLACION),

    // ===== ETAPA: PRECIO =====
    /**
     * Flag: incluye mla.precioEnvio para el producto.
     * El concepto actúa como marcador, el valor real viene de mlas.precio_envio.
     */
    FLAG_INCLUIR_ENVIO(Etapa.PRECIO, NaturalezaConcepto.COSTO_VENTA),

    /**
     * Comisión que se aplica como divisor sobre el PVP: PVP / (1 - %/100)
     * Ejemplo: Comisión ML -13%
     * NOTA: Para conceptos de cuotas (con campo cuotas != NULL), se procesan de manera especial.
     */
    COMISION_SOBRE_PVP(Etapa.PRECIO, NaturalezaConcepto.COSTO_VENTA),

    /**
     * Flag: usa mla.comisionPorcentaje como comisión sobre PVP.
     * El concepto actúa como marcador, el valor real viene de mlas.comision_porcentaje.
     * Se suma a COMISION_SOBRE_PVP y se aplica como divisor: PVP / (1 - %/100)
     */
    FLAG_COMISION_ML(Etapa.PRECIO, NaturalezaConcepto.COSTO_VENTA),

    /**
     * Flag: usa mla.comisionPorcentaje como inflación sobre PVP.
     * Funciona igual que FLAG_COMISION_ML en el cálculo del PVP (divisor),
     * pero NO se cuenta como costo de venta en las métricas.
     * Efecto: infla el PVP sin reducir la ganancia.
     */
    FLAG_INFLACION_ML(Etapa.PRECIO, NaturalezaConcepto.INFLACION),

    /**
     * Inflación sobre PVP usando el porcentaje del concepto.
     * Se SUMA al mismo divisor que COMISION_SOBRE_PVP / FLAG_COMISION_ML /
     * FLAG_INFLACION_ML (un solo bucket para todos los gastos sobre PVP),
     * pero NO se cuenta como costo de venta en las métricas.
     * Variante con porcentaje propio de FLAG_INFLACION_ML (que toma el % del MLA).
     * Caso de uso: gasto que infla el precio para ML/marketplace pero
     * que el dueño no considera costo neto del canal (ej: KG_MAQ_EMB en KT GASTRO).
     */
    INFLACION_SOBRE_PVP(Etapa.PRECIO, NaturalezaConcepto.INFLACION),

    /**
     * Calcula el PVP basándose en el PVP del canal base (canalBase).
     * Si existe este concepto, se omite el cálculo normal y se usa:
     * PVP = PVP_CANAL_BASE × (1 + porcentaje/100)
     * Requiere que el canal tenga un canalBase configurado.
     * <p>
     * El factor afecta TANTO el PVP final como el ingreso del dueño
     * (los costosVenta e ingresoNeto del canal hijo se escalan con el mismo factor).
     * Ideal para canales propios donde el dueño captura el PVP completo.
     */
    CALCULO_SOBRE_CANAL_BASE(Etapa.PRECIO, NaturalezaConcepto.BASE),

    /**
     * Variante "reseller" de CALCULO_SOBRE_CANAL_BASE.
     * El factor afecta el PVP final del canal hijo, pero el ingreso del dueño
     * SE CORTA en este punto (no escala con factores no-RESELLER posteriores).
     * <p>
     * PVP_HIJO    = PVP_BASE × ∏(factores RESELLER) × ∏(factores no-RESELLER)
     * INGRESO_DUEÑO = PVP_BASE × ∏(factores RESELLER)
     * GANANCIA   = INGRESO_DUEÑO escalado - costoProducto
     * <p>
     * Caso de uso: el canal hijo es un re-vendedor que compra al precio
     * "PVP_BASE × factores RESELLER" (típicamente un descuento mayorista) y
     * agrega su propio markup (factores no-RESELLER) para vender al consumidor final.
     * Ejemplo: LIZZY GASTRO compra a mayorista×0.72 y vende a su cliente final ×1.5.
     */
    CALCULO_SOBRE_CANAL_BASE_RESELLER(Etapa.PRECIO, NaturalezaConcepto.BASE),

    // ===== ETAPA: POST_PRECIO =====
    /**
     * Recargo cupón: se aplica como divisor adicional después de GT3C.
     * PVP / (1 - RECARGO_CUPON/100)
     */
    RECARGO_CUPON(Etapa.POST_PRECIO, NaturalezaConcepto.COSTO_VENTA),

    /**
     * Descuento porcentual que reduce el PVP al final del cálculo.
     * PVP × (1 - DESCUENTO/100)
     * Ejemplo: DESCUENTO_MAQUINA
     */
    DESCUENTO_PORCENTUAL(Etapa.POST_PRECIO, NaturalezaConcepto.DESCUENTO),

    /**
     * Inflación aplicada como divisor sobre el PVP.
     * PVP / (1 - INFLACION/100)
     */
    INFLACION_DIVISOR(Etapa.POST_PRECIO, NaturalezaConcepto.INFLACION),

    /**
     * Gasto del dueño que NO se traslada al PVP pero SÍ cuenta como costo de venta.
     * - PVP: no participa en ningún divisor ni multiplicador (el precio NO cambia).
     * - Indicadores: se suma a costosVenta (reduce ingresoNetoVendedor y ganancia).
     * Caso de uso: comisión interna del vendedor que el dueño absorbe sin
     * inflar el precio al consumidor (Excel KT GASTRO: KG_COMVEND).
     */
    GASTO_FUERA_PVP(Etapa.POST_PRECIO, NaturalezaConcepto.COSTO_VENTA),

    /**
     * Flag: habilita la aplicación de precio inflado para el canal.
     * Si existe este concepto, se aplican los precios inflados de producto_canal_precio_inflado.
     * Si NO existe, NO se aplican precios inflados.
     * Solo actúa como habilitador, el porcentaje del concepto se ignora.
     */
    FLAG_APLICAR_PRECIO_INFLADO(Etapa.POST_PRECIO, NaturalezaConcepto.COSMETICO);

    private final Etapa etapa;
    private final NaturalezaConcepto naturalezaDefault;

    AplicaSobre(Etapa etapa, NaturalezaConcepto naturalezaDefault) {
        this.etapa = etapa;
        this.naturalezaDefault = naturalezaDefault;
    }

    /**
     * @return la etapa del cálculo de precio a la que pertenece este aplica_sobre.
     *         Asignada en la declaración del enum, así el compilador exige decisión
     *         cuando se agrega un valor nuevo.
     */
    public Etapa getEtapa() {
        return etapa;
    }

    /**
     * @return la naturaleza contable por defecto para conceptos con este aplica_sobre.
     *         Cada concepto puede sobreescribirla en su columna {@code naturaleza}
     *         (ver {@code ConceptoCalculo#getNaturalezaResolved()}).
     *         Asignada en la declaración del enum, así el compilador exige decisión
     *         cuando se agrega un valor nuevo.
     */
    public NaturalezaConcepto getNaturalezaDefault() {
        return naturalezaDefault;
    }

    /**
     * @return true si este aplica_sobre opera sobre el PVP del canal base
     *         (cualquiera de las variantes CALCULO_SOBRE_CANAL_BASE).
     */
    public boolean esCalculoSobreCanalBase() {
        return this == CALCULO_SOBRE_CANAL_BASE || this == CALCULO_SOBRE_CANAL_BASE_RESELLER;
    }

    /**
     * @return true si este aplica_sobre es un flag (marcador on/off).
     *         Los flags ignoran el porcentaje del concepto — el valor real
     *         viene del producto, MLA, proveedor, etc., o el flag solo habilita
     *         una funcionalidad sin valor numérico. El porcentaje debe quedar null.
     */
    public boolean esFlag() {
        return name().startsWith("FLAG_");
    }
}
