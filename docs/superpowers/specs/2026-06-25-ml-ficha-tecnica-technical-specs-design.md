# Diseño: Ficha técnica de ML por `technical_specs/input` (fidelidad de componentes)

**Fecha:** 2026-06-25
**Estado:** Aprobado (pendiente plan de implementación)
**Supersede (parcialmente):** [2026-06-24-ml-atributos-categoria-design.md](2026-06-24-ml-atributos-categoria-design.md) — el agrupado por `attribute_group_id == "MAIN"` / `relevance` queda reemplazado.

## Problema

El modal de producto arma las "Características" de ML desde `GET /categories/{id}/attributes` y las agrupa con un criterio propio (primero `attribute_group_id == "MAIN"`, luego `relevance == 1`). Contra la categoría real (verificado con **Paneras `MLA413476`**, 52 atributos) esto **no reproduce** lo que ML muestra:

- `attribute_group_id` viene `"OTHERS"` para los 52 atributos (nunca `"MAIN"`).
- `relevance` viene `1` para los 52 (no discrimina).
- En consecuencia, todo cae en "Secundarias" y, además, se cuelan atributos `hidden` (AGID, MPN, "Es kit", "Fuente del producto", etc.) que ML oculta.

La UI real de ML se arma con otro recurso: **`GET /categories/{id}/technical_specs/input`**, que devuelve la estructura exacta: `groups → components → attributes`, con tipos de componente, `ui_config` (textos de ayuda y flags) y, por atributo, `values` (con `metadata.rgb` para color), `allowed_units`, `tags` y `value_max_length`.

ML presenta tres bloques que hoy no reproducimos:
1. **Características de la variante** — atributos con `allow_variations` (Color, Nombre del diseño).
2. **Características principales** — resto del grupo `MAIN` (Marca, Modelo, Medidas, Diámetro).
3. **Características secundarias** — grupo `OTHER` filtrado (Incluye tapa, Forma, materiales).

## Objetivo

Reescribir el render de características para que coincida con ML, consumiendo `technical_specs/input` como **única fuente** del formulario. Incluye en v1:

- Las **3 secciones** (Variante / Principales / Secundarias) con su organización real.
- Un **renderer por tipo de componente**: `TEXT_INPUT`, `COMBO`, `NUMBER_UNIT_INPUT`, `BOOLEAN_INPUT`, `COLOR_INPUT` (con **swatches** de color), `LINKED_BY_CONNECTOR_INPUT` ("Medidas" agrupado).
- **Swatches de color** (círculo con el `rgb`).
- Checkbox **"No aplica"** por campo, **persistido**.
- **Sincronización bidireccional** entre los inputs de "Dimensiones Físicas" y los atributos de dimensión de ML.

**Fuera de v1 (futuro):** variaciones reales (múltiples variantes con "Agregar variante"); afecta persistencia y payload de alta y queda explícitamente diferido.

## Decisiones tomadas

| Tema | Decisión |
|------|----------|
| Fuente del formulario | `technical_specs/input` (autosuficiente; no se mezcla con `/attributes` para el render). |
| Swatches de color | Sí. El círculo usa el `rgb` de los `values` del atributo con paleta dentro del `COLOR_INPUT` (en Paneras, `MAIN_COLOR`; `COLOR` no trae `rgb`). |
| "No aplica" | Sí, **persistido** con una columna nueva `producto_ml_atributo.no_aplica BOOLEAN`. |
| Variaciones reales | **No** en v1. Los atributos `allow_variations` se muestran como campos simples bajo "Variante". |
| Marca (BRAND) | **Visible** en "Principales", editable, **pre-cargada** con el texto de la marca del producto (relación `marca`). Si el usuario la sobrescribe, vale lo del form. |
| Validación de `required` | Sin cambios: el bloqueo de alta por `required` faltante se sigue resolviendo en el publish (vía `/attributes`). El form solo cambia el render. |
| Sync dimensiones físicas ↔ ML | Bidireccional, condicional a que la categoría tenga el atributo. Mapeo: Alto↔`HEIGHT`, Ancho↔`WIDTH`, Largo↔`LENGTH`, Diám. Boca↔`DIAMETER`/`MOUTH_DIAMETER`, Diám. Base↔`BASE_DIAMETER`, Capacidad↔`CAPACITY`, Espesor↔`THICKNESS`. |

