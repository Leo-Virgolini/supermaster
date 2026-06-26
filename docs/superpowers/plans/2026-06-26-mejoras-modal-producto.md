# Mejoras al modal de producto + fixes ML/login — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mejorar la UX del modal de producto (mensaje de validación, asteriscos por canal, unidades de medida) y corregir la integración con Mercado Libre (no aplica, dimensiones del paquete, SKU en descripción), más quitar el toast ruidoso al expirar la sesión.

**Architecture:** Cambios independientes en dos capas. Backend (Java/Spring, TDD con JUnit+AssertJ): `MlItemPayloadBuilder`, `MlDescripcionBuilder`, `NubeDescripcionBuilder`. Frontend (Next.js/React, verificación con lint + manual): `ProductoFormModal.tsx` y un helper en `utils`.

**Tech Stack:** Java 25 / Spring Boot 4 / Maven / JUnit 5 / AssertJ (backend); Next.js / React / TypeScript / Tailwind (frontend).

## Global Constraints

- Backend: `ddl-auto=validate` — ningún cambio de schema en este plan (los campos físicos ya son strings ≤45).
- Backend: correr tests con `mvn -o test` (el wrapper `mvnw` falla por red en el sandbox). Trabajar offline.
- Backend: `Map.of(...)` NO admite valores `null` ni claves duplicadas — usar `LinkedHashMap` cuando un atributo lleve `value_name: null`.
- Frontend: se trabaja directo sobre `main`. Verificación: `npm run lint` desde `supermaster-frontend` + verificación manual en el modal.
- Commits frecuentes, uno por tarea. Mensajes en español, estilo `tipo(scope): ...`.
- Cierre de commits con la línea `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

---

### Task 1: SKU en las descripciones (Nube + ML)

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeDescripcionBuilder.java`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlDescripcionBuilder.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/service/NubeDescripcionBuilderTest.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/MlDescripcionBuilderTest.java`

**Interfaces:**
- Consumes: `Producto.getSku()` (String, `NOT NULL`).
- Produces: ninguna interfaz nueva; cambia el contenido del String que devuelven ambos `construir(Producto)`.

- [ ] **Step 1: Escribir los tests que fallan**

En `NubeDescripcionBuilderTest.java`, agregar:

```java
@Test
void incluyeSkuAlFinal_despuesDeCaracteristicas() {
    Producto p = new Producto();
    p.setSku("ABC123");
    Marca ma = new Marca(); ma.setNombre("Tramontina"); p.setMarca(ma);

    String html = NubeDescripcionBuilder.construir(p);

    assertThat(html).contains("<b><u><span style=\"color:#1e40af\">SKU:</span></u></b> ABC123");
    assertThat(html.indexOf("CARACTERÍSTICAS")).isLessThan(html.indexOf("SKU:"));
}
```

En `MlDescripcionBuilderTest.java`, agregar:

```java
@Test
void incluyeSkuAlFinal_despuesDeCaracteristicas() {
    Producto p = new Producto();
    p.setSku("ABC123");
    Marca marca = new Marca(); marca.setNombre("Tramontina"); p.setMarca(marca);

    String desc = MlDescripcionBuilder.construir(p);

    assertThat(desc).contains("SKU: ABC123");
    assertThat(desc.indexOf("CARACTERÍSTICAS")).isLessThan(desc.indexOf("SKU: ABC123"));
    assertThat(desc).doesNotContain("<");
}
```

- [ ] **Step 2: Correr los tests y verificar que fallan**

Run: `cd supermaster-backend && mvn -o test -Dtest=NubeDescripcionBuilderTest,MlDescripcionBuilderTest`
Expected: FAIL — los nuevos tests no encuentran `SKU:` en la salida.

- [ ] **Step 3: Implementar en NubeDescripcionBuilder**

En `NubeDescripcionBuilder.construir`, después de `sb.append("</ul>");` (línea 48) y antes de `return sb.toString();`, agregar:

```java
        if (p.getSku() != null && !p.getSku().isBlank())
            sb.append("<p>").append(label("SKU")).append(" ").append(escape(p.getSku().trim())).append("</p>");
