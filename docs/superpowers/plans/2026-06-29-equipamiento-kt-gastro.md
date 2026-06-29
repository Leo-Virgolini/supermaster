# EQUIPAMIENTO en KT GASTRO — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Al subir a Tienda Nube **KT GASTRO** un producto de categoría **EQUIPAMIENTO**, agregar `*` pegado al final del título y un bullet "ENVIO A COTIZAR" al final de la descripción.

**Architecture:** Lógica pura en una clase `NubeEquipamiento` (detección + transformaciones), un flag `@Transient` en `Producto` seteado por tienda en `NubeExportService`, y los builders de alta/update de Nube aplicando las transformaciones. Solo backend.

**Tech Stack:** Java 25 / Spring Boot 4 / JUnit 5 + AssertJ.

## Global Constraints

- `mvn -o test` (offline, `mvn` del PATH, NO `mvnw`). Compilar: `mvn -o -q compile`.
- Solo canal **KT GASTRO** (`TiendaNubeService.STORE_GASTRO == "KT GASTRO"`); no afecta KT HOGAR, ML, Dux, ni el dato persistido.
- `*` **pegado** al título (`titulo + "*"`); bullet en **texto plano** `<ul><li>ENVIO A COTIZAR</li></ul>`. Ambos **idempotentes**.
- OSIV off: la detección toca asociaciones LAZY (`clasif*.getPadre()`) y corre dentro del `@Transactional(readOnly=true)` de `NubeExportService.exportar` (ya es así).
- Trabajar en `main`. Commits terminan con `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. `git add` solo de archivos específicos (hay WIP de Dux en el working tree que NO se toca), nunca `-A` ni `.superpowers/`.

## File Structure

- Create: `apis/nube/service/NubeEquipamiento.java` — detección + helpers de título/descripción.
- Create test: `apis/nube/service/NubeEquipamientoTest.java` (en `src/test/...`).
- Modify: `dominio/producto/entity/Producto.java` — `@Transient boolean equipamientoGastro`.
- Modify: `apis/nube/service/NubeExportService.java` — setear el flag por tienda.
- Modify: `apis/nube/service/NubeProductoPayloadBuilder.java` — alta (name + description).
- Modify: `apis/nube/service/TiendaNubeService.java` — update PATCH (name + description).
- Modify test: `apis/nube/service/NubeProductoPayloadBuilderTest.java` — casos con flag.

---

### Task 1: `NubeEquipamiento` (detección + helpers) con tests

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeEquipamiento.java`
- Create: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/service/NubeEquipamientoTest.java`

**Interfaces:**
- Produces:
  - `static boolean esEquipamiento(Producto p)`
  - `static String tituloConSufijo(String titulo, boolean eq)`
  - `static String descripcionConBullet(String desc, boolean eq)`

- [ ] **Step 1: Escribir el test (falla)**

```java
package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.dominio.clasif_gastro.entity.ClasifGastro;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NubeEquipamientoTest {

    private Producto conGastro(String... nombresDesdeRaiz) {
        // Construye la cadena padre→hijo y asigna la hoja al producto.
        Producto p = new Producto();
        ClasifGastro padre = null;
        for (String nombre : nombresDesdeRaiz) {
            ClasifGastro n = new ClasifGastro();
            n.setNombre(nombre);
            n.setPadre(padre);
            padre = n;
        }
        p.setClasifGastro(padre);
        return p;
    }

    @Test
    void esEquipamiento_hojaGastro() {
        assertThat(NubeEquipamiento.esEquipamiento(conGastro("EQUIPAMIENTO"))).isTrue();
    }

    @Test
    void esEquipamiento_ancestroGastro() {
        // raíz EQUIPAMIENTO, hoja otra cosa → true (cualquier nivel)
        assertThat(NubeEquipamiento.esEquipamiento(conGastro("EQUIPAMIENTO", "HORNOS"))).isTrue();
    }

    @Test
    void esEquipamiento_caseInsensitive() {
        assertThat(NubeEquipamiento.esEquipamiento(conGastro("equipamiento"))).isTrue();
    }

    @Test
    void esEquipamiento_fallbackGral() {
        Producto p = new Producto();
        ClasifGral g = new ClasifGral();
        g.setNombre("EQUIPAMIENTO");
        p.setClasifGral(g);
        assertThat(NubeEquipamiento.esEquipamiento(p)).isTrue();
    }

    @Test
    void esEquipamiento_falseSinMatch() {
        assertThat(NubeEquipamiento.esEquipamiento(conGastro("MENAJE", "OLLAS"))).isFalse();
        assertThat(NubeEquipamiento.esEquipamiento(new Producto())).isFalse();
    }

    @Test
    void tituloConSufijo() {
        assertThat(NubeEquipamiento.tituloConSufijo("Olla", true)).isEqualTo("Olla*");
        assertThat(NubeEquipamiento.tituloConSufijo("Olla*", true)).isEqualTo("Olla*"); // idempotente
        assertThat(NubeEquipamiento.tituloConSufijo("Olla", false)).isEqualTo("Olla");  // no aplica
    }

    @Test
    void descripcionConBullet() {
        assertThat(NubeEquipamiento.descripcionConBullet("<p>x</p>", true))
                .isEqualTo("<p>x</p><ul><li>ENVIO A COTIZAR</li></ul>");
        assertThat(NubeEquipamiento.descripcionConBullet("<p>x</p><ul><li>ENVIO A COTIZAR</li></ul>", true))
                .isEqualTo("<p>x</p><ul><li>ENVIO A COTIZAR</li></ul>"); // idempotente
        assertThat(NubeEquipamiento.descripcionConBullet("<p>x</p>", false)).isEqualTo("<p>x</p>"); // no aplica
        assertThat(NubeEquipamiento.descripcionConBullet(null, true)).isEqualTo("<ul><li>ENVIO A COTIZAR</li></ul>"); // base vacía
    }
}
```

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `cd supermaster-backend && mvn -o -q -Dtest=NubeEquipamientoTest test`
Expected: FAIL (no compila: `NubeEquipamiento` no existe).

- [ ] **Step 3: Implementar `NubeEquipamiento`**

```java
package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.dominio.clasif_gastro.entity.ClasifGastro;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;