## Endpoints de ML (referencia)

- `GET /categories/{id}/technical_specs/input` — estructura de carga (la que usamos).
- `GET /categories/{id}/attributes` — se mantiene para la validación de `required` en el publish (ya implementado en `MlValidacionRequeridos`).

### Forma de `technical_specs/input`

```
{
  groups: [
    { id: "MAIN"|"PRICING"|"OTHER", label, relevance,
      components: [
        { component: "TEXT_INPUT"|"COMBO"|"NUMBER_UNIT_INPUT"|"BOOLEAN_INPUT"|"COLOR_INPUT"|"LINKED_BY_CONNECTOR_INPUT",
          label,
          ui_config: { allow_custom_value, allow_filtering, connector, hint, tooltip, example },
          attributes: [
            { id, name, value_type, value_max_length, tags: [..],
              values: [ { id, name, metadata: { rgb, parent_id } } ],
              allowed_units: [ { id, name } ], default_unit, relevance, hierarchy }
          ] } ] } ]
}
```

Nota: `hint`/`tooltip`/`example` viven en `ui_config` del **componente**, no por atributo.

## Arquitectura

### Backend

**Nuevo DTO estructurado** (`apis/ml/dto`):

```java
record MlFichaDTO(List<MlSeccionDTO> secciones) {}

record MlSeccionDTO(
    String id,        // "VARIANTE" | "PRINCIPALES" | "SECUNDARIAS"
    String label,     // "Características de la variante" | "...principales" | "...secundarias"
    List<MlComponenteDTO> componentes) {}

record MlComponenteDTO(
    String tipo,              // component (TEXT_INPUT, COMBO, NUMBER_UNIT_INPUT, BOOLEAN_INPUT, COLOR_INPUT, LINKED_BY_CONNECTOR_INPUT)
    String label,
    String hint,              // ui_config.hint
    String tooltip,           // ui_config.tooltip
    String example,           // ui_config.example
    boolean allowCustomValue, // ui_config.allow_custom_value
    boolean allowFiltering,   // ui_config.allow_filtering
    List<MlAtributoDefDTO> atributos) {}
```

Se **reusa `MlAtributoDefDTO`** (id, name, valueType, values, allowedUnits, defaultUnit, required, conditional, multivalued, valueMaxLength, example, hint…) y se le **agrega `rgb` a `MlAtributoValorDTO`** (`record MlAtributoValorDTO(String id, String name, String rgb)`). El campo `grupo`/`relevance` del DTO de atributo deja de usarse para agrupar (la sección la define la jerarquía nueva); se mantiene por compatibilidad o se limpia en el plan.

**Servicio** (`MlFichaService`, o se extiende `MlCategoriaAtributoService`):
- Trae y cachea `technical_specs/input` por categoría (mismo patrón TTL 6 h y manejo de tokens que hoy).
- `parsear(JsonNode)` **puro y testeable** (sin red): recorre `groups → components → attributes`, **filtra** y **reorganiza** en secciones.

**Filtrado de atributos** (los que NO se muestran):
- Tags ocultos/internos: `hidden`, `read_only`, `fixed`, `vip_hidden`, `used_hidden`, `new_hidden`.
- Auto-gestionados por la plataforma (lista ya existente): `ITEM_CONDITION`, `SELLER_SKU`, `SELLER_PACKAGE_*`, `PACKAGE_*`, `VALUE_ADDED_TAX`, `IMPORT_DUTY`, `GTIN`, `EAN`. (Grupo `PRICING` queda excluido entero.)
- **Excepción Marca**: `BRAND` **no** se excluye (se muestra en Principales, pre-cargado).
- Un componente queda fuera si, tras filtrar, no le queda ningún atributo visible.