```

- [ ] **Step 4: Implementar en MlDescripcionBuilder**

En `MlDescripcionBuilder.construir`, antes de `return sb.toString();` (línea 30), agregar:

```java
        if (p.getSku() != null && !p.getSku().isBlank())
            sb.append("SKU: ").append(p.getSku().trim()).append("\n");
```

- [ ] **Step 5: Correr los tests y verificar que pasan**

Run: `cd supermaster-backend && mvn -o test -Dtest=NubeDescripcionBuilderTest,MlDescripcionBuilderTest`
Expected: PASS (todos, incluidos los previos).

- [ ] **Step 6: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/nube/service/NubeDescripcionBuilder.java \
        supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlDescripcionBuilder.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/nube/service/NubeDescripcionBuilderTest.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/MlDescripcionBuilderTest.java
git commit -m "feat(desc): agrega SKU al final de la descripción (Nube + ML)"
```

---

### Task 2: "No aplica" se envía explícito a ML (value_id "-1")

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlItemPayloadBuilder.java:100-109`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/MlItemPayloadBuilderTest.java:237-250`

**Interfaces:**
- Consumes: `ProductoMlAtributo.isNoAplica()`, `.getAttributeId()`.
- Produces: en el array `attributes`, un atributo `noAplica` ahora aparece como `{id, value_id:"-1", value_name:null}` (antes se omitía).

- [ ] **Step 1: Reemplazar el test existente por el nuevo comportamiento**

En `MlItemPayloadBuilderTest.java`, reemplazar el test `atributos_noAplicaSeOmiteDelPayload` (líneas 237-250) por:

```java
@Test
void atributos_noAplicaSeEnviaComoNA() {
    Producto p = productoBase();
    ProductoMlAtributo na = new ProductoMlAtributo();
    na.setAttributeId("SHAPE"); na.setValueName(""); na.setNoAplica(true);
    ProductoMlAtributo ok = new ProductoMlAtributo();
    ok.setAttributeId("MODEL"); ok.setValueName("X100");
    p.getMlAtributos().addAll(List.of(na, ok));

    var attrs = MlItemPayloadBuilder.construirAtributos(p, Set.of());

    // El "No aplica" se envía explícito como N/A (value_id "-1", value_name null).
    var shape = attrs.stream().filter(a -> "SHAPE".equals(a.get("id"))).findFirst().orElseThrow();
    assertThat(shape.get("value_id")).isEqualTo("-1");
    assertThat(shape).containsKey("value_name");
    assertThat(shape.get("value_name")).isNull();
    assertThat(attrs).anySatisfy(a -> assertThat(a.get("id")).isEqualTo("MODEL"));
}
```

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `cd supermaster-backend && mvn -o test -Dtest=MlItemPayloadBuilderTest#atributos_noAplicaSeEnviaComoNA`
Expected: FAIL — el atributo `SHAPE` no está presente (hoy se omite).

- [ ] **Step 3: Implementar el envío de N/A**

En `MlItemPayloadBuilder.construirAtributos`, reemplazar el bloque del loop de guardados (líneas 100-109):

```java
        for (ProductoMlAtributo a : p.getMlAtributos()) {
            if (a.isNoAplica()) {
                continue; // el usuario marcó "No aplica": no se envía a ML
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getAttributeId());
            if (a.getValueId() != null && !a.getValueId().isBlank()) m.put("value_id", a.getValueId());
            m.put("value_name", a.getValueName());
            attributes.add(m);
        }
```

por:

```java
        for (ProductoMlAtributo a : p.getMlAtributos()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getAttributeId());
            if (a.isNoAplica()) {
                // "No aplica": ML lo registra como N/A enviando value_id "-1" y value_name null.
                m.put("value_id", "-1");
                m.put("value_name", null);
                attributes.add(m);
                continue;
            }
            if (a.getValueId() != null && !a.getValueId().isBlank()) m.put("value_id", a.getValueId());
            m.put("value_name", a.getValueName());
            attributes.add(m);
        }
```

(`LinkedHashMap` ya está importado en el archivo, línea 9.)

