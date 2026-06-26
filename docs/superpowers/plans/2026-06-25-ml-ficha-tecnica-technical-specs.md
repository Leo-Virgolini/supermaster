# ML Ficha Técnica (technical_specs) — Plan de Implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reemplazar el render de características de ML por uno fiel a la UI de ML, construido desde `technical_specs/input` (secciones Variante/Principales/Secundarias, componentes reales, swatches de color, "No aplica" persistido) y sincronizar las dimensiones físicas con los atributos de dimensión de ML.

**Architecture:** Un servicio backend trae/cachea `GET /categories/{id}/technical_specs/input`, lo filtra y reorganiza en un `MlFichaDTO` (secciones → componentes → atributos). El front renderiza por tipo de componente y acopla en vivo los inputs de "Dimensiones Físicas" con los atributos ML mapeados. La persistencia reusa `producto_ml_atributo` + una columna nueva `no_aplica`.

**Tech Stack:** Spring Boot 4 / Java 25 / Jackson 3 (`tools.jackson`) / MapStruct / Next.js + React + TypeScript / Tailwind.

**Spec:** [docs/superpowers/specs/2026-06-25-ml-ficha-tecnica-technical-specs-design.md](../specs/2026-06-25-ml-ficha-tecnica-technical-specs-design.md)

## Global Constraints

- DB `ddl-auto=validate`: todo cambio de schema requiere script SQL manual en `supermaster-backend/src/main/resources/db/`.
- Tests backend offline: `mvn -o test -Dtest=...` (no `mvnw`, falla por red en sandbox).
- Records DTO: agregar un componente rompe `new XDTO(...)` posicional en tests → actualizar call sites.
- OSIV off: tocar asociaciones LAZY fuera de `@Transactional` falla silencioso (no aplica al parser, que es puro).
- Paquete base: `ar.com.leo.super_master_backend`.
- Token ML para inspección manual: `ml_tokens.json` en `C:/ProgramData/SuperMaster/secrets`.

---