import java.util.List;

/** Reglas de "EQUIPAMIENTO" para KT GASTRO: detección + sufijo de título + bullet de descripción. */
public final class NubeEquipamiento {

    private NubeEquipamiento() {}

    private static final String TEXTO = "ENVIO A COTIZAR";
    private static final String BULLET = "<ul><li>" + TEXTO + "</li></ul>";

    /** True si algún nodo de la categoría de Nube (gastro si existe, sino general) se llama "EQUIPAMIENTO". */
    public static boolean esEquipamiento(Producto p) {
        List<String> nombres = p.getClasifGastro() != null
                ? NubeCategoriaRuta.aplanar(p.getClasifGastro(), ClasifGastro::getPadre, ClasifGastro::getNombre)
                : p.getClasifGral() != null
                    ? NubeCategoriaRuta.aplanar(p.getClasifGral(), ClasifGral::getPadre, ClasifGral::getNombre)
                    : List.of();
        return nombres.stream().anyMatch(n -> n != null && "EQUIPAMIENTO".equalsIgnoreCase(n.trim()));
    }

    /** Agrega "*" pegado al final del título si {@code eq} y no termina ya en "*". */
    public static String tituloConSufijo(String titulo, boolean eq) {
        if (!eq || titulo == null) return titulo;
        return titulo.endsWith("*") ? titulo : titulo + "*";
    }

    /** Agrega el bullet "ENVIO A COTIZAR" al final de la descripción si {@code eq} (idempotente). */
    public static String descripcionConBullet(String desc, boolean eq) {
        if (!eq) return desc;
        if (desc != null && desc.contains(TEXTO)) return desc;
        return (desc == null ? "" : desc) + BULLET;
    }
}
```

- [ ] **Step 4: Correr el test y verificar que pasa**

Run: `cd supermaster-backend && mvn -o -q -Dtest=NubeEquipamientoTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeEquipamiento.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/service/NubeEquipamientoTest.java
git commit -m "feat(nube): NubeEquipamiento (detección EQUIPAMIENTO + sufijo título + bullet descripción)"
```

---

### Task 2: Cablear el flag y aplicar las transformaciones (alta + update)

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/entity/Producto.java`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeExportService.java`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeProductoPayloadBuilder.java`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/TiendaNubeService.java`
- Modify test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/service/NubeProductoPayloadBuilderTest.java`

**Interfaces:**
- Consumes: `NubeEquipamiento.esEquipamiento/tituloConSufijo/descripcionConBullet` (Task 1); `Producto.isEquipamientoGastro()`/`setEquipamientoGastro(boolean)` (este task).

- [ ] **Step 1: Agregar el flag `@Transient` en `Producto`**

