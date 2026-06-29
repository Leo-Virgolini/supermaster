package ar.com.leo.super_master_backend.dominio.imagen.service;

/** Resultado de generar una carátula: la imagen cruda usada y la generada (bytes en memoria, sin guardar). */
public record GeneracionCaratula(byte[] cruda, String crudaNombre, byte[] generada) {}