- [ ] **Step 4: Correr el test y verificar que pasa**

Run: `cd supermaster-backend && mvn -o test -Dtest=MlItemPayloadBuilderTest`
Expected: PASS (toda la clase).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlItemPayloadBuilder.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/MlItemPayloadBuilderTest.java
git commit -m "fix(ml): envía atributos 'No aplica' como N/A (value_id -1) en vez de omitirlos"
```

---

### Task 3: Dimensiones del paquete ML con mapeo natural

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlItemPayloadBuilder.java:75-80`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/MlItemPayloadBuilderTest.java:83-99`

**Interfaces:**
- Consumes: `Producto.getMlPaqAlto/Ancho/Largo/Peso()` (BigDecimal).
- Produces: `SELLER_PACKAGE_HEIGHT←mlPaqAlto`, `WIDTH←mlPaqAncho`, `LENGTH←mlPaqLargo`, `WEIGHT←mlPaqPeso`.

- [ ] **Step 1: Actualizar el test existente al mapeo natural**

En `MlItemPayloadBuilderTest.java`, en el test `incluyeDimensionesEnteras_yAtributosFiscales`, reemplazar las aserciones de dimensiones (líneas 95-99) por:

```java
        // Mapeo natural 1:1: HEIGHT←Alto(6), WIDTH←Ancho(25), LENGTH←Largo(31).
        assertEquals("6 cm",   attr(payload, "SELLER_PACKAGE_HEIGHT").get("value_name"));
        assertEquals("25 cm",  attr(payload, "SELLER_PACKAGE_WIDTH").get("value_name"));
        assertEquals("31 cm",  attr(payload, "SELLER_PACKAGE_LENGTH").get("value_name"));
        assertEquals("214 g",  attr(payload, "SELLER_PACKAGE_WEIGHT").get("value_name"));
```

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `cd supermaster-backend && mvn -o test -Dtest=MlItemPayloadBuilderTest#incluyeDimensionesEnteras_yAtributosFiscales`
Expected: FAIL — HEIGHT vale "25 cm" (mapeo viejo) en vez de "6 cm".

- [ ] **Step 3: Corregir el mapeo**

En `MlItemPayloadBuilder.construirAtributos`, reemplazar el bloque de dimensiones (líneas 75-80):

```java
            // El nombre de cada medida en la BD NO coincide con el de ML:
            // ML Alto (HEIGHT) = BD Ancho; ML Profundidad (LENGTH) = BD Alto; ML Ancho (WIDTH) = BD Largo.
            attributes.add(Map.of("id", "SELLER_PACKAGE_HEIGHT", "value_name", cm(p.getMlPaqAncho())));
            attributes.add(Map.of("id", "SELLER_PACKAGE_LENGTH", "value_name", cm(p.getMlPaqAlto())));
            attributes.add(Map.of("id", "SELLER_PACKAGE_WIDTH",  "value_name", cm(p.getMlPaqLargo())));
            attributes.add(Map.of("id", "SELLER_PACKAGE_WEIGHT", "value_name", gramos(p.getMlPaqPeso())));
```

por:

```java
            // Mapeo natural 1:1 entre el campo del producto y el atributo de ML.
            attributes.add(Map.of("id", "SELLER_PACKAGE_HEIGHT", "value_name", cm(p.getMlPaqAlto())));
            attributes.add(Map.of("id", "SELLER_PACKAGE_WIDTH",  "value_name", cm(p.getMlPaqAncho())));
            attributes.add(Map.of("id", "SELLER_PACKAGE_LENGTH", "value_name", cm(p.getMlPaqLargo())));
            attributes.add(Map.of("id", "SELLER_PACKAGE_WEIGHT", "value_name", gramos(p.getMlPaqPeso())));
```

- [ ] **Step 4: Correr el test y verificar que pasa**

Run: `cd supermaster-backend && mvn -o test -Dtest=MlItemPayloadBuilderTest`
Expected: PASS (toda la clase).

- [ ] **Step 5: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlItemPayloadBuilder.java \
        supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/MlItemPayloadBuilderTest.java
