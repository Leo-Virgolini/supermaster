package ar.com.leo.super_master_backend.dominio.producto.calculo.service;

import ar.com.leo.super_master_backend.dominio.common.dto.ProcesoMasivoEstadoDTO;
import ar.com.leo.super_master_backend.dominio.producto.calculo.dto.*;
import ar.com.leo.super_master_backend.dominio.producto.dto.CanalPreciosDTO;

import java.math.BigDecimal;
import java.util.List;

public interface CalculoPrecioService {

    /**
     * Calcula el precio de un producto para un canal (sin persistir).
     * 
     * @param productoId ID del producto
     * @param canalId ID del canal
     * @param numeroCuotas Número de cuotas (opcional). Si es null o 1, no se aplican gastos de cuotas.
     *                     Si se especifica (ej: 3, 6, 12), se aplica el concepto de gasto correspondiente.
     */
    PrecioCalculadoDTO calcularPrecioCanal(Integer productoId, Integer canalId, Integer numeroCuotas);

    /**
     * Calcula el precio de un producto para un canal con un costo de envío personalizado (sin persistir).
     * Útil para simular el PVP con un costo de envío diferente al almacenado en MLA.
     *
     * @param productoId ID del producto
     * @param canalId ID del canal
     * @param numeroCuotas Número de cuotas (opcional)
     * @param precioEnvioOverride Costo de envío a usar en lugar del almacenado en mla.precio_envio
     * @return PrecioCalculadoDTO con el precio simulado
     */
    PrecioCalculadoDTO calcularPrecioCanalConEnvio(Integer productoId, Integer canalId, Integer numeroCuotas, BigDecimal precioEnvioOverride);

    /**
     * Igual que {@link #calcularPrecioCanalConEnvio(Integer, Integer, Integer, BigDecimal)} pero
     * además permite pasar una comisión ML por parámetro. Útil para calcular el PVP ANTES de
     * que el producto tenga un MLA (cuando aún no se puede leer {@code mla.comisionPorcentaje}).
     * Si {@code comisionMlOverride} es null, el motor lee la comisión del MLA del producto
     * (comportamiento idéntico al método de 4 parámetros).
     *
     * @param productoId       ID del producto
     * @param canalId          ID del canal
     * @param numeroCuotas     Número de cuotas (opcional)
     * @param precioEnvioOverride Costo de envío a usar en lugar del almacenado en mla.precio_envio
     * @param comisionMlOverride  Porcentaje de comisión ML a usar en FLAG_COMISION_ML; null = leer del MLA
     * @return PrecioCalculadoDTO con el precio simulado
     */
    PrecioCalculadoDTO calcularPrecioCanalConEnvio(Integer productoId, Integer canalId,
            Integer numeroCuotas, BigDecimal precioEnvioOverride, BigDecimal comisionMlOverride);

    /**
     * Calcula y además guarda/actualiza el registro en producto_canal_precios.
     *
     * @param productoId ID del producto
     * @param canalId ID del canal
     * @param numeroCuotas Número de cuotas (opcional). Si es null o 1, no se aplican gastos de cuotas.
     */
    PrecioCalculadoDTO recalcularYGuardarPrecioCanal(Integer productoId, Integer canalId, Integer numeroCuotas);

    /**
     * Obtiene la fórmula del cálculo de precio para un producto, canal y número de cuotas.
     * Muestra paso a paso cómo se calcula el precio con los valores reales.
     *
     * @param productoId ID del producto
     * @param canalId ID del canal
     * @param numeroCuotas Número de cuotas (opcional)
     * @return DTO con la fórmula desglosada paso a paso
     */
    FormulaCalculoDTO obtenerFormulaCalculo(Integer productoId, Integer canalId, Integer numeroCuotas);

    /**
     * Simula la fórmula de cálculo para un producto hipotético (sin persistir nada).
     * Construye entidades transitorias en memoria con los atributos del input y ejecuta
     * el motor de cálculo, devolviendo la fórmula paso a paso como si el producto existiera.
     *
     * Útil para responder "¿qué precio tendría un producto con estos atributos en este canal?"
     * sin necesidad de dar de alta el producto ni persistir margen.
     */
    FormulaCalculoDTO simularFormulaCalculo(SimulacionPrecioInputDTO input);

