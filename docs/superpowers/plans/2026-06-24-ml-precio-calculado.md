# Precio calculado real para alta/actualización en ML — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publicar en Mercado Libre el PVP real del canal (comisión + envío iterativo), calculado ANTES de crear la publicación y enviado en un solo POST, en lugar de `costo*5`.

**Architecture:** El motor de cálculo aprende a recibir la comisión por parámetro (override), igual que ya recibe el envío. `MercadoLibreService` calcula el PVP final estabilizado sin necesitar el MLA (la comisión sale de `listing_prices` read-only y el envío del fallback `dimensions=`), y el alta/actualización publica ese precio. La persistencia de `mlas` (envío/comisión) no se modifica.

**Tech Stack:** Java 25, Spring Boot 4, Maven, JUnit 5 + AssertJ, Jackson.

## Global Constraints

- **No romper el cálculo actual de `mlas`**: `obtenerCostoVenta(mlaCode)` y `calcularCostoEnvioGratis(mlaCode)` conservan comportamiento y persistencia. El cálculo previo NO escribe en `mlas`.
- **Override aditivo**: con `comisionMlOverride = null`, el motor se comporta EXACTAMENTE como hoy.
- **Redondeo**: PVP a entero con `setScale(0, RoundingMode.HALF_UP)`.
- **Fallo de cálculo** → abortar el alta/actualización con `ResultadoAltaMl.error("no se pudo calcular el precio del canal ML: <motivo>")`.
- **Tests offline**: correr con `mvn -o test -Dtest=...` desde `supermaster-backend` (el wrapper falla por red en sandbox).
- **DTOs record**: agregar componentes rompe constructores posicionales en tests; verificar con `mvn -o test`, no solo `compile`.
- Canal ML: constante `CANAL_ML = "ML"`. Precio a publicar = `PrecioCalculadoDTO.pvp()` redondeado (coherente con el valor que estabiliza el bucle).

---

### Task 1: Override de comisión en el motor de cálculo

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/calculo/service/CalculoPrecioServiceImpl.java`
- Modify (interface): `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/calculo/service/CalculoPrecioService.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/calculo/service/CalculoPrecioComisionOverrideTest.java`

**Interfaces:**
- Produces: `PrecioCalculadoDTO calcularPrecioCanalConEnvio(Integer productoId, Integer canalId, Integer numeroCuotas, BigDecimal precioEnvioOverride, BigDecimal comisionMlOverride)` — nueva sobrecarga. La firma de 4 parámetros se mantiene y delega con `comisionMlOverride = null`.

- [ ] **Step 1: Escribir el test que falla**

Crear `CalculoPrecioComisionOverrideTest.java`. Usa un producto SIN MLA y un canal ML con concepto `FLAG_COMISION_ML`. Verifica que con override la comisión entra al PVP, y que la firma vieja (sin override) NO aplica comisión cuando no hay MLA (comportamiento actual).

```java
package ar.com.leo.super_master_backend.dominio.producto.calculo.service;

import ar.com.leo.super_master_backend.dominio.producto.calculo.dto.PrecioCalculadoDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CalculoPrecioComisionOverrideTest {

    @Autowired CalculoPrecioService service;

    // IDs de fixtures del entorno de test (ajustar a los seeds reales del proyecto).
    private static final Integer PRODUCTO_SIN_MLA = 1;
    private static final Integer CANAL_ML = 1;

    @Test
    void override_comision_aumenta_el_pvp_respecto_de_sin_comision() {
        PrecioCalculadoDTO sin = service.calcularPrecioCanalConEnvio(PRODUCTO_SIN_MLA, CANAL_ML, 0, BigDecimal.ZERO, null);
        PrecioCalculadoDTO con = service.calcularPrecioCanalConEnvio(PRODUCTO_SIN_MLA, CANAL_ML, 0, BigDecimal.ZERO, new BigDecimal("13"));
        assertThat(con.pvp()).isGreaterThan(sin.pvp());
    }
}
```

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `mvn -o test -Dtest=CalculoPrecioComisionOverrideTest -pl . ` (desde `supermaster-backend`)
Expected: FAIL de compilación — la sobrecarga de 5 parámetros no existe.

- [ ] **Step 3: Agregar la sobrecarga en la interfaz**

En `CalculoPrecioService.java`, junto al método existente `calcularPrecioCanalConEnvio(Integer, Integer, Integer, BigDecimal)`, agregar:

```java
PrecioCalculadoDTO calcularPrecioCanalConEnvio(Integer productoId, Integer canalId,
        Integer numeroCuotas, BigDecimal precioEnvioOverride, BigDecimal comisionMlOverride);