git commit -m "fix(ml): mapeo natural de dimensiones del paquete (corrige ejes cruzados)"
```

---

### Task 4: Mensaje de validación en el footer del modal

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx:1269` (footer) y `:1298-1302` (banner a eliminar)

**Interfaces:**
- Consumes: `formErrors` (objeto de errores), `Object.values(formErrors).some(Boolean)`.
- Produces: ningún cambio de API; solo presentación.

- [ ] **Step 1: Agregar el mensaje al footer**

Reemplazar el prop `footer` del Modal (línea 1269):

```tsx
                footer={<><Button variant="light" onClick={onClose} disabled={isSaving}><XMarkIcon className="w-4 h-4" /> Cancelar</Button><Button variant="dark" onClick={editandoProductoId ? handleGuardarEdicion : handleCreate} disabled={isSaving || (!editandoProductoId && skuYaExiste)}>{isSaving ? <SpinnerIcon /> : <CheckIcon className="w-4 h-4" />} {isSaving ? (editandoProductoId ? "Guardando..." : "Creando Producto...") : (editandoProductoId ? "Guardar Cambios" : "Crear Producto")}</Button></>}>
```

por:

```tsx
                footer={<div className="flex w-full items-center justify-between gap-3">
                    <span className={`text-sm text-red-600 dark:text-red-400 ${Object.values(formErrors).some(Boolean) ? "" : "invisible"}`}>Revisá los campos marcados antes de guardar.</span>
                    <div className="flex items-center gap-2">
                        <Button variant="light" onClick={onClose} disabled={isSaving}><XMarkIcon className="w-4 h-4" /> Cancelar</Button>
                        <Button variant="dark" onClick={editandoProductoId ? handleGuardarEdicion : handleCreate} disabled={isSaving || (!editandoProductoId && skuYaExiste)}>{isSaving ? <SpinnerIcon /> : <CheckIcon className="w-4 h-4" />} {isSaving ? (editandoProductoId ? "Guardando..." : "Creando Producto...") : (editandoProductoId ? "Guardar Cambios" : "Crear Producto")}</Button>
                    </div>
                </div>}>
```

(Se usa `invisible` en vez de desmontar para no alterar la altura del footer.)

- [ ] **Step 2: Eliminar el banner superior**

Eliminar el bloque (líneas 1298-1302):

```tsx
                    {Object.values(formErrors).some(Boolean) && (
                        <div className="rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-800 dark:bg-red-950/20 dark:text-red-300">
                            Revisá los campos marcados antes de guardar.
                        </div>
                    )}
```

- [ ] **Step 3: Verificar que el Modal renderiza el footer en una fila completa**

Confirmar que el componente `Modal` no impone `justify-end` rígido que rompa el `justify-between`. Abrir `supermaster-frontend/src/app/components/Modal/Modal.tsx` (o ruta equivalente del componente `Modal`) y verificar la clase del contenedor del footer. Si fuerza `justify-end`, el `<div className="flex w-full ...">` interno igual reparte el espacio (es `w-full`), así que no requiere cambio. Solo verificar; no editar si ya funciona.

- [ ] **Step 4: Lint + verificación manual**

Run: `cd supermaster-frontend && npm run lint`
Expected: sin errores nuevos.

Manual: abrir el modal, intentar guardar con un campo requerido vacío → el mensaje aparece en rojo a la izquierda de los botones; al corregir, desaparece. El banner superior ya no está.

- [ ] **Step 5: Commit**

```bash
git add supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(modal): mueve el aviso de validación al footer junto a los botones"
```

---

### Task 5: Asterisco de requerido en todos los inputs según el canal