### Task 1: `rgb` en `MlAtributoValorDTO`

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/dto/MlAtributoValorDTO.java`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlCategoriaAtributoService.java:170-176` (parser de `/attributes` setea rgb=null)
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/service/MlCategoriaAtributoServiceTest.java`

**Interfaces:**
- Produces: `record MlAtributoValorDTO(String id, String name, String rgb)`.

- [ ] **Step 1:** Cambiar el record a `public record MlAtributoValorDTO(String id, String name, String rgb) {}`.
- [ ] **Step 2:** En `MlCategoriaAtributoService.parsear`, actualizar la construcción de values a `new MlAtributoValorDTO(vid, vname, null)`.
- [ ] **Step 3:** Ajustar el assert existente en `MlCategoriaAtributoServiceTest` (los `MlAtributoValorDTO` ahora tienen 3 componentes; los `extracting(MlAtributoValorDTO::name)` siguen válidos).
- [ ] **Step 4:** `mvn -o test -Dtest=MlCategoriaAtributoServiceTest` → PASS.

---

### Task 2: DTOs de ficha estructurada

**Files:**
- Create: `.../apis/ml/dto/MlFichaDTO.java`
- Create: `.../apis/ml/dto/MlSeccionDTO.java`
- Create: `.../apis/ml/dto/MlComponenteDTO.java`

**Interfaces:**
- Produces:
  - `record MlFichaDTO(List<MlSeccionDTO> secciones)`
  - `record MlSeccionDTO(String id, String label, List<MlComponenteDTO> componentes)` — id ∈ {VARIANTE, PRINCIPALES, SECUNDARIAS}
  - `record MlComponenteDTO(String tipo, String label, String hint, String tooltip, String example, boolean allowCustomValue, boolean allowFiltering, List<MlAtributoDefDTO> atributos)`

- [ ] **Step 1:** Crear los tres records (sin lógica). Compila con `mvn -o compile`.

---

### Task 3: `MlFichaService` — fetch/cache/parse de technical_specs/input

**Files:**
- Create: `.../apis/ml/service/MlFichaService.java`
- Test: `.../apis/ml/service/MlFichaServiceTest.java`

**Interfaces:**
- Consumes: `MlAtributoDefDTO`, `MlAtributoValorDTO(id,name,rgb)`, los records de Task 2.
- Produces:
  - `MlFichaDTO obtenerFicha(String categoryId)` (con red+cache)
  - `static MlFichaDTO parsearFicha(JsonNode input)` (puro, testeable)

**Reglas del parser (`parsearFicha`):** recorre `input.groups[].components[].attributes[]`.
- Excluir atributo si algún tag ∈ {`hidden`,`read_only`,`fixed`,`vip_hidden`,`used_hidden`,`new_hidden`} o si su id ∈ AUTOGESTIONADOS (reusar `MlCategoriaAtributoService.AUTOGESTIONADOS`), **excepto `BRAND`** que se incluye.
- Componente sin atributos visibles → se descarta. Grupo `PRICING` → se descarta entero.
- Sección de cada componente: `VARIANTE` si algún atributo visible tiene tag `allow_variations`; si no, `PRINCIPALES` si el grupo es `MAIN`, si no `SECUNDARIAS`.
- Orden de secciones: VARIANTE, PRINCIPALES, SECUNDARIAS. Dentro, orden de aparición.
- `MlAtributoDefDTO`: id, name, value_type, values (con rgb desde `metadata.rgb`), allowed_units (ids), default_unit, required (`required`||`new_required`), conditional (`conditional_required`), multivalued, grupo=sección, relevance, value_max_length, example=null, hint=null (la ayuda va en el componente).
- `MlComponenteDTO`: tipo=`component`, label, hint/tooltip/example/allow_custom_value/allow_filtering desde `ui_config`.

- [ ] **Step 1: Test — agrupa y filtra.** Crear `MlFichaServiceTest` con un JSON `input` mínimo:

```java
@Test
void parsearFicha_agrupaEnSeccionesYFiltra() throws Exception {
    String json = """
      { "groups": [
        { "id":"MAIN", "label":"Características principales", "components": [
          { "component":"TEXT_INPUT", "label":"Marca",
            "ui_config":{"allow_custom_value":true,"allow_filtering":true,"hint":"Escribe la marca"},
            "attributes":[{"id":"BRAND","name":"Marca","value_type":"string","value_max_length":255,
                           "tags":["catalog_required","required"]}] },
          { "component":"COLOR_INPUT", "label":"Color", "ui_config":{},
            "attributes":[
              {"id":"COLOR","name":"Color","value_type":"string","tags":["defines_picture","allow_variations"]},
              {"id":"MAIN_COLOR","name":"Color","value_type":"list","tags":["vip_hidden","variation_attribute"],
               "values":[{"id":"1","name":"Marrón","metadata":{"rgb":"A0522D"}}]}
            ] },
          { "component":"NUMBER_UNIT_INPUT", "label":"Diámetro", "ui_config":{},
            "attributes":[{"id":"DIAMETER","name":"Diámetro","value_type":"number_unit",
                           "allowed_units":[{"id":"cm","name":"cm"}],"default_unit":"cm","tags":[]}] }
        ] },
        { "id":"PRICING", "label":"Precios", "components":[
          { "component":"COMBO","label":"IVA","ui_config":{},
            "attributes":[{"id":"VALUE_ADDED_TAX","name":"IVA","value_type":"list","tags":["conditional_required"]}] } ] },
        { "id":"OTHER", "label":"Otros", "components":[
          { "component":"COMBO","label":"Forma","ui_config":{},
            "attributes":[{"id":"SHAPE","name":"Forma","value_type":"string","tags":[]}] },
          { "component":"TEXT_INPUT","label":"AGID","ui_config":{},
            "attributes":[{"id":"AGID","name":"AGID","value_type":"string","tags":["hidden"]}] } ] }
      ] }""";
    JsonNode input = new ObjectMapper().readTree(json);
    MlFichaDTO ficha = MlFichaService.parsearFicha(input);

    assertThat(ficha.secciones()).extracting(MlSeccionDTO::id)
        .containsExactly("VARIANTE", "PRINCIPALES", "SECUNDARIAS");

    MlSeccionDTO variante = ficha.secciones().get(0);
    assertThat(variante.componentes()).extracting(MlComponenteDTO::tipo).containsExactly("COLOR_INPUT");
    // rgb mapeado desde metadata
    MlComponenteDTO color = variante.componentes().get(0);
    MlAtributoDefDTO mainColor = color.atributos().stream().filter(a -> a.id().equals("MAIN_COLOR")).findFirst().orElseThrow();
    assertThat(mainColor.values().get(0).rgb()).isEqualTo("A0522D");

    MlSeccionDTO principales = ficha.secciones().get(1);
    assertThat(principales.componentes()).extracting(MlComponenteDTO::label)
        .containsExactly("Marca", "Diámetro");          // BRAND incluido; Color salió a VARIANTE
    MlComponenteDTO marca = principales.componentes().get(0);
    assertThat(marca.hint()).isEqualTo("Escribe la marca");
    assertThat(marca.allowCustomValue()).isTrue();
    assertThat(marca.atributos().get(0).required()).isTrue();
    assertThat(marca.atributos().get(0).valueMaxLength()).isEqualTo(255);

    MlSeccionDTO secundarias = ficha.secciones().get(2);
    // SHAPE visible; AGID (hidden) excluido → componente AGID descartado; IVA (PRICING) descartado
    assertThat(secundarias.componentes()).extracting(MlComponenteDTO::label).containsExactly("Forma");
}
```

- [ ] **Step 2:** `mvn -o test -Dtest=MlFichaServiceTest` → FAIL (no compila / método ausente).
- [ ] **Step 3: Implementar `MlFichaService`.** Copiar el patrón de cache/tokens de `MlCategoriaAtributoService` (CacheEntry, TTL 6 h, RestClient, MlRetryHandler, tokens desde `ml_tokens.json`). `obtenerFicha` hace `GET /categories/{id}/technical_specs/input`. `parsearFicha` aplica las reglas de arriba. Constantes de tags ocultos como `Set<String> TAGS_OCULTOS = Set.of("hidden","read_only","fixed","vip_hidden","used_hidden","new_hidden")`. Reusar `MlCategoriaAtributoService.AUTOGESTIONADOS`.
- [ ] **Step 4:** `mvn -o test -Dtest=MlFichaServiceTest` → PASS.

---

### Task 4: Endpoint `/categorias/{id}/ficha`

**Files:**
- Modify: `.../apis/ml/controller/MercadoLibreController.java` (inyectar `MlFichaService`, agregar método)
- Test: `.../apis/ml/MlCategoriaFichaEndpointTest.java`

**Interfaces:**
- Consumes: `MlFichaService.obtenerFicha`.
- Produces: `GET /api/ml/categorias/{categoryId}/ficha → MlFichaDTO` (permiso `Permisos.MLAS_VER`).

- [ ] **Step 1: Test** (patrón de `MlCategoriaAtributosEndpointTest`, service mockeado):

```java
@Test
void fichaCategoria_devuelve200ConFichaDelServicio() {
    MlFichaService service = mock(MlFichaService.class);
    MercadoLibreController controller = new MercadoLibreController(null, null, null, service);
    MlFichaDTO ficha = new MlFichaDTO(List.of(new MlSeccionDTO("PRINCIPALES","Características principales", List.of())));
    when(service.obtenerFicha("MLA413476")).thenReturn(ficha);
    ResponseEntity<MlFichaDTO> r = controller.fichaCategoria("MLA413476");
    assertThat(r.getStatusCode().value()).isEqualTo(200);
    assertThat(r.getBody().secciones()).hasSize(1);
    verify(service).obtenerFicha("MLA413476");
}
```

- [ ] **Step 2:** Run → FAIL (constructor/método ausente).
- [ ] **Step 3:** Agregar `MlFichaService` al constructor del controller y el método `@GetMapping("/categorias/{categoryId}/ficha")`. Actualizar los call sites de `new MercadoLibreController(...)` en tests existentes (`MlCategoriaAtributosEndpointTest`) sumando el nuevo arg `null`.
- [ ] **Step 4:** `mvn -o test -Dtest=MlCategoriaFichaEndpointTest,MlCategoriaAtributosEndpointTest` → PASS.

---

### Task 5: Persistir `no_aplica`

**Files:**
- Create: `supermaster-backend/src/main/resources/db/2026-06-25-ml-atributo-no-aplica.sql`
- Modify: `.../dominio/producto/entity/ProductoMlAtributo.java`
- Modify: `.../dominio/producto/dto/ProductoMlAtributoDTO.java`
- Modify: `.../dominio/producto/mapper/ProductoMapper.java:142` (toMlAtributosDTO)
- Modify: `.../dominio/producto/service/ProductoServiceImpl.java:1334-1340` (reemplazarMlAtributos)
- Test: `.../dominio/producto/service/ProductoMlAtributosPersistTest.java`

**Interfaces:**
- Produces: `record ProductoMlAtributoDTO(String attributeId, String valueId, String valueName, boolean noAplica)`; entity con `boolean noAplica` (col `no_aplica`).

- [ ] **Step 1: Script SQL.**

```sql
-- Agrega el flag "No aplica" por atributo de ficha técnica de ML.
ALTER TABLE supermaster.producto_ml_atributo
  ADD COLUMN no_aplica BOOLEAN NOT NULL DEFAULT FALSE;
