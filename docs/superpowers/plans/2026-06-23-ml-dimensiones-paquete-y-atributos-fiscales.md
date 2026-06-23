# ML: dimensiones de paquete + atributos fiscales — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que la publicación a Mercado Libre incluya las dimensiones del paquete (`SELLER_PACKAGE_*`) y los atributos fiscales (`VALUE_ADDED_TAX`, `IMPORT_DUTY`), cargando las dimensiones en el form de producto y guardándolas en `productos`.

**Architecture:** Backend en 2 capas: (1) persistir 4 dimensiones nuevas en `productos` (entidad + DTOs + mapper); (2) emitir los 6 atributos en `MlItemPayloadBuilder`. Frontend: sección nueva en `ProductoFormModal` con validación al subir a ML.

**Tech Stack:** Spring Boot / Java 25 (backend, `mvn -o test`), Next.js 16 / React 19 / TS (frontend, `npx tsc --noEmit`). MySQL con `ddl-auto=validate`.

## Global Constraints

- Trabajar en `main`. Backend offline: usar `mvn` instalado (NO `./mvnw`, falla por red): `mvn -o -q test-compile` / `mvn -o -Dtest=Clase test`. Frontend: `npx tsc --noEmit` desde `supermaster-frontend/`.
- **NO ejecutar nada contra las APIs reales de ML/Dux/Nube.** Solo tests offline (POJO/`@TempDir`/Mockito) y typecheck.
- `ddl-auto=validate`: la columna debe existir en la BD antes de arrancar; el script SQL va en `src/main/resources/db/` y lo aplica el usuario a mano.
- **Orden de los campos:** agregar los 4 campos **al final** de cada record/constructor, en el orden fijo **`mlPaqAlto, mlPaqAncho, mlPaqLargo, mlPaqPeso`**. El `ProductoMapper.toDTO` y el constructor de `ProductoConPreciosDTO` son **posicionales manuales** — alinear agregando los 4 al final en el mismo orden.
- **Unidades/formato ML (exacto, de la doc):** dimensiones en `"N cm"`, peso en `"N g"`, **solo enteros** (redondear); peso = kg×1000. `VALUE_ADDED_TAX`/`IMPORT_DUTY` son listas cerradas con `value_id`+`value_name` (nombre con espacio: `"21 %"`).
- Commits terminan EXACTAMENTE con: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

---

### Task 1: Persistir las 4 dimensiones del paquete en `productos`

**Files:**
- Create: `supermaster-backend/src/main/resources/db/2026-06-23-ml-dimensiones-paquete.sql`
- Modify: `supermaster-backend/.../dominio/producto/entity/Producto.java`
- Modify: los DTOs de producto — `ProductoDTO`, `ProductoCreateDTO`, `ProductoUpdateDTO`, `ProductoConPreciosDTO`, `ProductoPatchDTO`
- Modify: `supermaster-backend/.../dominio/producto/mapper/ProductoMapper.java`
- Modify: `supermaster-backend/.../dominio/producto/service/ProductoServiceImpl.java` (`aplicarPatch`)
- Modify: tests que usan constructores posicionales de esos records (los señala el compilador)

**Interfaces:**
- Produces: en `Producto` los getters `getMlPaqAlto()`, `getMlPaqAncho()`, `getMlPaqLargo()`, `getMlPaqPeso()` → `BigDecimal` (nullable). Mismos 4 nombres como componentes (`BigDecimal`) en cada DTO, al final.

- [ ] **Step 1: Script SQL**

Crear `src/main/resources/db/2026-06-23-ml-dimensiones-paquete.sql`:
```sql
-- Dimensiones del paquete de envío para Mercado Libre (alto/ancho/largo en cm, peso en kg).
-- Nullable: solo se cargan para productos que se publican en ML.
ALTER TABLE productos
  ADD COLUMN ml_paq_alto  DECIMAL(6,2) NULL,
  ADD COLUMN ml_paq_ancho DECIMAL(6,2) NULL,
  ADD COLUMN ml_paq_largo DECIMAL(6,2) NULL,
  ADD COLUMN ml_paq_peso  DECIMAL(8,3) NULL;
```