**Files:**
- Read: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx:274-318` (validateForm — fuente de verdad de qué es requerido)
- Modify: los inputs correspondientes en el JSX del mismo archivo (títulos ML/Nube y atributos de ficha requeridos).

**Interfaces:**
- Consumes: flags `subirMl`, `subirKtHogar`, `subirKtGastro`; patrón existente `<span className="... text-red-600">*</span>`.
- Produces: solo presentación.

- [ ] **Step 1: Mapear qué exige validateForm**

Leer `validateForm` (líneas 274-318) y listar cada campo requerido condicional y su condición. Según el spec, al menos: Título ML (`subirMl`), Título Nube (`subirKtHogar || subirKtGastro`), atributos requeridos de la ficha ML (`subirMl`), y los 4 del paquete (`subirMl`, ya tienen `*`). Anotar las líneas exactas de cada `<span className={fieldLabelClassName}>` de esos campos.

- [ ] **Step 2: Agregar el asterisco condicional a cada label requerido**

Para el label del Título ML, transformar:

```tsx
<span className={fieldLabelClassName}>Título Mercado Libre</span>
```

en (usar la condición real del campo — `subirMl` para ML, `(subirKtHogar || subirKtGastro)` para Nube):

```tsx
<span className={fieldLabelClassName}>Título Mercado Libre{subirMl && <span className="ml-0.5 font-bold text-red-600">*</span>}</span>
```

Aplicar el mismo patrón al label del Título Nube con la condición `(subirKtHogar || subirKtGastro)`, y al título/label de cada atributo requerido de la ficha ML con la condición `subirMl` (en el render de la ficha, donde hoy se marca `d.required`, combinar con `subirMl`: mostrar `*` cuando `subirMl && d.required`).

> Nota: repetir el `<span>` literal en cada label (no extraer a variable) para mantener consistencia con el patrón ya usado en el bloque "Paquete para envío" (líneas 1775-1796). Las condiciones exactas se toman del Step 1 — no inventar requeridos que `validateForm` no exija.

- [ ] **Step 3: Lint + verificación manual**

Run: `cd supermaster-frontend && npm run lint`
Expected: sin errores nuevos.

Manual: marcar/desmarcar cada canal y verificar que el `*` aparece/desaparece exactamente en los inputs que `validateForm` exige para ese canal, y que coincide con los campos que se marcan en rojo al fallar la validación.

- [ ] **Step 4: Commit**

```bash
git add supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(modal): asterisco de requerido por canal en los inputs obligatorios"
```

---

### Task 6: Selector de unidad en Dimensiones Físicas + persistencia concatenada

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx` — bloque de sincronización (847-916), reverse-sync `setAtributo` (886-891), y la sección "Dimensiones Físicas" (1816-1855).

**Interfaces:**
- Consumes: estados `largo/ancho/alto/diamboca/diambase/capacidad/espesor` (string), `ML_DIM_MAP`, `fichaAttrIds`, `mlAtributosVal`, helpers `parseNumero`, `setAtributoCore`.
- Produces: cada campo físico se persiste como `"<número> <unidad>"`; la sincronización con la ficha ML conserva la unidad en ambos sentidos.

- [ ] **Step 1: Agregar el mapa de unidades comunes por campo físico**

Justo después de `ML_DIM_MAP` (línea 860), agregar:

```tsx
    // Unidades ofrecidas en "Dimensiones Físicas" (la primera es el default).
    const FISICO_UNITS: Record<FisicoKey, string[]> = {
        largo: ["cm", "mm", "m"], ancho: ["cm", "mm", "m"], alto: ["cm", "mm", "m"],
        diamboca: ["cm", "mm", "m"], diambase: ["cm", "mm", "m"],
        espesor: ["mm", "cm"], capacidad: ["ml", "L", "cc"],
    };
```

- [ ] **Step 2: Mirroring físico → ficha ML conservando la unidad del valor**

Reemplazar `onFisicoChange` (líneas 906-916):

```tsx
    const onFisicoChange = (fisico: FisicoKey, raw: string) => {
        fisicoSetters[fisico](raw);
        for (const [attrId, m] of Object.entries(ML_DIM_MAP)) {
            if (m.fisico !== fisico || !fichaAttrIds.has(attrId) || mlAtributosVal[attrId]?.noAplica) continue;
            // Conserva la unidad que el usuario ya eligió en el atributo ML; si no hay, usa la del mapeo.
            const unidadActual = (mlAtributosVal[attrId]?.valueName ?? "").split(" ").slice(1).join(" ") || m.unidad;
            const valueName = m.fisico === "capacidad" ? raw : formatNumberUnit(parseNumero(raw), unidadActual);
            setAtributoCore(attrId, valueName, mlAtributosVal[attrId]?.valueId ?? null);
        }
    };
```