```

- [ ] **Step 4: Implementar en `CalculoPrecioServiceImpl`**

Reemplazar el método actual (línea ~287-299) para que la firma vieja delegue en la nueva, y propagar el override hasta `calcularPrecioUnificado`:

```java
@Override
@Transactional(readOnly = true, noRollbackFor = {NotFoundException.class, BadRequestException.class})
public PrecioCalculadoDTO calcularPrecioCanalConEnvio(Integer productoId, Integer canalId,
        Integer numeroCuotas, BigDecimal precioEnvioOverride) {
    return calcularPrecioCanalConEnvio(productoId, canalId, numeroCuotas, precioEnvioOverride, null);
}

@Override
@Transactional(readOnly = true, noRollbackFor = {NotFoundException.class, BadRequestException.class})
public PrecioCalculadoDTO calcularPrecioCanalConEnvio(Integer productoId, Integer canalId,
        Integer numeroCuotas, BigDecimal precioEnvioOverride, BigDecimal comisionMlOverride) {
    Producto producto = obtenerProducto(productoId);
    if (!productoAplicaAlCanal(producto, canalId)) {
        throw new BadRequestException("El producto no aplica al canal según sus reglas de exclusión");
    }
    ContextoCalculo ctx = prepararContexto(producto, canalId, numeroCuotas);
    return calcularPrecioUnificado(ctx.producto(), ctx.productoMargen(), ctx.conceptosCanal(),
            numeroCuotas, canalId, ctx.canal(), precioEnvioOverride, comisionMlOverride);
}
```

- [ ] **Step 5: Agregar el parámetro a `calcularPrecioUnificado` y usarlo**

En la firma de `calcularPrecioUnificado` (línea ~423-430) agregar al final `BigDecimal comisionMlOverride`. En el bloque FLAG_COMISION_ML (líneas ~619-624), reemplazar por:

```java
List<CanalConcepto> conceptosComisionMl = conceptosPorTipo.getOrDefault(AplicaSobre.FLAG_COMISION_ML, List.of());
if (!conceptosComisionMl.isEmpty()) {
    BigDecimal comisionAplicar;
    if (comisionMlOverride != null) {
        comisionAplicar = comisionMlOverride;
    } else if (producto.getMla() != null && producto.getMla().getComisionPorcentaje() != null) {
        comisionAplicar = producto.getMla().getComisionPorcentaje();
    } else {
        comisionAplicar = BigDecimal.ZERO;
    }
    if (comisionAplicar.compareTo(BigDecimal.ZERO) > 0) {
        gastosSobrePVPTotal = gastosSobrePVPTotal.add(comisionAplicar);
    }
}
```

- [ ] **Step 6: Actualizar los call sites internos de `calcularPrecioUnificado`**

Agregar `null` como último argumento en cada llamada que NO sea la del Step 4. Líneas aproximadas: 275, 346, 2465, 2781, 3313, 3473. Ejemplo (línea 2465):

```java
PrecioCalculadoDTO indicadores = calcularPrecioUnificado(producto, productoMargen, conceptosCanal, input.cuotas(), input.canalId(), canal, null, null);
```

- [ ] **Step 7: Correr el test y verificar que pasa**

Run: `mvn -o test -Dtest=CalculoPrecioComisionOverrideTest` (desde `supermaster-backend`)
Expected: PASS. Si los IDs de fixture no existen en el entorno de test, ajustarlos a un producto sin MLA y al canal `ML` reales de los seeds.

- [ ] **Step 8: Correr la regresión del motor**

Run: `mvn -o test -Dtest=CalculoPrecio*Test`
Expected: PASS — confirma que el comportamiento con override null es idéntico al actual.

- [ ] **Step 9: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/calculo/service/CalculoPrecioService.java \
        supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/calculo/service/CalculoPrecioServiceImpl.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/calculo/service/CalculoPrecioComisionOverrideTest.java
git commit -m "feat(calculo): override de comision ML en calcularPrecioCanalConEnvio"
```

---