```

- [ ] **Step 2:** Entity: agregar `@Column(name="no_aplica", nullable=false) private boolean noAplica;`.
- [ ] **Step 3:** DTO: agregar `boolean noAplica` (4º componente).
- [ ] **Step 4:** Mapper `toMlAtributosDTO`: `new ProductoMlAtributoDTO(a.getAttributeId(), a.getValueId(), a.getValueName(), a.isNoAplica())`.
- [ ] **Step 5:** `reemplazarMlAtributos`: `e.setNoAplica(a.noAplica());`.
- [ ] **Step 6:** Actualizar todos los `new ProductoMlAtributoDTO(...)` posicionales en tests (`ProductoMlAtributosPersistTest`, `ProductoMapperEanTest`, etc. — buscar con grep) sumando `false`.
- [ ] **Step 7:** Test de round-trip: persistir un atributo con `noAplica=true` y verificar que el DTO lo refleja (extender `ProductoMlAtributosPersistTest`).
- [ ] **Step 8:** `mvn -o test -Dtest=ProductoMlAtributosPersistTest` → PASS (los tests que tocan BD usan H2/perfil test; si es integración, validar que pasa).

---

### Task 6: Payload de ML — BRAND guardado y omitir "No aplica"

**Files:**
- Modify: `.../apis/ml/service/MlItemPayloadBuilder.java:61-101`
- Test: `.../apis/ml/service/MlItemPayloadBuilderTest.java`

**Interfaces:**
- Consumes: `Producto.getMlAtributos()` (ahora con `noAplica`).

**Reglas:**
- Si entre los `mlAtributos` guardados hay uno con `attributeId == "BRAND"` (y no `noAplica`), NO agregar el BRAND auto desde `p.getMarca()` (evita duplicado); el guardado se manda en el loop.
- Atributos guardados con `noAplica == true` se omiten del payload.

- [ ] **Step 1: Test** — un producto con `mlAtributos` que incluye `BRAND` guardado: el payload tiene un solo `BRAND` con el value guardado. Otro con `noAplica=true`: ausente del payload.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implementar: calcular `boolean brandGuardado = p.getMlAtributos().stream().anyMatch(a -> "BRAND".equals(a.getAttributeId()) && !a.isNoAplica());` y condicionar el bloque auto-BRAND a `!brandGuardado`. En el loop de guardados, `if (a.isNoAplica()) continue;`.
- [ ] **Step 4:** `mvn -o test -Dtest=MlItemPayloadBuilderTest` → PASS.
- [ ] **Step 5:** Suite ML completa: `mvn -o test -Dtest=ar.com.leo.super_master_backend.apis.ml.**` → PASS.

---

### Task 7: Front — tipos + API de ficha

**Files:**
- Modify: `supermaster-frontend/src/app/productos/productosService.ts:313-324`

**Interfaces:**
- Produces:
```ts
export type MlAtributoValor = { id: string; name: string; rgb: string | null };
export type MlFichaAtributo = MlAtributoDef;   // reusa el tipo existente (+ values con rgb)
export type MlComponente = { tipo: string; label: string; hint: string | null; tooltip: string | null;
  example: string | null; allowCustomValue: boolean; allowFiltering: boolean; atributos: MlAtributoDef[] };