por (ahora el valor físico YA trae unidad → se espeja el string completo):

```tsx
    const onFisicoChange = (fisico: FisicoKey, raw: string) => {
        fisicoSetters[fisico](raw);
        for (const [attrId, m] of Object.entries(ML_DIM_MAP)) {
            if (m.fisico !== fisico || !fichaAttrIds.has(attrId) || mlAtributosVal[attrId]?.noAplica) continue;
            // El valor físico ya incluye la unidad: se espeja tal cual al atributo ML mapeado.
            setAtributoCore(attrId, raw, mlAtributosVal[attrId]?.valueId ?? null);
        }
    };
```

- [ ] **Step 3: Reverse-sync ficha ML → físico conservando la unidad**

Reemplazar `setAtributo` (líneas 886-891):

```tsx
    const setAtributo = (id: string, valueName: string, valueId: string | null = null) => {
        setAtributoCore(id, valueName, valueId);
        const map = ML_DIM_MAP[id];
        if (map) fisicoSetters[map.fisico](map.fisico === "capacidad" ? valueName : parseNumero(valueName));
    };
```

por (el campo físico guarda el `valueName` completo, con unidad):

```tsx
    const setAtributo = (id: string, valueName: string, valueId: string | null = null) => {
        setAtributoCore(id, valueName, valueId);
        const map = ML_DIM_MAP[id];
        if (map) fisicoSetters[map.fisico](valueName);
    };
```

- [ ] **Step 4: Agregar un helper de render para campo físico número+unidad**

Antes del `return (` del JSX (alrededor de la línea 1265), agregar un helper que renderiza input numérico + selector de unidad para un campo físico:

```tsx
    // Render de un campo de "Dimensiones Físicas": número + selector de unidad. Persiste "<num> <unidad>".
    const renderFisico = (key: FisicoKey, label: string) => {
        const value = fisicoValues[key];
        const num = parseNumero(value);
        const unidades = FISICO_UNITS[key];
        const unidadActual = value.replace(/^\s*-?\d+(?:[.,]\d+)?\s*/, "").trim() || unidades[0];
        const setFis = (n: string, u: string) => { onFisicoChange(key, n ? `${n} ${u}` : ""); if (formErrors[key]) setFormErrors(p => ({ ...p, [key]: "" })); };
        return (
            <label className="block">
                <span className={fieldLabelClassName}>{label}</span>
                <div className="mt-1 flex gap-2">
                    <input type="number" min={0} className={`${inputBaseClassName} ${formErrors[key] ? inputErrorClassName : ""}`}
                        value={num} onChange={e => setFis(e.target.value, unidadActual)} />
                    <select className={selectBaseClassName} value={unidadActual} onChange={e => setFis(num, e.target.value)}>
                        {unidades.map(u => <option key={u} value={u}>{u}</option>)}
                    </select>
                </div>
                {formErrors[key] && <p className="mt-1 text-xs text-red-500">{formErrors[key]}</p>}
            </label>
        );
    };
```

> `selectBaseClassName` ya existe en el archivo (se usa en los selectores de cuotas). Verificar el nombre exacto de la constante de clase del select al implementar (buscar `selectBaseClassName` o `selectCls`).

- [ ] **Step 5: Reemplazar los inputs de "Dimensiones Físicas" por el helper**

En la sección "Dimensiones Físicas" (líneas 1819-1850), reemplazar los `<label>` de Capacidad, Largo, Ancho, Alto, Diám. Boca, Diám. Base y Espesor por llamadas al helper, conservando el grid contenedor y el bloque de Aptos:

```tsx
                            {renderFisico("capacidad", "Capacidad")}
                            {renderFisico("largo", "Largo")}
                            {renderFisico("ancho", "Ancho")}
                            {renderFisico("alto", "Alto")}
                            {renderFisico("diamboca", "Diám. Boca")}
                            {renderFisico("diambase", "Diám. Base")}
                            {renderFisico("espesor", "Espesor")}
```