    /**
     * Igual que {@link #simularFormulaCalculo}, pero además devuelve los indicadores
     * calculados (PVP, ganancia, costos venta, ingreso neto, márgenes, markup) para
     * que el frontend pueda mostrar el mismo conjunto de métricas que el Monitor de Precios.
     */
    SimulacionResultadoDTO simularPrecioCompleto(SimulacionPrecioInputDTO input);

    /**
     * Calcula y guarda los precios de un producto para todas las cuotas configuradas en el canal.
     * Incluye contado (null) y todas las cuotas de canal_concepto_cuota.
     *
     * @param productoId ID del producto
     * @param canalId ID del canal
     * @return DTO con el canal y sus precios calculados y guardados
     */
    CanalPreciosDTO recalcularYGuardarPrecioCanalTodasCuotas(Integer productoId, Integer canalId);

    /**
     * Calcula y guarda precios de un producto para un canal.
     * Si cuotas es null, calcula para todas las cuotas configuradas en el canal.
     * Si cuotas tiene valor, calcula solo para esa cantidad de cuotas.
     *
     * @param productoId ID del producto
     * @param canalId ID del canal
     * @param cuotas Número de cuotas (opcional). Si es null, calcula todas.
     * @return DTO con el canal y sus precios calculados y guardados
     */
    CanalPreciosDTO recalcularYGuardar(Integer productoId, Integer canalId, Integer cuotas);

    /**
     * Calcula y guarda precios de un producto en todos sus canales configurados.
     *
     * @param productoId ID del producto
     * @return Lista de DTOs con los precios de cada canal
     */
    List<CanalPreciosDTO> recalcularProductoTodosCanales(Integer productoId);

    /**
     * Calcula y guarda precios de un producto en todos sus canales configurados,
     * pero solo para las cuotas especificadas.
     *
     * @param productoId ID del producto
     * @param cuotas Número de cuotas a calcular
     * @return Lista de DTOs con los precios de cada canal (solo la cuota indicada)
     */
    List<CanalPreciosDTO> recalcularProductoTodosCanales(Integer productoId, Integer cuotas);

    /**
     * Calcula y guarda precios de TODOS los productos en TODOS los canales.
     * Operación masiva que puede tomar tiempo considerable.
     *
     * @return Resultado con total de precios calculados y productos ignorados
     */
    RecalculoMasivoResultDTO recalcularTodos();

    /**
     * Activa un caché ThreadLocal de contexto canal-constante (reglas, conceptos, cuotas,
     * canalBase) para acelerar el recálculo: estas queries dependen solo del canal, así que
     * se ejecutan una sola vez en lugar de repetirse por producto/cuota. Los canales se
     * memoizan on-demand; {@code canalId} se precarga (puede ser {@code null} para solo
     * activar el caché y dejar que se llene a medida que se procesan varios canales).
     *
     * <p>Es ANIDABLE (ref-counted): un loop externo puede mantener el caché vivo mientras
     * llama operaciones que también lo abren/cierran. Debe cerrarse SIEMPRE con un
     * {@link #limpiarCacheContextoCanal()} balanceado en finally.
     */
    void iniciarCacheContextoCanal(Integer canalId);

    /** Cierra un nivel del caché de contexto de canal; libera el caché en el nivel 0. Llamar en finally. */
    void limpiarCacheContextoCanal();

    /**
     * Callback de avance del recálculo de un canal completo. Permite reflejar el progreso
     * (productos procesados / total) en el badge del header durante recálculos largos.
     */
    @FunctionalInterface
    interface AvanceCanalCallback {
        void onAvance(int procesados, int total, int ok, int errores);
    }

    /**
     * Recalcula TODO el catálogo en un canal usando la maquinaria de precarga + batch del
     * masivo (precarga de productos/precios/inflados en memoria, persistencia diferida al
     * commit). Mucho más rápido que el camino producto-por-producto. Pensado para canales
     * BASE (sin canalBase): el cálculo no recursa, así que el loop no hace queries y la
     * persistencia se agrupa en un único flush. Devuelve la cantidad de productos OK.
     */
    int recalcularCanalCompletoBatch(Integer canalId, AvanceCanalCallback onAvance);

    // Recálculo masivo asincrónico con progreso
    boolean iniciarRecalculoMasivo();
    ProcesoMasivoEstadoDTO obtenerEstadoRecalculo();
    void cancelarRecalculo();
    RecalculoMasivoResultDTO obtenerResultadoRecalculo();
}