- [ ] **Step 2: Campos en la entidad `Producto`**

Junto al campo `iva` existente (`@Column(name="iva", ...) private BigDecimal iva;`), agregar 4 campos análogos (nullable, sin `@NotNull`):
```java
@Column(name = "ml_paq_alto", precision = 6, scale = 2)
private BigDecimal mlPaqAlto;

@Column(name = "ml_paq_ancho", precision = 6, scale = 2)
private BigDecimal mlPaqAncho;

@Column(name = "ml_paq_largo", precision = 6, scale = 2)
private BigDecimal mlPaqLargo;

@Column(name = "ml_paq_peso", precision = 8, scale = 3)
private BigDecimal mlPaqPeso;
```
(Lombok `@Getter/@Setter` ya generan getters/setters; si la clase los escribe a mano, replicar el patrón de `iva`.)

- [ ] **Step 3: Agregar los 4 componentes a cada DTO**

En cada record (`ProductoDTO`, `ProductoCreateDTO`, `ProductoUpdateDTO`, `ProductoConPreciosDTO`, `ProductoPatchDTO`), agregar **al final** los 4 componentes `BigDecimal mlPaqAlto, BigDecimal mlPaqAncho, BigDecimal mlPaqLargo, BigDecimal mlPaqPeso` (sin validación, son opcionales — no agregar `@NotNull`; en CreateDTO/UpdateDTO se puede poner `@PositiveOrZero` opcional si los demás numéricos lo usan, replicando ese patrón). Mantener el mismo orden en los 5.

- [ ] **Step 4: Alinear el mapper**

En `ProductoMapper.toDTO` (construcción manual posicional) y en cualquier constructor manual de `ProductoConPreciosDTO`, agregar al final los 4 argumentos `entity.getMlPaqAlto(), entity.getMlPaqAncho(), entity.getMlPaqLargo(), entity.getMlPaqPeso()` en el mismo orden. Si MapStruct mapea por nombre para otros DTOs (toEntity/update), los 4 campos se mapean solos por nombre.

- [ ] **Step 5: `aplicarPatch`**

En `ProductoServiceImpl.aplicarPatch`, contemplar los 4 campos siguiendo el patrón de los demás numéricos (p. ej. si usa `if (patch.campo() != null) entity.setCampo(patch.campo())`, replicar para los 4).

- [ ] **Step 6: Compilar tests y arreglar constructores posicionales**

Run (desde `supermaster-backend/`): `mvn -o -q test-compile`
Expected: errores de compilación en tests que hacen `new ProductoXDTO(...)` (p. ej. `RecalculoAutomaticoIntegrationTest`, `buildProductoUpdate`). Arreglarlos agregando al final los 4 argumentos (usar `null` donde no aplique, o valores `BigDecimal` si el test lo amerita). Repetir hasta `BUILD SUCCESS`.

- [ ] **Step 7: Correr tests y commit**

Run: `mvn -o -Dtest=RecalculoAutomaticoIntegrationTest test`
Expected: `Tests run: 49, Failures: 0`.
```bash
git add supermaster-backend/src/main/resources/db/2026-06-23-ml-dimensiones-paquete.sql \
        supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/
git commit -m "feat(productos): persistir dimensiones del paquete ML (ml_paq_alto/ancho/largo/peso)"
```

---

### Task 2: Emitir los 6 atributos en el payload de ML

**Files:**
- Modify: `supermaster-backend/.../apis/ml/service/MlItemPayloadBuilder.java`
- Test: `supermaster-backend/src/test/java/.../apis/ml/service/MlItemPayloadBuilderTest.java` (crear si no existe)

**Interfaces:**
- Consumes: `Producto.getMlPaqAlto/Ancho/Largo/Peso()` y `getIva()` (Task 1).
- Produces: `MlItemPayloadBuilder.construir(...)` agrega a `attributes` los `SELLER_PACKAGE_*` (si las 4 dimensiones != null), `VALUE_ADDED_TAX` (si el iva mapea) e `IMPORT_DUTY` (siempre).