(Quitar los sufijos "(cm)"/"(mm)" de los labels: ahora la unidad la muestra el selector. Mantener intacto el `<div className="md:col-span-2 xl:col-span-4">` de Aptos en la línea 1851.)

- [ ] **Step 6: Lint + verificación manual**

Run: `cd supermaster-frontend && npm run lint`
Expected: sin errores nuevos.

Manual:
1. Alta nueva: cargar Largo=30 (unidad cm), guardar; reabrir → muestra "30" + "cm". La descripción generada (Nube/ML) muestra "Largo: 30 cm".
2. Cambiar la unidad a "mm" y verificar que el atributo de dimensión de la ficha ML se actualiza a "30 mm" (si la categoría tiene ese atributo), y viceversa: cargar el atributo en la ficha ML y ver que el campo físico se llena con número y unidad.
3. Editar un producto viejo cuyo Largo era "30" sin unidad → muestra "30" + unidad por defecto "cm"; al guardar queda "30 cm".

- [ ] **Step 7: Commit**

```bash
git add supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(modal): selector de unidad en Dimensiones Físicas con persistencia número+unidad"
```

---

### Task 7: No mostrar el toast genérico al expirar la sesión

**Files:**
- Modify: `supermaster-frontend/src/app/utils/fetchAPI.ts:86-89` (exportar sentinela del error de sesión)
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx:601-603` (catch que toastea)

**Interfaces:**
- Consumes: el `Error` lanzado por `fetchAPI` ante 401.
- Produces: constante exportada `SESION_EXPIRADA_MSG` (string) y helper `esSesionExpirada(error: unknown): boolean`.

- [ ] **Step 1: Exportar el sentinela y el helper desde fetchAPI**

En `fetchAPI.ts`, definir y exportar la constante del mensaje cerca del top del archivo (después de los imports):

```ts
export const SESION_EXPIRADA_MSG = "Sesión expirada. Por favor, volvé a iniciar sesión.";

export function esSesionExpirada(error: unknown): boolean {
    return error instanceof Error && error.message === SESION_EXPIRADA_MSG;
}
```

Y reemplazar el throw del 401 (línea 88) para usar la constante:

```ts
            throw new Error(SESION_EXPIRADA_MSG);
```

- [ ] **Step 2: Importar el helper en el modal**

En `ProductoFormModal.tsx`, agregar `esSesionExpirada` al import existente desde `utils/fetchAPI` (o crear el import si no existe):

```tsx
import { esSesionExpirada } from "../utils/fetchAPI";
```

(Ajustar la ruta relativa según los imports vecinos del archivo.)

- [ ] **Step 3: Saltar el toast cuando la sesión expiró**

Reemplazar el `catch` del bloque de carga de relaciones (líneas 601-603):

```tsx
                } catch {
                    notificar.error("No se pudieron cargar catálogos/aptos/clientes del producto");
                }
```

por:

```tsx
                } catch (e) {
                    // Si la sesión expiró (401), fetchAPI ya redirige al login: no ensuciar con un toast.
                    if (!esSesionExpirada(e)) notificar.error("No se pudieron cargar catálogos/aptos/clientes del producto");
                }
```

- [ ] **Step 4: Lint + verificación manual**

Run: `cd supermaster-frontend && npm run lint`
Expected: sin errores nuevos.

Manual: con el modal de edición abierto, forzar expiración de sesión (token vencido / borrar token) y recargar el modal → debe redirigir a login SIN el toast "No se pudieron cargar...". Con backend OK, el modal carga normal.

- [ ] **Step 5: Commit**

```bash
git add supermaster-frontend/src/app/utils/fetchAPI.ts supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "fix(login): no muestra el toast de carga cuando la sesión expiró (401)"
```

---

## Notas de cierre

- Tras todas las tareas, correr la suite completa del backend: `cd supermaster-backend && mvn -o test` y `npm run lint` en el frontend.
- No se requiere actualizar `MEMORY.md` salvo que surja un hallazgo no obvio durante la ejecución.