**Reorganización en secciones** (sobre el grupo de origen):
- **VARIANTE**: componentes cuyos atributos visibles tienen `allow_variations`. (En `COLOR_INPUT`, el atributo de paleta `rgb` viaja en el mismo componente.)
- **PRINCIPALES**: componentes del grupo `MAIN` que no son de variante.
- **SECUNDARIAS**: componentes del grupo `OTHER` (filtrados).

**Color (`COLOR_INPUT`)**: el componente puede traer dos atributos (`COLOR` editable sin `rgb`, `MAIN_COLOR` lista con `rgb`). Para v1 se renderiza la **paleta del atributo que trae `rgb`** (swatches) y se persiste ese atributo. Si solo hay un atributo de color, se usa ese.

**Endpoint**: `GET /api/ml/categorias/{categoryId}/ficha` → `MlFichaDTO`. El endpoint plano `/atributos` se deja de usar desde el front (se puede mantener temporalmente o quitar en el plan).

### Frontend (`ProductoFormModal.tsx` + `productosService.ts`)

- Nuevo tipo TS espejo de `MlFichaDTO`. `getMlCategoriaFichaAPI(categoryId)`.
- Render: por cada **sección** un bloque con su `label`; dentro, por cada **componente**, un renderer según `tipo`:
  - `TEXT_INPUT` → input texto, `maxLength = valueMaxLength`, `placeholder = example`, datalist si hay `values`.
  - `COMBO` → si `allowCustomValue`: input editable + datalist; si no: `select` de `values`. (`allowFiltering` → búsqueda; v1 puede degradar a select/datalist nativo.)
  - `NUMBER_UNIT_INPUT` → número + `select` de `allowedUnits` (default `defaultUnit`).
  - `BOOLEAN_INPUT` → toggle Sí/No.
  - `COLOR_INPUT` → lista de opciones con **círculo de color** (`background: #rgb`) + nombre; seleccionable.
  - `LINKED_BY_CONNECTOR_INPUT` → los atributos del componente en una fila ("Medidas": Alto/Ancho/Largo), cada uno número+unidad.
  - `hint`/`tooltip` como subtexto / ícono de ayuda; `example` como placeholder.
- **"No aplica"**: checkbox por campo; al marcarlo, deshabilita el/los input(s) del atributo y setea `noAplica=true` en el estado; se envía y persiste.
- **Sync con Dimensiones Físicas**: ver la sección dedicada. Un helper de parseo/formato (`parseNumero`/`formatNumberUnit`) acopla el estado de los inputs físicos y los atributos ML mapeados.

### Sincronización Dimensiones Físicas ↔ atributos de dimensión de ML

Cuando la categoría de ML expone un atributo de dimensión, su input en la ficha y el input correspondiente de "Dimensiones Físicas" (fieldset existente, columnas del producto) quedan **espejados**: escribir en uno actualiza el otro en vivo. Es una sincronización **de estado en el front**; no agrega lógica de backend (cada lado ya se persiste por su canal: físicas como columnas del producto, atributos ML en `producto_ml_atributo`).

**Mapeo** (atributo ML → columna física → unidad). El mapeo se indexa por **id de atributo ML**, porque algunas categorías separan el diámetro en boca/base (verificado: Paneras/Ollas/Platos tienen un único `DIAMETER`; **Macetas `MLA11034`** y **Floreros `MLA9972`** tienen `MOUTH_DIAMETER` + `BASE_DIAMETER`):

| Atributo ML | Físico (columna producto) | Unidad |
|---|---|---|
| `HEIGHT` | `alto` | cm |
| `WIDTH` | `ancho` | cm |
| `LENGTH` | `largo` | cm |
| `DIAMETER` | `diamboca` | cm |
| `MOUTH_DIAMETER` | `diamboca` | cm |
| `BASE_DIAMETER` | `diambase` | cm |
| `CAPACITY` | `capacidad` | string con unidad (ej. "500 ml") |
| `THICKNESS` | `espesor` | mm |