- [ ] **Step 1: Test del payload (falla primero)**

Crear `MlItemPayloadBuilderTest.java`. Usa POJOs, sin Spring:
```java
package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MlItemPayloadBuilderTest {

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> attrs(Map<String, Object> payload) {
        return (List<Map<String, Object>>) payload.get("attributes");
    }
    private static Map<String, Object> attr(Map<String, Object> payload, String id) {
        return attrs(payload).stream().filter(a -> id.equals(a.get("id"))).findFirst().orElse(null);
    }

    @Test
    void incluyeDimensionesEnteras_yAtributosFiscales() {
        Producto p = new Producto();
        p.setSku("CUT-001");
        p.setIva(new BigDecimal("21.000"));
        p.setMlPaqAlto(new BigDecimal("6"));
        p.setMlPaqAncho(new BigDecimal("25"));
        p.setMlPaqLargo(new BigDecimal("31"));
        p.setMlPaqPeso(new BigDecimal("0.214")); // 214 g

        var payload = MlItemPayloadBuilder.construir(p, "MLA30083", new BigDecimal("100"), 0, List.of(), "Fam");

        assertEquals("6 cm",   attr(payload, "SELLER_PACKAGE_HEIGHT").get("value_name"));
        assertEquals("25 cm",  attr(payload, "SELLER_PACKAGE_WIDTH").get("value_name"));
        assertEquals("31 cm",  attr(payload, "SELLER_PACKAGE_LENGTH").get("value_name"));
        assertEquals("214 g",  attr(payload, "SELLER_PACKAGE_WEIGHT").get("value_name"));

        var vat = attr(payload, "VALUE_ADDED_TAX");
        assertEquals("48405909", vat.get("value_id"));
        assertEquals("21 %",     vat.get("value_name"));

        var imp = attr(payload, "IMPORT_DUTY");
        assertEquals("49553239", imp.get("value_id"));
        assertEquals("0 %",      imp.get("value_name"));
    }

    @Test
    void omiteDimensiones_siFaltaAlguna_yIva105() {
        Producto p = new Producto();
        p.setSku("X");
        p.setIva(new BigDecimal("10.5"));
        p.setMlPaqAlto(new BigDecimal("6")); // faltan las otras 3
        var payload = MlItemPayloadBuilder.construir(p, "MLA1", new BigDecimal("1"), 0, List.of(), "F");

        assertNull(attr(payload, "SELLER_PACKAGE_HEIGHT"));
        assertEquals("10.5 %", attr(payload, "VALUE_ADDED_TAX").get("value_name"));
        assertEquals("48405908", attr(payload, "VALUE_ADDED_TAX").get("value_id"));
    }
}
```

- [ ] **Step 2: Verificar que falla**

Run: `mvn -o -Dtest=MlItemPayloadBuilderTest test`
Expected: FAIL (los atributos aún no se agregan; `attr(...)` devuelve null → NPE/assert).

- [ ] **Step 3: Implementar en `MlItemPayloadBuilder`**

