# Completar campos de canal (EAN a Nube, garantía ML) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mandar el EAN del producto a Tienda Nube como `barcode` del variant, y enviar a Mercado Libre la garantía fija "Sin garantía".

**Architecture:** Dos cambios aislados en los payload builders del alta de cada canal, cada uno con su test (TDD). Sin inputs nuevos en el modal; usan datos existentes (`ean`) o un valor fijo.

**Tech Stack:** Java 25 / Spring Boot 4 / Maven / JUnit 5 / AssertJ.

## Global Constraints

- Backend: tests con `mvn -o test` (offline) desde `supermaster-backend`; usar el `mvn` del PATH (NO `mvnw`, falla por red).
- No tocar peso/dimensiones/stock de Nube (quedan hardcodeados/vacíos como están) ni `free_shipping` (queda `false`) — decisión confirmada del usuario.
- ML garantía "Sin garantía": va en `sale_terms` como `{"id":"WARRANTY_TYPE","value_id":"6150835","value_name":"Sin garantía"}` ([doc ML](https://developers.mercadolibre.com.ar/publica-productos)). Valor fijo.
- Nube `barcode`: se manda solo si el EAN es válido (reusar `MlItemPayloadBuilder.esGtinValido`).
- Commits en español, estilo `tipo(scope): ...`, cerrando con:
  `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

## Estructura de archivos

- Modificar `apis/ml/service/MlItemPayloadBuilder.java` — hacer `esGtinValido` público (C1) y agregar `sale_terms` (C2).
- Modificar `apis/nube/service/NubeProductoPayloadBuilder.java` — agregar `barcode` al variant (C1).
- Tests: `MlItemPayloadBuilderTest.java` (C2), `NubeProductoPayloadBuilderTest.java` (C1).

---

### Task 1: EAN → `barcode` en el variant de Nube

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlItemPayloadBuilder.java` (visibilidad de `esGtinValido`, ~línea 117)
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeProductoPayloadBuilder.java:28-43` (variant)
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/service/NubeProductoPayloadBuilderTest.java`

**Interfaces:**
- Consumes: `MlItemPayloadBuilder.esGtinValido(String)` (se hace público en esta tarea); `Producto.getEan()`.
- Produces: el variant de Nube incluye `barcode` cuando el EAN es válido.

- [ ] **Step 1: Escribir el test que falla**

En `NubeProductoPayloadBuilderTest.java`, agregar (el helper `base()` ya existe; setea sku/título/costo):

```java
    @Test
    @SuppressWarnings("unchecked")
    void conEanValido_incluyeBarcodeEnElVariant() {
        Producto p = base();
        p.setEan("1234567890128"); // EAN-13 válido
        Map<String, Object> payload = NubeProductoPayloadBuilder.construir(p, new BigDecimal("1500.00"), null, null, null);
        Map<String, Object> v = ((List<Map<String, Object>>) payload.get("variants")).get(0);
        assertThat(v.get("barcode")).isEqualTo("1234567890128");
    }

    @Test
    @SuppressWarnings("unchecked")
    void conEanInvalidoOVacio_noIncluyeBarcode() {
        Producto p = base();
        p.setEan("1178911569"); // 10 dígitos: inválido
        Map<String, Object> v1 = ((List<Map<String, Object>>) NubeProductoPayloadBuilder
                .construir(p, new BigDecimal("1500.00"), null, null, null).get("variants")).get(0);
        assertThat(v1).doesNotContainKey("barcode");

        Producto sinEan = base(); // ean null
        Map<String, Object> v2 = ((List<Map<String, Object>>) NubeProductoPayloadBuilder
                .construir(sinEan, new BigDecimal("1500.00"), null, null, null).get("variants")).get(0);
        assertThat(v2).doesNotContainKey("barcode");
    }
```

- [ ] **Step 2: Correr el test y verlo fallar**

Run: `cd supermaster-backend && mvn -o test -Dtest=NubeProductoPayloadBuilderTest`
Expected: FAIL — `conEanValido...` no encuentra `barcode` (hoy no se manda).

> Si además falla a compilar por visibilidad de `esGtinValido`, es esperado hasta el Step 3.

- [ ] **Step 3: Hacer público `esGtinValido`**

En `MlItemPayloadBuilder.java`, cambiar la firma del método `esGtinValido` (hoy package-private):

```java
    static boolean esGtinValido(String codigo) {
```

por:

```java
    public static boolean esGtinValido(String codigo) {
```

(No cambia su cuerpo; el test de ML que ya lo usa sigue compilando.)

- [ ] **Step 4: Agregar `barcode` al variant de Nube**

En `NubeProductoPayloadBuilder.java`, agregar el import:

```java
import ar.com.leo.super_master_backend.apis.ml.service.MlItemPayloadBuilder;
```

Y dentro de `construir`, justo después de `variant.put("sku", p.getSku());` (línea 29), agregar:

```java
        // Código de barras: el EAN del producto, solo si es un GTIN/EAN válido (mismo criterio que ML).
        if (MlItemPayloadBuilder.esGtinValido(p.getEan())) variant.put("barcode", p.getEan().trim());
```

- [ ] **Step 5: Correr el test y verlo pasar**

Run: `cd supermaster-backend && mvn -o test -Dtest=NubeProductoPayloadBuilderTest,MlItemPayloadBuilderTest`
Expected: PASS (ambas clases; se corre también la de ML para confirmar que hacer público `esGtinValido` no rompió nada).

- [ ] **Step 6: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlItemPayloadBuilder.java \
        supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeProductoPayloadBuilder.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/service/NubeProductoPayloadBuilderTest.java
git commit -m "feat(nube): manda el EAN como barcode del variant (si es válido)"
```

---

### Task 2: Garantía "Sin garantía" en ML

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlItemPayloadBuilder.java:27-37` (método `construir`, ensamblado del payload)
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/MlItemPayloadBuilderTest.java`

**Interfaces:**
- Produces: el payload de alta de ML (`construir`) incluye `sale_terms` con `WARRANTY_TYPE = "Sin garantía"`.

- [ ] **Step 1: Escribir el test que falla**

En `MlItemPayloadBuilderTest.java`, agregar (el helper `base()` ya existe; devuelve un Producto con sku/marca):

```java
    @Test
    @SuppressWarnings("unchecked")
    void construir_incluyeGarantiaSinGarantia() {
        var payload = MlItemPayloadBuilder.construir(base(), "MLA1", new BigDecimal("100"), 0, List.of(), "Fam");
        List<Map<String, Object>> saleTerms = (List<Map<String, Object>>) payload.get("sale_terms");
        assertThat(saleTerms).anySatisfy(t -> {
            assertThat(t.get("id")).isEqualTo("WARRANTY_TYPE");
            assertThat(t.get("value_id")).isEqualTo("6150835");
            assertThat(t.get("value_name")).isEqualTo("Sin garantía");
        });
    }
```

- [ ] **Step 2: Correr el test y verlo fallar**

Run: `cd supermaster-backend && mvn -o test -Dtest=MlItemPayloadBuilderTest#construir_incluyeGarantiaSinGarantia`
Expected: FAIL — `sale_terms` es null (no se manda hoy).

- [ ] **Step 3: Agregar `sale_terms` al payload**

En `MlItemPayloadBuilder.construir`, después de `payload.put("condition", "new");` (línea 36), agregar:

```java
        // Garantía fija: "Sin garantía" (ML la espera en sale_terms).
        payload.put("sale_terms", List.of(Map.of(
                "id", "WARRANTY_TYPE", "value_id", "6150835", "value_name", "Sin garantía")));
```

(`List` y `Map` ya están importados en el archivo.)

- [ ] **Step 4: Correr el test y verlo pasar**

Run: `cd supermaster-backend && mvn -o test -Dtest=MlItemPayloadBuilderTest`
Expected: PASS (toda la clase).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlItemPayloadBuilder.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/MlItemPayloadBuilderTest.java
git commit -m "feat(ml): envía garantía 'Sin garantía' en sale_terms"
```

---

## Notas de cierre

- Alcance: solo el **alta** de cada canal (los payload builders). El `barcode` y la garantía quedan seteados al crear; las actualizaciones (PUT de precio en Nube, PUT de atributos en ML) no los re-envían — aceptable porque son datos estables que se fijan en la publicación.
- Tras ambas tareas: `cd supermaster-backend && mvn -o test` (suite completa verde).