Junto a los otros campos `@Transient` de datos de canal (descripcionMl/descripcionNube/mlCategoryId/mlAtributos), agregar:
```java
    @Transient
    private boolean equipamientoGastro;
```
(Lombok genera `isEquipamientoGastro()` y `setEquipamientoGastro(boolean)`.)

- [ ] **Step 2: Setear el flag por tienda en `NubeExportService`**

En el loop de `exportar`, justo después de `producto.setDescripcionNube(destino.descripcion());` (~línea 64), agregar:
```java
                producto.setEquipamientoGastro(
                        TiendaNubeService.STORE_GASTRO.equals(tienda) && NubeEquipamiento.esEquipamiento(producto));
```
(Se setea en cada iteración —también a false para KT HOGAR— porque el mismo `producto` se reutiliza para ambas tiendas.)

- [ ] **Step 3: Aplicar en el alta (`NubeProductoPayloadBuilder.construir`)**

Reemplazar las líneas del `name` y la descripción (líneas 21-23):
```java
        payload.put("name", Map.of("es", p.getTituloNube() != null ? p.getTituloNube() : ""));
        String descNube = NubeDescripcionBuilder.construir(p);
        if (descNube != null && !descNube.isBlank()) payload.put("description", Map.of("es", descNube));
```
por:
```java
        String nombre = NubeEquipamiento.tituloConSufijo(p.getTituloNube() != null ? p.getTituloNube() : "", p.isEquipamientoGastro());
        payload.put("name", Map.of("es", nombre));
        String descNube = NubeEquipamiento.descripcionConBullet(NubeDescripcionBuilder.construir(p), p.isEquipamientoGastro());
        if (descNube != null && !descNube.isBlank()) payload.put("description", Map.of("es", descNube));
```

- [ ] **Step 4: Aplicar en el update (PATCH en `TiendaNubeService`)**

Reemplazar las líneas del `name` y la descripción del body (líneas 966-968):
```java
            body.put("name", Map.of("es", producto.getTituloNube() != null ? producto.getTituloNube() : ""));
            String descNube = NubeDescripcionBuilder.construir(producto);
            if (descNube != null && !descNube.isBlank()) body.put("description", Map.of("es", descNube));
```
por:
```java
            String nombre = NubeEquipamiento.tituloConSufijo(producto.getTituloNube() != null ? producto.getTituloNube() : "", producto.isEquipamientoGastro());
            body.put("name", Map.of("es", nombre));
            String descNube = NubeEquipamiento.descripcionConBullet(NubeDescripcionBuilder.construir(producto), producto.isEquipamientoGastro());
            if (descNube != null && !descNube.isBlank()) body.put("description", Map.of("es", descNube));
```

- [ ] **Step 5: Test del alta con el flag**

Agregar a `NubeProductoPayloadBuilderTest` (seguir el patrón de los tests existentes del archivo; `descripcionNube` se setea con `p.setDescripcionNube(...)`, el name con `p.setTituloNube(...)`):
```java
    @Test
    void equipamientoGastro_agregaSufijoYBullet() {
        Producto p = new Producto();
        p.setSku("EQ-1");
        p.setTituloNube("Horno industrial");
        p.setDescripcionNube("<p>Detalle</p>");
        p.setEquipamientoGastro(true);

        Map<String, Object> body = NubeProductoPayloadBuilder.construir(p, new BigDecimal("100"), null, null, null);

        @SuppressWarnings("unchecked")
        Map<String, Object> name = (Map<String, Object>) body.get("name");
        assertThat(name.get("es")).isEqualTo("Horno industrial*");
        @SuppressWarnings("unchecked")
        Map<String, Object> desc = (Map<String, Object>) body.get("description");
        assertThat((String) desc.get("es")).contains("ENVIO A COTIZAR");
    }

    @Test
    void sinEquipamiento_noTocaTituloNiDescripcion() {
        Producto p = new Producto();
        p.setSku("EQ-2");
        p.setTituloNube("Horno industrial");
        p.setDescripcionNube("<p>Detalle</p>");
        p.setEquipamientoGastro(false);

        Map<String, Object> body = NubeProductoPayloadBuilder.construir(p, new BigDecimal("100"), null, null, null);

        @SuppressWarnings("unchecked")
        Map<String, Object> name = (Map<String, Object>) body.get("name");
        assertThat(name.get("es")).isEqualTo("Horno industrial");
        @SuppressWarnings("unchecked")
        Map<String, Object> desc = (Map<String, Object>) body.get("description");
        assertThat((String) desc.get("es")).doesNotContain("ENVIO A COTIZAR");
    }
```
(Ajustar imports del test si faltan: `java.math.BigDecimal`, `Producto`, `Map`. Verificar la firma real de `construir` por si el test existente ya la usa con otros args.)

