# Peso/dimensiones de Nube editables + precio promocional en tarjetas — Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hacer editables (externalizadas) las dimensiones de envío de Tienda Nube y mostrar el precio promocional en las tarjetas de "Canales de venta".

**Architecture:** Las dims de Nube son transient (sin columnas nuevas): default fijo en alta, leídas del canal en edición, enviadas al exportar (reemplazan el placeholder del builder), mismo patrón que descripción/SEO. El precio promocional se expone agregando `promo` a `EstadoCanalDTO` (leído de la variante Nube) y se muestra en la tarjeta.

**Tech Stack:** Spring Boot 4 / Java 25 / Maven / Jackson 3 (backend); Next.js/React/TS (frontend).

## Global Constraints

- Backend offline: `mvn -o test` (mvn del PATH, NO mvnw). Jackson 3: `node.path("x").asString(null)`.
- Frontend: `npx tsc --noEmit` exit 0.
- Trabajar en `main`. Commits terminan con `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. `git add` SOLO de los archivos de cada tarea (hay WIP en el working tree que NO se toca), nunca `-A` ni `.superpowers/`.
- **Records:** agregar componentes a `EstadoCanalDTO`/`DatosCanalDTO` rompe constructores posicionales → actualizar TODOS los call sites (incluidos factories y tests).
- Default fijo dims Nube: **`weight "0.050"`, `depth "8.00"`, `width "5.00"`, `height "5.00"`**.
- Sin columnas nuevas en BD. Sin cambios en Mercado Libre para promo (ML pasa `promo = null`).

---

## Task 1: Backend — `promo` en EstadoCanalDTO + parsers

**Files:**
- Modify: `supermaster-backend/.../dominio/producto/estado/dto/EstadoCanalDTO.java`
- Modify: `supermaster-backend/.../dominio/producto/estado/NubeEstadoParser.java:33`
- Modify: `supermaster-backend/.../dominio/producto/estado/MlEstadoParser.java:28`
- Modify: `supermaster-backend/.../dominio/producto/estado/DuxEstadoParser.java:14`
- Test: `supermaster-backend/.../dominio/producto/estado/EstadoPublicacionServiceTest.java`

**Interfaces:**
- Produces: `EstadoCanalDTO` con componente `BigDecimal promo` (4º, tras `precio`).

- [ ] **Step 1: Agregar `promo` al record + factories**

`EstadoCanalDTO.java` completo:

```java
package ar.com.leo.super_master_backend.dominio.producto.estado.dto;

import java.math.BigDecimal;

/** Estado + snapshot read-only de la publicación de un canal. */
public record EstadoCanalDTO(
        boolean publicado,
        String estado,        // ML: "active"/"paused"; Nube: "visible"/"oculta"; null si no aplica
        BigDecimal precio,
        BigDecimal promo,     // precio promocional (Nube: variant.promotional_price); null si no aplica
        Integer stock,
        String peso,          // ej "1.5 kg" / "214 g"
        String dimensiones,   // ej "10 × 20 × 30 cm"
        boolean error
) {
    public static EstadoCanalDTO noPublicado() {
        return new EstadoCanalDTO(false, null, null, null, null, null, null, false);
    }
    public static EstadoCanalDTO ofError() {
        return new EstadoCanalDTO(false, null, null, null, null, null, null, true);
    }
}
```

- [ ] **Step 2: `NubeEstadoParser` lee `promotional_price`**

En `NubeEstadoParser.parse`, después de calcular `precio` (línea ~22) agregar el parseo de promo, y actualizar el `return` (línea 33):

```java
        String promoStr = variant.path("promotional_price").asString(null);
        BigDecimal promo = null;
        if (promoStr != null && !promoStr.isBlank()) {
            try {
                BigDecimal p = new BigDecimal(promoStr.trim());
                if (p.signum() > 0) promo = p;   // 0/vacío = sin promo
            } catch (NumberFormatException ignored) {}
        }
```

```java
        return new EstadoCanalDTO(true, published ? "visible" : "oculta", precio, promo, stock, peso, dims, false);
```

- [ ] **Step 3: `MlEstadoParser` y `DuxEstadoParser` pasan `promo = null`**

`MlEstadoParser.java:28`:
```java
        return new EstadoCanalDTO(true, status, precio, null, stock, peso, dims, false);
```

`DuxEstadoParser.java:14-15`:
```java
        return new EstadoCanalDTO(true, habilitado ? "habilitado" : "deshabilitado",
                null, null, null, null, null, false);