Importar `java.math.RoundingMode`. Justo después de `attributes.add(Map.of("id", "SELLER_SKU", ...));` y antes de `payload.put("attributes", attributes);`, agregar:
```java
// Dimensiones del paquete de envío (ML exige enteros, cm y g). Solo si están las 4.
if (p.getMlPaqAlto() != null && p.getMlPaqAncho() != null
        && p.getMlPaqLargo() != null && p.getMlPaqPeso() != null) {
    attributes.add(Map.of("id", "SELLER_PACKAGE_HEIGHT", "value_name", cm(p.getMlPaqAlto())));
    attributes.add(Map.of("id", "SELLER_PACKAGE_WIDTH",  "value_name", cm(p.getMlPaqAncho())));
    attributes.add(Map.of("id", "SELLER_PACKAGE_LENGTH", "value_name", cm(p.getMlPaqLargo())));
    attributes.add(Map.of("id", "SELLER_PACKAGE_WEIGHT", "value_name", gramos(p.getMlPaqPeso())));
}
// IVA: lista cerrada de ML; se mapea el iva del producto. Se omite si no es 0/10.5/21/27.
Map<String, String> vat = mapearIva(p.getIva());
if (vat != null) {
    attributes.add(Map.of("id", "VALUE_ADDED_TAX", "value_id", vat.get("id"), "value_name", vat.get("name")));
}
// Impuesto de importación: siempre 0 %.
attributes.add(Map.of("id", "IMPORT_DUTY", "value_id", "49553239", "value_name", "0 %"));
```
Y agregar los helpers privados estáticos al final de la clase:
```java
private static String cm(BigDecimal valor) {
    return valor.setScale(0, RoundingMode.HALF_UP).toPlainString() + " cm";
}
private static String gramos(BigDecimal kg) {
    return kg.multiply(BigDecimal.valueOf(1000)).setScale(0, RoundingMode.HALF_UP).toPlainString() + " g";
}
private static Map<String, String> mapearIva(BigDecimal iva) {
    if (iva == null) return null;
    if (iva.compareTo(new BigDecimal("0"))    == 0) return Map.of("id", "48405907", "name", "0 %");
    if (iva.compareTo(new BigDecimal("10.5")) == 0) return Map.of("id", "48405908", "name", "10.5 %");
    if (iva.compareTo(new BigDecimal("21"))   == 0) return Map.of("id", "48405909", "name", "21 %");
    if (iva.compareTo(new BigDecimal("27"))   == 0) return Map.of("id", "48405910", "name", "27 %");
    return null;
}
```

- [ ] **Step 4: Verificar que pasa y commit**

Run: `mvn -o -Dtest=MlItemPayloadBuilderTest test`
Expected: `Tests run: 2, Failures: 0`.
```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlItemPayloadBuilder.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/MlItemPayloadBuilderTest.java
git commit -m "feat(ml): agregar SELLER_PACKAGE_* + VALUE_ADDED_TAX + IMPORT_DUTY al payload de items"
```

---

### Task 3: Sección de dimensiones en el form + envío

**Files:**
- Modify: `supermaster-frontend/src/app/productos/types.ts`
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`

**Interfaces:**
- Consumes: el backend de Task 1 acepta/devuelve `mlPaqAlto/mlPaqAncho/mlPaqLargo/mlPaqPeso` en los DTOs de producto.

- [ ] **Step 1: Tipos**

En `types.ts`, agregar a los tipos de producto (donde está `iva`) los 4 campos opcionales:
```ts
mlPaqAlto?: number | null;
mlPaqAncho?: number | null;
mlPaqLargo?: number | null;
mlPaqPeso?: number | null;
```
(En los tipos de creación/edición/patch que correspondan, replicando cómo está tipado `iva`/`costo`.)

- [ ] **Step 2: Estado + precarga en `ProductoFormModal`**

Agregar 4 `useState` (junto a los de dimensiones existentes), tipados como los otros numéricos del form (`number | ""`):
```tsx
const [mlPaqAlto, setMlPaqAlto] = useState<number | "">("");
const [mlPaqAncho, setMlPaqAncho] = useState<number | "">("");
const [mlPaqLargo, setMlPaqLargo] = useState<number | "">("");
const [mlPaqPeso, setMlPaqPeso] = useState<number | "">("");
```
En el `useEffect` de precarga (rama `if (producto)`), agregar:
```tsx
setMlPaqAlto(producto.mlPaqAlto ?? "");
setMlPaqAncho(producto.mlPaqAncho ?? "");
setMlPaqLargo(producto.mlPaqLargo ?? "");
setMlPaqPeso(producto.mlPaqPeso ?? "");
```

- [ ] **Step 3: Sección de inputs en el JSX**

Agregar una `fieldset`/sección (usando `sectionClassName`/`fieldLabelClassName`/`inputBaseClassName` como las demás), titulada **"Paquete para Mercado Libre (envío)"**, con un tooltip (mismo patrón `InformationCircleIcon`+`Tooltip`): "ML exige las dimensiones del paquete para publicar. Se envían en cm y gramos, redondeadas a enteros." Y 4 inputs `type="number"`:
```tsx
<label className="block">
    <span className={fieldLabelClassName}>Alto (cm){subirMl && <span className="ml-0.5 font-bold text-red-600">*</span>}</span>
    <input type="number" min={0} className={`${inputBaseClassName} ${formErrors.mlPaqAlto ? inputErrorClassName : ""}`}
        value={mlPaqAlto} onChange={e => { setMlPaqAlto(e.target.value === "" ? "" : Number(e.target.value)); if (formErrors.mlPaqAlto) setFormErrors(p => ({ ...p, mlPaqAlto: "" })); }} />
    {formErrors.mlPaqAlto && <p className="mt-1 text-xs text-red-500">{formErrors.mlPaqAlto}</p>}