Notas del mapeo:
- En categorías con un único `DIAMETER`, este alimenta `diamboca` (la abertura). En categorías con el par boca/base, `MOUTH_DIAMETER`↔`diamboca` y `BASE_DIAMETER`↔`diambase`.
- Si una categoría expusiera a la vez `DIAMETER` y `MOUTH_DIAMETER` (no observado), prevalece el par boca/base y el genérico `DIAMETER` se ignora para evitar doble binding sobre `diamboca`.

**Reglas:**
- El par solo se activa si el atributo ML está presente en la ficha de la categoría. Si no, el input físico se comporta como hoy.
- **Lineales** (`HEIGHT`/`WIDTH`/`LENGTH`/`DIAMETER`/`MOUTH_DIAMETER`/`BASE_DIAMETER`/`THICKNESS`, todos `number_unit`): el input físico guarda **solo el número** (su label ya indica cm/mm); el atributo ML guarda `"{número} {unidad}"` con la unidad fija del mapeo. Al cambiar uno, se parsea el número y se refleja en el otro.
- **Capacidad** (`CAPACITY`, `number_unit`; el físico es texto libre tipo "500 ml"): se sincroniza el string `"{número} {unidad}"` en ambos sentidos. Si el físico no tiene un número parseable, no se fuerza nada.
- Si el usuario cambia la **unidad** en el selector del input ML a una distinta de la del mapeo (ej. cm→m), **no se convierte**: el físico recibe el número tal cual. (Limitación documentada; las unidades por defecto coinciden con las físicas.)
- Si el campo físico tiene contenido no numérico, no se sincroniza (se ignora).
- Si el atributo ML está marcado **"No aplica"**, la sincronización para ese par se corta.
- Guardas anti-bucle: la actualización en cascada no vuelve a disparar la del origen.

### Persistencia

- Valores siguen en `producto_ml_atributo (attributeId, valueId, valueName)`. El alta/actualización a ML **no cambia** (sigue leyendo esa tabla).
- **Schema**: nueva columna `producto_ml_atributo.no_aplica BOOLEAN NOT NULL DEFAULT FALSE`. Script SQL manual en `src/main/resources/db/2026-06-25-ml-atributo-no-aplica.sql` (por `ddl-auto=validate`). Se actualizan entity/DTO/mapper de `ProductoMlAtributo`.
- **Marca**: `BRAND` se guarda como un atributo más; su valor por defecto en el form es el nombre de la marca del producto. En el publish, el builder usa el `BRAND` guardado si existe; si no, cae al actual (marca del producto) como fallback.

## Pruebas

Backend (offline, `mvn -o`):
- `parsear`: agrupa en VARIANTE/PRINCIPALES/SECUNDARIAS; excluye `hidden`/auto-gestionados; deja `BRAND` en PRINCIPALES; mapea `rgb`; respeta tipos de componente y `ui_config`; descarta componentes vacíos.
- Mapeo de `MlAtributoValorDTO.rgb` desde `metadata.rgb`.
- Persistencia: `no_aplica` se guarda y recupera (round-trip entity/DTO/mapper).

Frontend: typecheck (`tsc --noEmit`). Sin framework de tests JS en el repo. El helper de sync (`parseNumero`/`formatNumberUnit`) se diseña como función pura para poder verificarlo manualmente; la sincronización bidireccional se valida a mano (escribir en físico actualiza ML y viceversa; "No aplica" corta el sync).

## Riesgos / notas

- `COLOR_INPUT` con dos atributos: para v1 se persiste el atributo con paleta `rgb`; alinear con el publish a ML (qué `id` se manda) se revisa en el plan.
- Categorías sin grupo `OTHER` visible → "Secundarias" puede quedar vacía (no renderizar bloques vacíos).
- El trabajo previo (agrupado por `relevance` + `valueMaxLength`/`example`/`hint`) se conserva en el `MlAtributoDefDTO`; el agrupamiento se reemplaza por la jerarquía de secciones.