### Task 2: Helper de redondeo a entero

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/MlRedondeoPrecioTest.java`

**Interfaces:**
- Produces: `static BigDecimal redondearPrecioMl(BigDecimal pvp)` — entero `HALF_UP`; null-safe (devuelve null si la entrada es null).

- [ ] **Step 1: Escribir el test que falla**

```java
package ar.com.leo.super_master_backend.apis.ml.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class MlRedondeoPrecioTest {
    @Test
    void redondea_a_entero_half_up() {
        assertThat(MercadoLibreService.redondearPrecioMl(new BigDecimal("18340.49"))).isEqualByComparingTo("18340");
        assertThat(MercadoLibreService.redondearPrecioMl(new BigDecimal("18340.50"))).isEqualByComparingTo("18341");
        assertThat(MercadoLibreService.redondearPrecioMl(new BigDecimal("18340.99"))).isEqualByComparingTo("18341");
    }

    @Test
    void null_devuelve_null() {
        assertThat(MercadoLibreService.redondearPrecioMl(null)).isNull();
    }
}
```

- [ ] **Step 2: Correr y verificar que falla**

Run: `mvn -o test -Dtest=MlRedondeoPrecioTest`
Expected: FAIL — método inexistente.

- [ ] **Step 3: Implementar el helper**

En `MercadoLibreService`, agregar:

```java
/** Redondea el PVP al entero más cercano (sin centavos) para publicar en ML. */
static BigDecimal redondearPrecioMl(BigDecimal pvp) {
    return pvp == null ? null : pvp.setScale(0, java.math.RoundingMode.HALF_UP);
}
```

- [ ] **Step 4: Correr y verificar que pasa**

Run: `mvn -o test -Dtest=MlRedondeoPrecioTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/MlRedondeoPrecioTest.java
git commit -m "feat(ml): helper de redondeo de precio a entero"
```

---

### Task 3: Núcleo puro del bucle iterativo de envío

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/EnvioEstabilizador.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/EnvioEstabilizadorTest.java`

**Interfaces:**
- Produces:
  ```java
  record ResultadoEstabilizacion(BigDecimal pvp, BigDecimal costoEnvioSinIva,
          BigDecimal costoEnvioConIva, int iteraciones) {}

  // pvpFn: costoEnvioSinIva -> pvp del motor.
  // envioConIvaFn: pvp -> costo de envío CON IVA (API ML o tier).
  static ResultadoEstabilizacion estabilizar(
      java.util.function.Function<BigDecimal, BigDecimal> pvpFn,
      java.util.function.Function<BigDecimal, BigDecimal> envioConIvaFn,
      BigDecimal divisorIva, int maxIteraciones)
  ```

Esta clase extrae la matemática pura (PVP ↔ envío, estabilización y detección de oscilación) hoy embebida en `calcularCostoEnvioGratis` (líneas 236-344), SIN red ni BD. El `tipoCalculo`/`status` (API ML vs tier) NO entran al núcleo: los determina el caller según el umbral. El record tiene exactamente 4 campos.

- [ ] **Step 1: Escribir el test que falla**

```java
package ar.com.leo.super_master_backend.apis.ml.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class EnvioEstabilizadorTest {

    private static final BigDecimal DIV_IVA = new BigDecimal("1.21");

    @Test
    void estabiliza_cuando_el_envio_se_repite() {
        // pvp = base + envioSinIva*2 ; envioConIva fijo 1210 (sinIva 1000).
        var r = EnvioEstabilizador.estabilizar(
                envioSinIva -> new BigDecimal("10000").add(envioSinIva.multiply(new BigDecimal("2"))),
                pvp -> new BigDecimal("1210"),
                DIV_IVA, 10);
        assertThat(r.costoEnvioConIva()).isEqualByComparingTo("1210");
        assertThat(r.costoEnvioSinIva()).isEqualByComparingTo("1000.00");
        assertThat(r.iteraciones()).isLessThanOrEqualTo(10);
    }

    @Test
    void corta_por_oscilacion_y_toma_el_mayor() {
        // Alterna 1000/2000 con IVA según el PVP -> oscila; debe cortar tomando el mayor.
        var r = EnvioEstabilizador.estabilizar(
                envioSinIva -> envioSinIva.compareTo(new BigDecimal("900")) > 0
                        ? new BigDecimal("5000") : new BigDecimal("20000"),
                pvp -> pvp.compareTo(new BigDecimal("12000")) > 0
                        ? new BigDecimal("1000") : new BigDecimal("2000"),
                DIV_IVA, 10);
        assertThat(r.costoEnvioConIva()).isEqualByComparingTo("2000");
    }
}
```

