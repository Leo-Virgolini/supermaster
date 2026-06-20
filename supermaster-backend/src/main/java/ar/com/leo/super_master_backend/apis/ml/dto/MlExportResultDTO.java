package ar.com.leo.super_master_backend.apis.ml.dto;

import java.util.List;

public record MlExportResultDTO(int creados, List<String> actualizados, List<String> yaExistian, List<String> errores, List<String> advertencias) {}