- [ ] **Step 6: Compilar y correr los tests de Nube**

Run: `cd supermaster-backend && mvn -o -q compile`
Expected: BUILD SUCCESS.

Run: `cd supermaster-backend && mvn -o -q -Dtest=NubeEquipamientoTest,NubeProductoPayloadBuilderTest test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/entity/Producto.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeExportService.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeProductoPayloadBuilder.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/TiendaNubeService.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/service/NubeProductoPayloadBuilderTest.java
git commit -m "feat(nube): aplica EQUIPAMIENTO en KT GASTRO (sufijo * en título + bullet ENVIO A COTIZAR) en alta y update"
```

---

### Task 3: Aviso en el modal (frontend)

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`

**Interfaces:**
- Consumes: estados `clasifGastroDisplay`, `clasifGralDisplay`, `subirKtGastro` (ya existen).

- [ ] **Step 1: Computar `esEquipamiento` (espejo del backend)**

Cerca de los otros `useMemo`/derivados del componente (p.ej. junto a `mlAtributosDef`), agregar:
```tsx
    // EQUIPAMIENTO: algún nodo de la clasificación de Nube (gastro si existe, sino general) es "EQUIPAMIENTO".
    const esEquipamiento = useMemo(() => {
        const ruta = clasifGastroDisplay || clasifGralDisplay || "";
        return ruta.split(">").some(seg => seg.trim().toUpperCase() === "EQUIPAMIENTO");
    }, [clasifGastroDisplay, clasifGralDisplay]);
```
(`useMemo` ya está importado en el archivo.)

- [ ] **Step 2: Mostrar el aviso dentro de la sección KT GASTRO**

En la sección **Tienda Nube · KT GASTRO** (`{subirKtGastro && (` con legend "Tienda Nube · KT GASTRO"), justo después del `<legend>`, agregar:
```tsx
                            {esEquipamiento && (
                                <div className="mb-4 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-800 dark:border-amber-700 dark:bg-amber-900/20 dark:text-amber-300">
                                    Producto de <b>EQUIPAMIENTO</b>: al subir a KT GASTRO se le agregará <b>*</b> al final del título y un bullet <b>&quot;ENVIO A COTIZAR&quot;</b> a la descripción.
                                </div>
                            )}
```
(Como la sección solo se renderiza cuando `subirKtGastro` está tildado, el aviso aparece exactamente cuando KT GASTRO está seleccionado y el producto es EQUIPAMIENTO.)

- [ ] **Step 3: Verificar tipos**

Run: `cd supermaster-frontend && npx tsc --noEmit`
Expected: exit 0.

- [ ] **Step 4: Revisión manual**

Con KT GASTRO tildado y un producto cuya clasif (gastro o general) tenga un nodo "EQUIPAMIENTO" → aparece el aviso ámbar. Si no es EQUIPAMIENTO, o KT GASTRO no está tildado → no aparece.

- [ ] **Step 5: Commit**

```bash
git add supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(front): aviso EQUIPAMIENTO en la sección KT GASTRO del modal"
```

---

## Self-Review

1. **Cobertura de la spec:** detección (gastro/gral, cualquier nivel) → Task 1 `esEquipamiento`; sufijo `*` pegado idempotente → `tituloConSufijo`; bullet idempotente texto plano → `descripcionConBullet`; solo KT GASTRO → Task 2 Step 2 (`STORE_GASTRO.equals(tienda)`); alta → Step 3; update → Step 4; flag `@Transient` → Step 1; **aviso en el modal** → Task 3. ✅
2. **Placeholders:** código completo en cada paso.
3. **Consistencia de tipos:** `NubeEquipamiento.esEquipamiento(Producto)`, `tituloConSufijo(String,boolean)`, `descripcionConBullet(String,boolean)` definidos en Task 1 y usados igual en Task 2; `isEquipamientoGastro()/setEquipamientoGastro(boolean)` (Lombok) en Task 2. `STORE_GASTRO` ya existe.
4. **No afecta** KT HOGAR (flag false), ML, Dux ni el dato persistido (flag transient, título sin tocar en BD).