- [ ] **Step 2: Correr y verificar que falla**

Run: `mvn -o test -Dtest=EnvioEstabilizadorTest`
Expected: FAIL — clase inexistente.

- [ ] **Step 3: Implementar `EnvioEstabilizador`**

Portar la lógica de las líneas 236-344 de `calcularCostoEnvioGratis` a un método estático puro:

```java
package ar.com.leo.super_master_backend.apis.ml.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Estabilización iterativa PVP ↔ costo de envío. Pura: sin red ni BD. */
final class EnvioEstabilizador {
    private EnvioEstabilizador() {}

    record ResultadoEstabilizacion(BigDecimal pvp, BigDecimal costoEnvioSinIva,
            BigDecimal costoEnvioConIva, int iteraciones) {}

    static ResultadoEstabilizacion estabilizar(
            Function<BigDecimal, BigDecimal> pvpFn,
            Function<BigDecimal, BigDecimal> envioConIvaFn,
            BigDecimal divisorIva, int maxIteraciones) {
        BigDecimal costoEnvioSinIva = BigDecimal.ZERO;
        BigDecimal costoEnvioConIva = BigDecimal.ZERO;
        BigDecimal pvp = BigDecimal.ZERO;
        int it = 0;
        List<BigDecimal> vistos = new ArrayList<>();
        while (it < maxIteraciones) {
            it++;
            pvp = pvpFn.apply(costoEnvioSinIva);
            BigDecimal nuevoConIva = envioConIvaFn.apply(pvp);
            if (nuevoConIva.compareTo(costoEnvioConIva) == 0) {
                break;
            }
            boolean oscila = vistos.stream().anyMatch(v -> v.compareTo(nuevoConIva) == 0);
            if (oscila) {
                BigDecimal ciclo = nuevoConIva.max(costoEnvioConIva);
                costoEnvioConIva = ciclo;
                costoEnvioSinIva = ciclo.compareTo(BigDecimal.ZERO) > 0
                        ? ciclo.divide(divisorIva, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                break;
            }
            vistos.add(nuevoConIva);
            costoEnvioConIva = nuevoConIva;
            costoEnvioSinIva = nuevoConIva.compareTo(BigDecimal.ZERO) > 0
                    ? nuevoConIva.divide(divisorIva, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        }
        return new ResultadoEstabilizacion(pvp, costoEnvioSinIva, costoEnvioConIva, it);
    }
}
```

- [ ] **Step 4: Correr y verificar que pasa**

Run: `mvn -o test -Dtest=EnvioEstabilizadorTest`
Expected: PASS.

- [ ] **Step 5: Refactor de `calcularCostoEnvioGratis` para usar el núcleo (sin cambiar comportamiento)**

Reemplazar el bucle interno (líneas ~236-344) por una llamada a `EnvioEstabilizador.estabilizar(...)`, pasando como `pvpFn` la llamada al motor (`calcularPrecioCanalConEnvio(productoDb.getId(), canalMl.getId(), 0, envio, null)` → `.pvp()`) y como `envioConIvaFn` la decisión API-ML/tier según el umbral. El `tipoCalculo`/`status` se determinan en el caller. Mantener `guardarCostoEnvio(...)` y el DTO de retorno idénticos.

- [ ] **Step 6: Correr la regresión de envío/ML**

Run: `mvn -o test -Dtest=MercadoLibre*Test,CostoEnvio*Test,*EnvioTest`
Expected: PASS — el comportamiento observable de `calcularCostoEnvioGratis` no cambia.

- [ ] **Step 7: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/EnvioEstabilizador.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/EnvioEstabilizadorTest.java \
        supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java
git commit -m "refactor(ml): extraer nucleo puro de estabilizacion de envio"
```

---

### Task 4: Comisión sin ítem (listing_prices read-only)

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/MlComisionSinItemTest.java`

**Interfaces:**
- Produces: `BigDecimal consultarComisionPorcentaje(String categoryId, BigDecimal price, String listingTypeId, String logisticType)` — devuelve `meli_percentage_fee` (BigDecimal) o `BigDecimal.ZERO` si no se puede determinar. Consulta `GET /sites/MLA/listing_prices?...` SIN `item_id`. Y un parser estático testeable:
  `static BigDecimal parseMeliPercentageFee(JsonNode listingPricesArray, String listingTypeId)`.