export type MlSeccion = { id: "VARIANTE"|"PRINCIPALES"|"SECUNDARIAS"; label: string; componentes: MlComponente[] };
export type MlFicha = { secciones: MlSeccion[] };
export const getMlCategoriaFichaAPI = (categoryId: string): Promise<MlFicha> => ...
```

- [ ] **Step 1:** Agregar `rgb: string | null` a `MlAtributoValor`. Agregar tipos `MlComponente`/`MlSeccion`/`MlFicha`. Agregar `getMlCategoriaFichaAPI` (fetch a `/api/ml/categorias/{id}/ficha`).
- [ ] **Step 2:** `cd supermaster-frontend && npx tsc --noEmit` → exit 0.

---

### Task 8: Front — render de ficha por componente

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx` (estado, fetch, render)

**Detalle:**
- Reemplazar el fetch de `getMlCategoriaAtributosAPI` por `getMlCategoriaFichaAPI`; estado `mlFicha: MlFicha | null`. Derivar `mlAtributosDef` (lista plana) de `mlFicha` para reusar validación de required existente.
- Render: por cada `seccion` un bloque con `label`; por cada `componente`, un renderer según `tipo`:
  - `TEXT_INPUT`: input texto, `maxLength`, datalist si hay values.
  - `COMBO`: si `allowCustomValue` → input + datalist; si no → `select`.
  - `NUMBER_UNIT_INPUT`: número + select de unidades.
  - `BOOLEAN_INPUT`: toggle Sí/No (usar values del atributo).
  - `COLOR_INPUT`: para el atributo con values con `rgb`, lista/desplegable con círculo (`style={{background:'#'+rgb}}`) + nombre.
  - `LINKED_BY_CONNECTOR_INPUT`: los `atributos` del componente en una fila (cada uno número+unidad), bajo el `label` del componente.
  - `hint`/`example` como subtexto/placeholder; `tooltip` como ícono de ayuda.
