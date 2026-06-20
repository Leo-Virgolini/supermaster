package ar.com.leo.super_master_backend.dominio.common.dto;

import java.util.List;

/** Resultado de exportar/sincronizar productos a un canal (Nube, ML): cuántos creados/actualizados y los detalles. */
public record ExportCanalResultDTO(int creados, List<String> actualizados, List<String> yaExistian, List<String> errores, List<String> advertencias) {}