- [ ] **Step 1: Escribir el test que falla (parser puro)**

```java
package ar.com.leo.super_master_backend.apis.ml.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MlComisionSinItemTest {
    private final ObjectMapper om = new ObjectMapper();

    @Test
    void extrae_meli_percentage_fee_del_listing_type() throws Exception {
        String json = """
            [{"listing_type_id":"gold_special","sale_fee_details":{"meli_percentage_fee":13,"percentage_fee":13}},
             {"listing_type_id":"gold_pro","sale_fee_details":{"meli_percentage_fee":15.5}}]""";
        assertThat(MercadoLibreService.parseMeliPercentageFee(om.readTree(json), "gold_special"))
                .isEqualByComparingTo("13");
    }

    @Test
    void listing_type_inexistente_devuelve_cero() throws Exception {
        String json = "[{\"listing_type_id\":\"free\",\"sale_fee_details\":{\"meli_percentage_fee\":0}}]";
        assertThat(MercadoLibreService.parseMeliPercentageFee(om.readTree(json), "gold_special"))
                .isEqualByComparingTo("0");
    }
}
```

- [ ] **Step 2: Correr y verificar que falla**

Run: `mvn -o test -Dtest=MlComisionSinItemTest`
Expected: FAIL — método inexistente.

- [ ] **Step 3: Implementar parser + consulta**

Reutilizar el patrón de `obtenerCostoVenta` (líneas 442-470) para el parser. Agregar:

```java
/** Extrae meli_percentage_fee del listing_type pedido en la respuesta de listing_prices. ZERO si no está. */
static BigDecimal parseMeliPercentageFee(com.fasterxml.jackson.databind.JsonNode arr, String listingTypeId) {
    if (arr == null || !arr.isArray()) return BigDecimal.ZERO;
    for (com.fasterxml.jackson.databind.JsonNode listing : arr) {
        if (listingTypeId.equals(listing.path("listing_type_id").asString(""))) {
            return BigDecimal.valueOf(listing.path("sale_fee_details").path("meli_percentage_fee").asDouble(0));
        }
    }
    return BigDecimal.ZERO;
}

/** Comisión (meli_percentage_fee) SIN necesitar el ítem: calculador read-only de listing_prices. */
BigDecimal consultarComisionPorcentaje(String categoryId, BigDecimal price, String listingTypeId, String logisticType) {
    verificarTokens();
    String uri = String.format(
            "/sites/MLA/listing_prices?category_id=%s&price=%s&currency_id=ARS&listing_type_id=%s&logistic_type=%s&shipping_mode=me2",
            categoryId, price.setScale(0, java.math.RoundingMode.HALF_UP), listingTypeId, logisticType);
    String body = retryHandler.get(uri, () -> tokens.accessToken);
    if (body == null) return BigDecimal.ZERO;
    try {
        return parseMeliPercentageFee(objectMapper.readTree(body), listingTypeId);
    } catch (Exception e) {
        log.warn("ML - No se pudo parsear listing_prices para categoria {}: {}", categoryId, e.getMessage());
        return BigDecimal.ZERO;
    }
}
```

- [ ] **Step 4: Correr y verificar que pasa**

Run: `mvn -o test -Dtest=MlComisionSinItemTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/MlComisionSinItemTest.java
git commit -m "feat(ml): consultar comision via listing_prices sin item"
```

---