</label>
```
Repetir para Ancho (cm), Largo (cm) y Peso (kg) — `mlPaqAncho`/`mlPaqLargo`/`mlPaqPeso`, con sus `formErrors.*` (Peso con label "Peso (kg)").

- [ ] **Step 4: Validación al subir a ML**

En `validateForm`, agregar: si `subirMl` está marcado, exigir las 4 dimensiones:
```tsx
if (subirMl) {
    if (mlPaqAlto === "" || Number(mlPaqAlto) <= 0) errores.mlPaqAlto = "Requerido para subir a ML";
    if (mlPaqAncho === "" || Number(mlPaqAncho) <= 0) errores.mlPaqAncho = "Requerido para subir a ML";
    if (mlPaqLargo === "" || Number(mlPaqLargo) <= 0) errores.mlPaqLargo = "Requerido para subir a ML";
    if (mlPaqPeso === "" || Number(mlPaqPeso) <= 0) errores.mlPaqPeso = "Requerido para subir a ML";
}
```
(Usar el nombre exacto del acumulador de errores que ya use `validateForm`.)

- [ ] **Step 5: Incluir en el payload de alta y edición**

En el objeto que se manda en `handleCreate` (create payload) y en `handleGuardarEdicion` (update/patch payload), agregar los 4 campos, convirtiendo `""` a `null`:
```tsx
mlPaqAlto: mlPaqAlto === "" ? null : Number(mlPaqAlto),
mlPaqAncho: mlPaqAncho === "" ? null : Number(mlPaqAncho),
mlPaqLargo: mlPaqLargo === "" ? null : Number(mlPaqLargo),
mlPaqPeso: mlPaqPeso === "" ? null : Number(mlPaqPeso),
```

- [ ] **Step 6: Typecheck y commit**

Run (desde `supermaster-frontend/`): `npx tsc --noEmit`
Expected: 0 errores.
```bash
git add supermaster-frontend/src/app/productos/types.ts supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(front/productos): seccion de dimensiones de paquete ML + validacion al subir"
```

---

## Verificación final
- [ ] Backend: `mvn -o test` (o al menos `MlItemPayloadBuilderTest` + `RecalculoAutomaticoIntegrationTest`) en verde.
- [ ] Frontend: `npx tsc --noEmit` sin errores.
- [ ] **Usuario:** aplicar el SQL `2026-06-23-ml-dimensiones-paquete.sql` antes de arrancar el backend (ddl-auto=validate).
- [ ] **Smoke (usuario):** crear/editar un producto con las 4 dimensiones, subir a ML real → ya no falla por `seller_package_*` ni `VALUE_ADDED_TAX/IMPORT_DUTY`; confirmar que ML acepta el formato de los atributos fiscales.

## Notas
- El recálculo de precio antes de subir ya quedó arreglado (commit `5c14fab`): no es parte de este plan.
- `VALUE_ADDED_TAX` solo soporta IVAs 0/10.5/21/27 (decisión de la spec); fuera de eso se omite y ML rechazará.
