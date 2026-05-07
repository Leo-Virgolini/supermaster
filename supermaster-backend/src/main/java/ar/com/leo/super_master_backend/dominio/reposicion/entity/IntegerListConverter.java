package ar.com.leo.super_master_backend.dominio.reposicion.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Converter
public class IntegerListConverter implements AttributeConverter<List<Integer>, String> {

    @Override
    public String convertToDatabaseColumn(List<Integer> list) {
        if (list == null || list.isEmpty()) return null;
        return list.stream()
                .map(String::valueOf)
                .reduce((a, b) -> a + "," + b)
                .orElse(null);
    }

    @Override
    public List<Integer> convertToEntityAttribute(String s) {
        if (s == null || s.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.stream(s.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .toList());
    }
}