### Task 5: Envío sin ítem (shipping_options con dimensions)

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java`

**Interfaces:**
- Produces: `BigDecimal consultarEnvioConIvaSinItem(String userId, Producto producto, String categoryId, String listingType, String logisticType, String zipCode, BigDecimal pvp)` — costo de envío CON IVA usando `GET /users/{userId}/shipping_options/free?dimensions=...` (sin `item_id`); `BigDecimal.ZERO` si no aplica/falla. `Producto` es `ar.com.leo.super_master_backend.dominio.producto.entity.Producto` (dimensiones del paquete ML vía `mlPaq*`).

- [ ] **Step 1: Implementar (sin test unitario de red; se cubre vía Task 6/integración)**

Extraer el armado de URL con `dimensions=` de `calcularCostoEnvioInterno` (líneas 928-953) a un método que no dependa del objeto `Producto` de la API ML (modelo `apis.ml.model.Producto`), sino de los datos del producto de dominio. Reutiliza `producto.getDimensions()`, el zip del vendedor (`getUserShippingZip()` — obtener vía `getUserId()`/perfil; si no hay helper, leer del `seller_address` de la cuenta como hace el flujo actual). Devuelve `list_cost` con IVA.

```java
BigDecimal consultarEnvioConIvaSinItem(String userId,
        ar.com.leo.super_master_backend.dominio.producto.entity.Producto producto,
        String categoryId, String listingType, String logisticType, String zipCode, BigDecimal pvp) {
    String dimensions = producto.getMlDimensionsString(); // "alto x ancho x largo,peso" en el formato que ya usa el alta
    if (dimensions == null || dimensions.isBlank() || zipCode == null || zipCode.isBlank()) {
        return BigDecimal.ZERO;
    }
    String params = String.format(
            "&item_price=%s&listing_type_id=%s&mode=me2&condition=new&logistic_type=%s&zip_code=%s&verbose=true&free_shipping=true&category_id=%s",
            String.format(java.util.Locale.US, "%.2f", pvp), listingType, logisticType, zipCode, categoryId);
    String uri = String.format("/users/%s/shipping_options/free?dimensions=%s%s",
            userId, java.net.URLEncoder.encode(dimensions, java.nio.charset.StandardCharsets.UTF_8), params);
    String body = retryHandler.get(uri, () -> tokens.accessToken);
    if (body == null) return BigDecimal.ZERO;
    try {
        double cost = objectMapper.readTree(body).path("coverage").path("all_country").path("list_cost").asDouble(0);
        return cost > 0 ? BigDecimal.valueOf(cost) : BigDecimal.ZERO;
    } catch (Exception e) {
        log.warn("ML - envío sin item falló para SKU {}: {}", producto.getSku(), e.getMessage());
        return BigDecimal.ZERO;
    }
}
```

> Implementación: si no existe `producto.getMlDimensionsString()`, construirlo con el mismo formato que ya espera la API ML (revisar cómo arma `getDimensions()` el modelo `apis.ml.model.Producto`) y agregar el helper en la entidad de dominio o un util. El `zipCode` se obtiene una sola vez por export (igual que el flujo actual hace `getUserId()` + perfil).

- [ ] **Step 2: Compilar**

Run: `mvn -o -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java
git commit -m "feat(ml): consultar costo de envio sin item via dimensions"
```

---

### Task 6: Orquestador `calcularPrecioFinalParaPublicar`

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/MlPrecioFinalTest.java`

**Interfaces:**
- Consumes: Task 1 (`calcularPrecioCanalConEnvio` con override), Task 2 (`redondearPrecioMl`), Task 3 (`EnvioEstabilizador`), Task 4 (`consultarComisionPorcentaje`), Task 5 (`consultarEnvioConIvaSinItem`).
- Produces: `BigDecimal calcularPrecioFinalParaPublicar(Producto producto, String categoryId, int cuotas)` — PVP final redondeado a entero, o lanza `IllegalStateException` con motivo si el motor no puede calcular. `Producto` = entidad de dominio.

- [ ] **Step 1: Implementar el orquestador**

Arma el bucle previo SIN MLA: en cada iteración el motor calcula el PVP con `comisionMlOverride` = comisión de `listing_prices` para el PVP actual, y el envío sale de `consultarEnvioConIvaSinItem` (o tier `configuracionMlService.obtenerCostoEnvioPorPvp(pvp)` si el PVP no supera `umbralEnvioGratis`). Reusa `EnvioEstabilizador.estabilizar`.