```

- [ ] **Step 4: Assert de promo en el test**

En `EstadoPublicacionServiceTest.leer_cruzaMlYLasDosTiendas`, el mock de HOGAR no tiene promo → cambialo para incluir `promotional_price` y asertar. Reemplazá el stub de HOGAR:

```java
        when(nube.buscarProductoPorSku("ABC", TiendaNubeService.STORE_HOGAR)).thenReturn(json("""
            {"id":5,"published":true,"variants":[{"id":9,"price":"100","promotional_price":"80","stock":2,"weight":"1.0","height":"1","width":"1","depth":"1"}]}"""));
```

Y agregá, junto a las aserciones de hogar:

```java
        assertThat(dto.hogar().promo()).isEqualByComparingTo(new java.math.BigDecimal("80"));
        assertThat(dto.ml().promo()).isNull();
```

- [ ] **Step 5: Compilar y correr el test**

Run: `cd supermaster-backend && mvn -o -Dtest=EstadoPublicacionServiceTest test`
Expected: `Tests run: 3, Failures: 0, Errors: 0`, BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/dto/EstadoCanalDTO.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/NubeEstadoParser.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/MlEstadoParser.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/DuxEstadoParser.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/estado/EstadoPublicacionServiceTest.java
git commit -m "feat(estado): expone promo (promotional_price) en EstadoCanalDTO desde la variante Nube"
```

---

## Task 2: Frontend — mostrar promo en la tarjeta de canal

**Files:**
- Modify: `supermaster-frontend/src/app/productos/productosService.ts` (type `EstadoCanal`, ~líneas 521-529)
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx` (`renderEstadoBody`)

**Interfaces:**
- Consumes: `EstadoCanalDTO.promo` (Task 1).

- [ ] **Step 1: Agregar `promo` al tipo `EstadoCanal`**

En `productosService.ts`, en `export type EstadoCanal = { ... }`, agregar `promo` después de `precio`:

```ts
	precio: number | null;
	promo: number | null;