- Checkbox "No aplica" por componente/atributo: estado `noAplica` en `mlAtributosVal`; deshabilita inputs; se envía en el DTO (`noAplica`).
- Al enviar, mapear `mlAtributosVal` a `{ attributeId, valueId, valueName, noAplica }`.

- [ ] **Step 1:** Implementar el render y el estado. Mantener la validación de required (resaltar faltantes) sobre la lista plana derivada.
- [ ] **Step 2:** `npx tsc --noEmit` → exit 0.
- [ ] **Step 3:** Verificación manual: abrir un producto con categoría Paneras → 3 secciones, swatch en Color, "No aplica" deshabilita.

---

### Task 9: Front — sync Dimensiones Físicas ↔ ML

**Files:**
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx` (helpers + wiring)

**Detalle:**
- Mapeo `ML_DIM_MAP: Record<string, {fisico: 'alto'|'ancho'|'largo'|'diamboca'|'diambase'|'capacidad'|'espesor', unidad: string}>` con las claves `HEIGHT, WIDTH, LENGTH, DIAMETER, MOUTH_DIAMETER, BASE_DIAMETER, CAPACITY, THICKNESS`.
- Helpers puros: `parseNumero(s: string): string` (extrae el primer número), `formatNumberUnit(num: string, unidad: string): string`.
- Al cambiar un input físico mapeado → si el atributo ML existe en la ficha y no está "No aplica", setear el atributo (`formatNumberUnit`). Al cambiar el atributo ML → setear el físico (`parseNumero`). Guarda anti-bucle con una bandera/origen.
- Capacidad: sincroniza string completo (number+unidad) en ambos sentidos.

- [ ] **Step 1:** Implementar helpers + wiring bidireccional.
- [ ] **Step 2:** `npx tsc --noEmit` → exit 0.
- [ ] **Step 3:** Verificación manual: tipear "20" en "Alto (cm)" llena "Altura" de ML y viceversa; en Macetas, Diám. Base ↔ "Diámetro de la base".

---

## Self-Review

- **Spec coverage:** secciones (T3), componentes+swatches+No aplica (T8), sync dimensiones (T9), persistencia no_aplica (T5), marca editable/payload (T6), endpoint (T4), rgb (T1). ✅
- **Type consistency:** `MlAtributoValorDTO(id,name,rgb)`, `ProductoMlAtributoDTO(...,noAplica)`, `MlFichaDTO/MlSeccionDTO/MlComponenteDTO` usados consistentes entre tasks. ✅
- **Pendiente conocido:** el endpoint plano `/atributos` y `MlCategoriaAtributoService.obtenerAtributos` quedan sin uso desde el front (se conservan para la validación de required que ya usa `idsValidos`); no se borran en este plan.
