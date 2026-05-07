package ar.com.leo.super_master_backend.config;

/**
 * Expresiones SpEL listas para usar en {@code @PreAuthorize}.
 * <p>
 * Uso: {@code @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)}
 * <p>
 * Los nombres (sin el wrapper {@code hasAuthority('...')}) deben coincidir
 * exactamente con la columna {@code nombre} de la tabla {@code supermaster.permisos}.
 */
public final class Permisos {

    private Permisos() {}

    public static final String AUDITORIA_VER = "hasAuthority('AUDITORIA_VER')";

    public static final String CANALES_VER = "hasAuthority('CANALES_VER')";
    public static final String CANALES_EDITAR = "hasAuthority('CANALES_EDITAR')";

    public static final String CATALOGOS_PDF_VER = "hasAuthority('CATALOGOS_PDF_VER')";
    public static final String CATALOGOS_PDF_EDITAR = "hasAuthority('CATALOGOS_PDF_EDITAR')";

    public static final String CONFIGURACION_VER = "hasAuthority('CONFIGURACION_VER')";
    public static final String CONFIGURACION_EDITAR = "hasAuthority('CONFIGURACION_EDITAR')";

    public static final String ESTADISTICAS_VER = "hasAuthority('ESTADISTICAS_VER')";

    public static final String EXCEL_VER = "hasAuthority('EXCEL_VER')";
    public static final String EXCEL_EDITAR = "hasAuthority('EXCEL_EDITAR')";

    public static final String INTEGRACIONES_VER = "hasAuthority('INTEGRACIONES_VER')";
    public static final String INTEGRACIONES_EDITAR = "hasAuthority('INTEGRACIONES_EDITAR')";

    public static final String MAESTROS_VER = "hasAuthority('MAESTROS_VER')";
    public static final String MAESTROS_EDITAR = "hasAuthority('MAESTROS_EDITAR')";

    public static final String MLAS_VER = "hasAuthority('MLAS_VER')";
    public static final String MLAS_EDITAR = "hasAuthority('MLAS_EDITAR')";

    public static final String ORDENES_COMPRA_VER = "hasAuthority('ORDENES_COMPRA_VER')";
    public static final String ORDENES_COMPRA_EDITAR = "hasAuthority('ORDENES_COMPRA_EDITAR')";

    public static final String PRECIOS_VER = "hasAuthority('PRECIOS_VER')";
    public static final String PRECIOS_EDITAR = "hasAuthority('PRECIOS_EDITAR')";

    public static final String PRODUCTOS_VER = "hasAuthority('PRODUCTOS_VER')";
    public static final String PRODUCTOS_EDITAR = "hasAuthority('PRODUCTOS_EDITAR')";

    public static final String REPOSICION_VER = "hasAuthority('REPOSICION_VER')";
    public static final String REPOSICION_EDITAR = "hasAuthority('REPOSICION_EDITAR')";

    public static final String USUARIOS_VER = "hasAuthority('USUARIOS_VER')";
    public static final String USUARIOS_EDITAR = "hasAuthority('USUARIOS_EDITAR')";
}