```java
BigDecimal calcularPrecioFinalParaPublicar(
        ar.com.leo.super_master_backend.dominio.producto.entity.Producto producto,
        String categoryId, int cuotas) {
    verificarTokens();
    Canal canalMl = canalRepository.findByNombreIgnoreCase(CANAL_ML)
            .orElseThrow(() -> new IllegalStateException("No se encontró el canal " + CANAL_ML));
    ConfiguracionMl config = configuracionMlService.obtenerEntidad();
    BigDecimal umbral = config.getUmbralEnvioGratis();
    String listingType = "gold_special";
    String logisticType = config.getLogisticTypeDefault() != null ? config.getLogisticTypeDefault() : "drop_off";
    String zip;
    try { zip = obtenerZipVendedor(); } catch (Exception e) { zip = null; }
    BigDecimal iva = producto.getIva() != null ? producto.getIva() : new BigDecimal("21");
    BigDecimal divisorIva = BigDecimal.ONE.add(iva.divide(new BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP));
    final String zipFinal = zip;

    var r = EnvioEstabilizador.estabilizar(
        envioSinIva -> {
            BigDecimal pvpTmp = calculoPrecioService
                    .calcularPrecioCanalConEnvio(producto.getId(), canalMl.getId(), cuotas, envioSinIva, BigDecimal.ZERO)
                    .pvp();
            BigDecimal comision = consultarComisionPorcentaje(categoryId, pvpTmp, listingType, logisticType);
            return calculoPrecioService
                    .calcularPrecioCanalConEnvio(producto.getId(), canalMl.getId(), cuotas, envioSinIva, comision)
                    .pvp();
        },
        pvp -> pvp.compareTo(umbral) >= 0
                ? consultarEnvioConIvaSinItem(obtenerUserIdSafe(), producto, categoryId, listingType, logisticType, zipFinal, pvp)
                : nvl(configuracionMlService.obtenerCostoEnvioPorPvp(pvp)),
        divisorIva, 10);

    if (r.pvp() == null || r.pvp().compareTo(BigDecimal.ZERO) <= 0) {
        throw new IllegalStateException("PVP calculado no válido");
    }
    return redondearPrecioMl(r.pvp());
}
```

> `nvl(...)` devuelve `BigDecimal.ZERO` si el tier es null. `obtenerUserIdSafe()` cachea el `getUserId()`. `obtenerZipVendedor()` lee el zip del perfil del vendedor (mismo origen que el flujo actual de envío). Si `calcularPrecioCanalConEnvio` lanza `BadRequestException`/`NotFoundException` (sin margen/costo), propagar como `IllegalStateException(e.getMessage())`.

- [ ] **Step 2: Escribir test con mocks**

Mockear `calculoPrecioService`, `consultarComisionPorcentaje` (vía spy) y la config para verificar que el resultado es el PVP estabilizado redondeado, y que un `BadRequestException` del motor se traduce a `IllegalStateException`. (Usar `@MockBean`/Mockito sobre `MercadoLibreService` parcial o extraer la dependencia de cálculo.)

```java
// Verifica: estabiliza con envío constante y devuelve el PVP redondeado.
// (Detalle de mocks según el estilo de los tests existentes de MercadoLibreService.)
```

- [ ] **Step 3: Correr y verificar que pasa**

Run: `mvn -o test -Dtest=MlPrecioFinalTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/MlPrecioFinalTest.java
git commit -m "feat(ml): orquestador de precio final para publicar (sin MLA)"
```

---

### Task 7: Usar el precio calculado en el ALTA

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java` (`crearItemEnMlCore`, `crearItemEnMl`)
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/CrearItemEnMlTest.java` (ampliar)

**Interfaces:**
- Consumes: Task 6 (`calcularPrecioFinalParaPublicar`).
- `crearItemEnMlCore` recibe el precio ya calculado en vez de calcular `costo*5`. Nuevo parámetro `BigDecimal precioFinal` (en lugar de derivarlo del costo). Si `precioFinal == null` → `ResultadoAltaMl.error("no se pudo calcular el precio del canal ML")`.

- [ ] **Step 1: Ampliar el test del core**

En `CrearItemEnMlTest`, agregar un caso: con `precioFinal = 18341`, el JSON posteado lleva `price = 18341`; con `precioFinal = null`, retorna `ResultadoAltaMl.error(...)` y no postea.

- [ ] **Step 2: Correr y verificar que falla**

Run: `mvn -o test -Dtest=CrearItemEnMlTest`
Expected: FAIL — el core aún calcula `costo*5`.

- [ ] **Step 3: Modificar `crearItemEnMlCore`**

Cambiar la firma para recibir `BigDecimal precioFinal` (quitar el cálculo `costo * MULTIPLICADOR_PRECIO_ML` de la línea ~1935). Validar al inicio: si `precioFinal == null` → `return ResultadoAltaMl.error("no se pudo calcular el precio del canal ML")`. Usar `precioFinal` en `MlItemPayloadBuilder.construir(producto, categoryId, precioFinal, 0, pictureIds, familyName)`.

- [ ] **Step 4: Modificar `crearItemEnMl` (wrapper con red)**

Antes de llamar al core, predecir la categoría (ya se hace dentro del core vía `predictor`; extraer la predicción de categoría ANTES para poder calcular el precio). Calcular `BigDecimal precioFinal`:

```java
String categoryId = resolverCategoriaMl(producto.getMlCategoryId(), producto.getTituloMl(), this::predecirCategoria);
if (categoryId == null || categoryId.isBlank()) {
    return ResultadoAltaMl.error("no se pudo predecir la categoría");
}
BigDecimal precioFinal;
try {
    precioFinal = calcularPrecioFinalParaPublicar(producto, categoryId, 0);
} catch (Exception e) {
    return ResultadoAltaMl.error("no se pudo calcular el precio del canal ML: " + e.getMessage());
}
```

Pasar `categoryId` y `precioFinal` al core (el core ya no predice ni calcula precio).

- [ ] **Step 5: Correr y verificar que pasa**

Run: `mvn -o test -Dtest=CrearItemEnMlTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/CrearItemEnMlTest.java
git commit -m "feat(ml): publicar con precio calculado real en el alta"
```

---

### Task 8: Usar el precio calculado en la ACTUALIZACIÓN

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java` (`actualizarItemEnMlCore`, `actualizarItemEnMl`)
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/ActualizarItemEnMlTest.java` (ampliar)

**Interfaces:**
- Consumes: Task 6.
- En `actualizarItemEnMlCore`, el `price` deja de ser `costo*5` (línea ~1800); se inyecta `precioFinal`. Si es null → advertencia "precio no actualizado" (no aborta el resto del update).

- [ ] **Step 1: Ampliar el test**

Verificar que `actualizarItemEnMlCore` usa el `precioFinal` inyectado en `updatePrice.actualizar(mla, precioFinal.doubleValue())`, y que con `precioFinal=null` agrega la advertencia "precio no actualizado".

- [ ] **Step 2: Correr y verificar que falla**

Run: `mvn -o test -Dtest=ActualizarItemEnMlTest`
Expected: FAIL.

- [ ] **Step 3: Modificar el core y el wrapper**

En `actualizarItemEnMlCore`, reemplazar la línea `double price = producto.getCosto().multiply(MULTIPLICADOR_PRECIO_ML).doubleValue();` por usar un `BigDecimal precioFinal` recibido por parámetro: si null → `advertencia = concatAdv(advertencia, "precio no actualizado")` y saltear el `updatePrice`. En `actualizarItemEnMl`, calcular `precioFinal` con `calcularPrecioFinalParaPublicar(producto, <categoryId del ítem>, 0)` (categoría: leer la del ítem publicado vía `getItemByMLA(mla).categoryId`).

- [ ] **Step 4: Correr y verificar que pasa**

Run: `mvn -o test -Dtest=ActualizarItemEnMlTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/ActualizarItemEnMlTest.java
git commit -m "feat(ml): actualizar publicacion con precio calculado real"
```

---

### Task 9: Eliminar `MULTIPLICADOR_PRECIO_ML` y cierre

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java`

- [ ] **Step 1: Quitar la constante**

Eliminar `private static final BigDecimal MULTIPLICADOR_PRECIO_ML = BigDecimal.valueOf(5);` (línea ~69) si ya no tiene usos.

- [ ] **Step 2: Verificar que no quedan referencias**

Run: `grep -rn "MULTIPLICADOR_PRECIO_ML" supermaster-backend/src` → debe no devolver nada.

- [ ] **Step 3: Suite completa de ML + cálculo**

Run: `mvn -o test -Dtest=MercadoLibre*Test,*ItemEnMlTest,CalculoPrecio*Test,EnvioEstabilizadorTest,MlComisionSinItemTest,MlRedondeoPrecioTest,MlPrecioFinalTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java
git commit -m "chore(ml): eliminar multiplicador costo*5 (reemplazado por precio calculado)"
```

---

## Notas de ejecución

- **Configuración `logistic_type` por defecto**: Task 6 usa `config.getLogisticTypeDefault()`. Si `ConfiguracionMl` no tiene ese campo, agregarlo (columna nueva → script SQL en `src/main/resources/db/`, `ddl-auto=validate`) o usar la constante `"drop_off"` directamente y dejar un TODO acotado. Confirmar con el dueño antes de crear la columna.
- **`obtenerZipVendedor()` / `obtenerUserIdSafe()`**: si no existen helpers equivalentes, extraerlos del flujo de `calcularCostoEnvioGratis` (que ya obtiene `userId` y arma el envío). Reusar, no duplicar.
- **Frontend**: sin cambios en Fase 1 (se publica a contado, `cuotas = 0`).
