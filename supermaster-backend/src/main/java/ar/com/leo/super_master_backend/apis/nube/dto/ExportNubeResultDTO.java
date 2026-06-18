package ar.com.leo.super_master_backend.apis.nube.dto;

import java.util.List;

public record ExportNubeResultDTO(int creados, List<String> yaExistian, List<String> errores) {}