```

- [ ] **Step 2: Mostrar la promo en `renderEstadoBody`**

En `ProductoFormModal.tsx`, dentro de `renderEstadoBody`, después del bloque que muestra `canal.precio` (el `{canal.precio != null && (...)}`), agregar:

```tsx
                      {canal.promo != null && (
                          <div className="flex justify-between gap-1">
                              <span className="text-slate-400">Promo</span>
                              <span className="font-medium text-emerald-600 dark:text-emerald-400">{canal.promo.toLocaleString("es-AR", { style: "currency", currency: "ARS", minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
                          </div>
                      )}
```

- [ ] **Step 3: Verificar tipos**

Run: `cd supermaster-frontend && npx tsc --noEmit`
Expected: exit 0.

- [ ] **Step 4: Commit**

```bash
git add supermaster-frontend/src/app/productos/productosService.ts supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(estado): muestra el precio promocional en la tarjeta de canal"
```

---

## Task 3: Backend — leer dims de Nube y exponerlas en DatosCanalDTO

**Files:**
- Modify: `supermaster-backend/.../dominio/producto/estado/dto/DatosCanalDTO.java`
- Modify: `supermaster-backend/.../dominio/producto/estado/EstadoPublicacionService.java` (record `NubePanel`, `leerNubePanel`, `leer`)
- Test: `supermaster-backend/.../dominio/producto/estado/EstadoPublicacionServiceTest.java`

**Interfaces:**
- Produces: `DatosCanalDTO` con 4 componentes nuevos al final: `nubePeso`, `nubeProfundidad`, `nubeAncho`, `nubeAlto` (String).

- [ ] **Step 1: Agregar los 4 campos a `DatosCanalDTO`**

En `DatosCanalDTO.java`, agregar al final del record (después de `String mlaResuelto`):

```java
        String mlaResuelto,
        /** Dimensiones de la variante en Nube (crudas), para pre-cargar los inputs en edición. */
        String nubePeso,
        String nubeProfundidad,
        String nubeAncho,
        String nubeAlto
```

- [ ] **Step 2: `NubePanel` lleva las dims y `leerNubePanel` las parsea**

En `EstadoPublicacionService.java`, cambiar el record `NubePanel`:

```java
    private record NubePanel(EstadoCanalDTO estado, String descripcion, SeoCanalDTO seo,
                             String peso, String profundidad, String ancho, String alto) {}
```

y reescribir `leerNubePanel`:

```java
    private NubePanel leerNubePanel(String sku, String store) {
        JsonNode product;
        try {
            product = tiendaNubeService.buscarProductoPorSku(sku, store);
        } catch (Exception e) {
            return new NubePanel(EstadoCanalDTO.ofError(), null, null, null, null, null, null);
        }
        JsonNode variant = (product != null) ? product.path("variants").path(0) : null;
        String peso = variant != null ? variant.path("weight").asString(null) : null;
        String prof = variant != null ? variant.path("depth").asString(null) : null;
        String ancho = variant != null ? variant.path("width").asString(null) : null;
        String alto = variant != null ? variant.path("height").asString(null) : null;
        return new NubePanel(estadoNube(product), descripcionNube(product), NubeSeoParser.parse(product),
                peso, prof, ancho, alto);
    }
```

- [ ] **Step 3: `leer()` pasa las dims a `DatosCanalDTO` (HOGAR, o GASTRO si no hay)**

En `EstadoPublicacionService.leer`, reemplazar la construcción de `DatosCanalDTO` por:

```java
        DatosCanalDTO datos = new DatosCanalDTO(
                ml.categoryId(),
                ml.categoryNombre(),
                ml.atributos(),
                ml.descripcion(),
                hogar.descripcion(),
                gastro.descripcion(),
                hogar.seo(),
                gastro.seo(),
                ml.mlaResuelto(),
                hogar.peso() != null ? hogar.peso() : gastro.peso(),
                hogar.profundidad() != null ? hogar.profundidad() : gastro.profundidad(),
                hogar.ancho() != null ? hogar.ancho() : gastro.ancho(),
                hogar.alto() != null ? hogar.alto() : gastro.alto());
```

- [ ] **Step 4: Assert de dims en el test**

En `EstadoPublicacionServiceTest.leer_cruzaMlYLasDosTiendas` (el stub de HOGAR ya tiene `weight/height/width/depth = "1.0"/"1"/"1"/"1"` de Task 1), agregá:

```java
        assertThat(dto.datos().nubePeso()).isEqualTo("1.0");
        assertThat(dto.datos().nubeProfundidad()).isEqualTo("1"); // depth
        assertThat(dto.datos().nubeAncho()).isEqualTo("1");        // width
        assertThat(dto.datos().nubeAlto()).isEqualTo("1");         // height
```

- [ ] **Step 5: Compilar y correr el test**

Run: `cd supermaster-backend && mvn -o -Dtest=EstadoPublicacionServiceTest test`
Expected: `Tests run: 3, Failures: 0`, BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/dto/DatosCanalDTO.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/EstadoPublicacionService.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/producto/estado/EstadoPublicacionServiceTest.java
git commit -m "feat(estado): expone dims de la variante Nube (peso/prof/ancho/alto) en DatosCanalDTO"
```

---

## Task 4: Backend — enviar las dims de Nube al publicar

**Files:**
- Modify: `supermaster-backend/.../dominio/producto/entity/Producto.java` (campos `@Transient`, ~línea 75)
- Modify: `supermaster-backend/.../apis/nube/dto/ExportNubeRequestDTO.java`
- Modify: `supermaster-backend/.../apis/nube/service/NubeExportService.java` (~líneas 52-66)
- Modify: `supermaster-backend/.../apis/nube/service/NubeProductoPayloadBuilder.java:44-47`

**Interfaces:**
- Consumes: nada de tareas previas.
- Produces: `Producto.setNube{Peso,Profundidad,Ancho,Alto}(String)`; `ExportNubeRequestDTO` con dims top-level.

- [ ] **Step 1: `@Transient` en `Producto`**

En `Producto.java`, junto a los otros `@Transient` (después de `duxHabilitado`, ~línea 75), agregar:

```java
    @Transient
    private String nubePeso;
    @Transient
    private String nubeProfundidad;
    @Transient
    private String nubeAncho;
    @Transient
    private String nubeAlto;
```

(Lombok genera getters/setters como con los demás `@Transient`.)

- [ ] **Step 2: Dims en `ExportNubeRequestDTO` (top-level)**

`ExportNubeRequestDTO.java` completo:

```java
package ar.com.leo.super_master_backend.apis.nube.dto;

import java.util.List;

public record ExportNubeRequestDTO(List<String> skus, List<DestinoNube> tiendas,
                                   String nubePeso, String nubeProfundidad, String nubeAncho, String nubeAlto) {
    public record DestinoNube(String tienda, Integer cuotas,
                              ar.com.leo.super_master_backend.apis.openai.dto.SeoGeneradoDTO seo,
                              String descripcion) {}
}
```

- [ ] **Step 3: `NubeExportService` setea las dims antes de publicar**

En `NubeExportService.exportar`, dentro del loop de tiendas (después de `setEquipamientoGastro(...)`, ~línea 66), agregar:

```java
                producto.setNubePeso(request.nubePeso());
                producto.setNubeProfundidad(request.nubeProfundidad());
                producto.setNubeAncho(request.nubeAncho());
                producto.setNubeAlto(request.nubeAlto());
```

(En lote/masivo llegan null → el builder usa el default.)

- [ ] **Step 4: `NubeProductoPayloadBuilder` usa las dims con fallback al default**

En `NubeProductoPayloadBuilder.construir`, reemplazar las 4 líneas hardcodeadas (44-47):

```java
        variant.put("weight", "0.050");
        variant.put("depth", "8.00");
        variant.put("width", "5.00");
        variant.put("height", "5.00");
```

por:

```java
        variant.put("weight", dimOrDefault(p.getNubePeso(), "0.050"));
        variant.put("depth", dimOrDefault(p.getNubeProfundidad(), "8.00"));
        variant.put("width", dimOrDefault(p.getNubeAncho(), "5.00"));
        variant.put("height", dimOrDefault(p.getNubeAlto(), "5.00"));
```

y agregar el helper privado al final de la clase:

```java
    /** Valor de dimensión si viene cargado; si no, el default fijo de Nube. */
    private static String dimOrDefault(String v, String def) {
        return (v != null && !v.isBlank()) ? v.trim() : def;
    }
```

- [ ] **Step 5: Buscar callers rotos por la nueva firma del record**

Run: `cd supermaster-backend && grep -rn "new ExportNubeRequestDTO(" src`
Expected: SIN resultados (se construye solo vía Jackson `@RequestBody`). Si aparece alguno, actualizarlo pasando `null` en las 4 dims.

- [ ] **Step 6: Compilar y correr tests de Nube/estado**

Run: `cd supermaster-backend && mvn -o -q compile && mvn -o -Dtest=EstadoPublicacionServiceTest,ActualizarProductoEnNubeTest test`
Expected: BUILD SUCCESS, tests verdes. (Si `ActualizarProductoEnNubeTest` no existe, correr solo `EstadoPublicacionServiceTest`.)

- [ ] **Step 7: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/entity/Producto.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/dto/ExportNubeRequestDTO.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeExportService.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeProductoPayloadBuilder.java
git commit -m "feat(nube): envía peso/dimensiones editables (transient) en vez del placeholder hardcodeado"
```

---

## Task 5: Frontend — inputs de peso/dimensiones de Nube

**Files:**
- Modify: `supermaster-frontend/src/app/productos/productosService.ts` (`exportarProductosANubeAPI` ~289-297; type `DatosCanal` ~531-540)
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx` (estados, carga, inputs, llamada de export)

**Interfaces:**
- Consumes: `DatosCanal.nube*` (Task 3); `exportarProductosANubeAPI` con dims (este task); endpoint que acepta dims (Task 4).

- [ ] **Step 1: `DatosCanal` (tipo) suma las dims**

En `productosService.ts`, en `export type DatosCanal = { ... }`, agregar antes del cierre:

```ts
	mlaResuelto: string | null;
	nubePeso: string | null;
	nubeProfundidad: string | null;
	nubeAncho: string | null;
	nubeAlto: string | null;
```

(si `mlaResuelto` ya está, solo agregar las 4 dims).

- [ ] **Step 2: `exportarProductosANubeAPI` acepta y manda las dims**

Reemplazar la función (289-297):

```ts
export const exportarProductosANubeAPI = async (
	skus: string[], tiendas: DestinoNube[],
	dims?: { nubePeso: string; nubeProfundidad: string; nubeAncho: string; nubeAlto: string },
): Promise<ExportCanalResultDTO> => {
	const res = await fetchAPI(`${API_BASE_URL}/api/nube/exportar-productos`, {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify({ skus, tiendas, ...dims }),
	});
	if (!res.ok) throw new Error(await extraerMensajeError(res, "No se pudo subir el producto a Tienda Nube"));
	return await res.json();
};
```

- [ ] **Step 3: Estados de las dims con default fijo**

En `ProductoFormModal.tsx`, junto a los otros `useState` del componente, agregar:

```tsx
    const [nubePeso, setNubePeso] = useState("0.050");
    const [nubeProfundidad, setNubeProfundidad] = useState("8.00");
    const [nubeAncho, setNubeAncho] = useState("5.00");
    const [nubeAlto, setNubeAlto] = useState("5.00");
```

- [ ] **Step 4: Carga — alta default / edición desde el canal**

En el efecto de apertura, en la rama de **alta** (el `else` que resetea, donde está `setEstadoCanales(null); ...`), agregar el reset al default:

```tsx
            setNubePeso("0.050"); setNubeProfundidad("8.00"); setNubeAncho("5.00"); setNubeAlto("5.00");
```

En el `.then` de `getEstadoPublicacionAPI` (edición), después de setear las descripciones/SEO, agregar (si Nube trae el valor se usa, si no se deja el default):

```tsx
                    if (e.datos.nubePeso) setNubePeso(e.datos.nubePeso);
                    if (e.datos.nubeProfundidad) setNubeProfundidad(e.datos.nubeProfundidad);
                    if (e.datos.nubeAncho) setNubeAncho(e.datos.nubeAncho);
                    if (e.datos.nubeAlto) setNubeAlto(e.datos.nubeAlto);
```

- [ ] **Step 5: Inputs en el modal (bloque "Paquete de envío · Tienda Nube")**

En `ProductoFormModal.tsx`, dentro de la sección de Tienda Nube y solo cuando hay alguna tienda Nube tildada, agregar el bloque. Insertarlo donde corresponda a la sección Nube (p. ej. después del Título Nube compartido), envuelto en `{(subirKtHogar || subirKtGastro) && (...)}`:

```tsx
                    {(subirKtHogar || subirKtGastro) && (
                        <fieldset className={`${sectionClassName} ${SECTION_TINT.seo}`}>
                            <legend className={sectionTitleClassName}><CubeIcon className="h-5 w-5" /> Paquete de envío · Tienda Nube</legend>
                            <p className={`${sectionDescriptionClassName} mb-3`}>Peso y dimensiones del paquete que se envían a Tienda Nube.</p>
                            <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
                                <label className="block">
                                    <span className={fieldLabelClassName}>Peso (kg)</span>
                                    <input type="number" min={0} step="0.001" className={inputBaseClassName} value={nubePeso} onChange={e => setNubePeso(e.target.value)} />
                                </label>
                                <label className="block">
                                    <span className={fieldLabelClassName}>Profundidad (cm)</span>
                                    <input type="number" min={0} className={inputBaseClassName} value={nubeProfundidad} onChange={e => setNubeProfundidad(e.target.value)} />
                                </label>
                                <label className="block">
                                    <span className={fieldLabelClassName}>Ancho (cm)</span>
                                    <input type="number" min={0} className={inputBaseClassName} value={nubeAncho} onChange={e => setNubeAncho(e.target.value)} />
                                </label>
                                <label className="block">
                                    <span className={fieldLabelClassName}>Alto (cm)</span>
                                    <input type="number" min={0} className={inputBaseClassName} value={nubeAlto} onChange={e => setNubeAlto(e.target.value)} />
                                </label>
                            </div>
                        </fieldset>
                    )}
```

(Si `CubeIcon` no está importado en ese scope, usar un icono ya importado como `BuildingStorefrontIcon`.)

- [ ] **Step 6: Pasar las dims en la llamada de export de Nube**

En `ProductoFormModal.tsx`, donde se llama `exportarProductosANubeAPI([skuExport], tiendas)` (~línea 491), pasar las dims:

```tsx
                    const r = await exportarProductosANubeAPI([skuExport], tiendas, { nubePeso, nubeProfundidad, nubeAncho, nubeAlto });
```

- [ ] **Step 7: Verificar tipos**

Run: `cd supermaster-frontend && npx tsc --noEmit`
Expected: exit 0.

- [ ] **Step 8: Commit**

```bash
git add supermaster-frontend/src/app/productos/productosService.ts supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(nube): inputs de peso/dimensiones (default en alta, del canal en edición) enviados al exportar"
```

---

## Verificación manual final

- **Promo:** producto con precio inflado/promocional en Nube → la tarjeta de Nube en "Canales de venta" muestra "Precio" + "Promo".
- **Dims alta:** crear producto con Nube tildado → inputs en 0.050/8/5/5; al subir, Nube recibe esos valores (no el placeholder).
- **Dims edición:** abrir un producto publicado en Nube → los inputs traen las dims guardadas en Nube; cambiarlas y guardar → se actualizan en Nube (HOGAR y GASTRO).
- **Sin Nube tildado:** el bloque de dims no se muestra; el resto del modal intacto.
