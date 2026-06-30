# Carga completa de datos de canales al editar (ML + Nube)

**Fecha:** 2026-06-30
**Estado:** Aprobado (pendiente de review del usuario)

## Contexto

Al abrir un producto para editar, [ProductoFormModal.tsx](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx) llama a `getEstadoPublicacionAPI(producto.id)` → backend `EstadoPublicacionService.leer(...)`, que lee los 4 canales en paralelo y devuelve `EstadoPublicacionDTO` con un `DatosCanalDTO` (campos editables leídos del canal, no persistidos) para pre-llenar el modal. El flag `cargandoEstado` está en `true` mientras esa lectura corre.

Hoy se pre-cargan **del canal**: categoría ML, atributos ML (ficha técnica), descripción ML, descripción Nube (HOGAR/GASTRO), SEO Nube (HOGAR/GASTRO) y dimensiones Nube. Se cargan **del producto persistido**: título Nube ([línea 612](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx#L612)) y dimensiones del paquete de envío de ML (`mlPaq*`, [líneas 643-646](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx#L643-L646)).

Problemas a resolver:

1. **El paquete de envío de ML no se trae del canal.** La respuesta de `GET /items/{id}` de ML **sí** incluye las dimensiones como atributos `SELLER_PACKAGE_HEIGHT/WIDTH/LENGTH/WEIGHT`, pero hoy se **omiten a propósito** en [MlDatosParser.java:18](../../../supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/MlDatosParser.java#L18) (se consideran atributos de sistema, no ficha técnica).
2. **El título Nube se carga del producto, no del canal.** Se pidió que Tienda Nube cargue **todos** sus campos desde la publicación (ambos canales).
3. **No hay indicador de "cargando"** en los campos de ML (categoría, atributos, paquete) mientras `cargandoEstado` es `true`; el usuario ve los campos vacíos y parece que no hay datos. El patrón de indicador ya existe para la descripción Nube ([líneas 2349-2351](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx#L2349-L2351)): `{cargandoEstado ? <SpinnerIcon/> Cargando datos del canal… : <campo/>}`.

## Objetivos

- **B1 — Indicador de carga de ML:** mostrar "Cargando datos de ML…" (mismo patrón existente) en **categoría**, **atributos (ficha técnica)** y **paquete de envío** mientras `cargandoEstado`.
- **B2 — Paquete de envío desde ML:** parsear `SELLER_PACKAGE_*` del item y pre-cargar los inputs `mlPaq*`.
- **B3 — Tienda Nube completo desde el canal + indicador:** pre-cargar **todos** los campos de Nube (título, SEO, descripción, dimensiones) desde la publicación, para ambos canales (HOGAR/GASTRO), y mostrar el indicador "Cargando datos del canal…" en los campos que llegan async (SEO title/description/tags, título) mientras `cargandoEstado` — hoy esos campos se ven vacíos durante la carga porque no muestran el indicador (como sí lo hace la descripción).

## Decisiones tomadas (brainstorming)

- Alcance del Tema B cerrado en B1 + B2 + B3 (no hay más cambios pendientes).
- B3: **todo desde el canal**, incluido el **título** (deja de venir del producto al editar).
- **El canal manda con fallback al producto (solo en la pre-carga):** al abrir, cuando el canal trae el dato pisa el valor cargado del producto; si el producto no está publicado en ese canal (dato null), se conserva el valor del producto. Consistente con cómo ya se cargan categoría/descripción de ML.
- **Al guardar: BD + publicación.** `tituloNube` y `mlPaq*` se persisten en la BD del producto (los usan listado/columnas/historial) **y** se propagan a la publicación del canal mediante el flujo "actualizar al editar" ya existente. **Verificado para ML:** `actualizarItemEnMlCore` ([MercadoLibreService.java:2029-2032](../../../supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java#L2029-L2032)) ya manda las dimensiones del paquete vía `MlItemPayloadBuilder.construirAtributos` (lee de `producto.getMlPaq*`). Para Nube, el título se actualiza en el mismo flujo. No se quita la persistencia en BD; **no hay trabajo de guardado nuevo** en este spec (solo pre-carga + indicadores).

---

## Diseño

### B2 — Paquete de envío desde ML (backend)

**Parser** — nuevo método en [MlDatosParser](../../../supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/producto/estado/MlDatosParser.java):

```java
public record PaqueteMl(Double altoCm, Double anchoCm, Double largoCm, Double pesoKg) {}
public static PaqueteMl paquete(JsonNode item) { ... }
```

- Recorre `item.attributes` buscando `SELLER_PACKAGE_HEIGHT/WIDTH/LENGTH/WEIGHT`.
- Lee el número de cada uno desde `value_struct.number` (fallback: parsear el número inicial de `value_name`, p. ej. `"21 cm"` → `21`).
- Unidades: HEIGHT/WIDTH/LENGTH ya vienen en **cm** → directo. WEIGHT viene en **gramos** → `pesoKg = gramos / 1000`. (Coherente con el tooltip del form: "se envían a ML en cm y gramos".)
- Mapeo a inputs: HEIGHT→Alto, WIDTH→Ancho, LENGTH→Largo, WEIGHT→Peso.
- Campos ausentes → `null` (no se pre-cargan; queda el valor del producto).

Los `SELLER_PACKAGE_*` **siguen** en la lista `OMITIR` de `atributos(...)` (no son ficha técnica editable); solo se leen aparte para el paquete.

**DTO y service:**

- `DatosCanalDTO`: agregar `Double mlPaqAlto, mlPaqAncho, mlPaqLargo, mlPaqPeso`.
- `EstadoPublicacionService`: `MlPanel` suma un `PaqueteMl`; `leerMlPanel` llama `MlDatosParser.paquete(item)`; al construir `DatosCanalDTO` se vuelcan los 4 campos.

### B3 — Tienda Nube completo desde el canal (backend)

- **Título:** leer el nombre del producto Nube (`product.name.es`, a confirmar contra el JSON real de Nube) en `leerNubePanel`; agregar `String titulo` al `NubePanel` y `String nubeTitulo` al `DatosCanalDTO`. Como el título es compartido HOGAR/GASTRO, se elige con el mismo patrón de fallback ya usado: `hogar.titulo() != null ? hogar.titulo() : gastro.titulo()`.
- **SEO:** ya se parsea (`NubeSeoParser.parse`) y se carga bien; **no estaba vacío, estaba cargando**. El cambio es de **frontend** (indicador, ver más abajo), no de backend.
- **Descripción y dimensiones:** ya se cargan del canal; sin cambios.

**Indicador de carga en campos Nube (frontend):** mientras `cargandoEstado`, mostrar "Cargando datos del canal…" (mismo patrón de [líneas 2349-2351](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx#L2349-L2351)) en los campos que llegan async y hoy se ven vacíos durante la carga: **Título Nube** y los tres campos **SEO** (title/description/tags) de cada bloque (`renderSeoNube` para HOGAR y GASTRO). La descripción ya tiene el indicador; se replica al resto.

### Frontend — pre-carga (ProductoFormModal)

En el `.then(e => { ... })` de `getEstadoPublicacionAPI` ([líneas 686-716](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx#L686-L716)), agregar (patrón "solo si el canal lo trae", para no pisar con null):

```ts
if (e.datos.nubeTitulo != null) setTituloNube(e.datos.nubeTitulo);
if (e.datos.mlPaqAlto  != null) setMlPaqAlto(e.datos.mlPaqAlto);
if (e.datos.mlPaqAncho != null) setMlPaqAncho(e.datos.mlPaqAncho);
if (e.datos.mlPaqLargo != null) setMlPaqLargo(e.datos.mlPaqLargo);
if (e.datos.mlPaqPeso  != null) setMlPaqPeso(e.datos.mlPaqPeso);
```

- Tipos en [productosService.ts](../../../supermaster-frontend/src/app/productos/productosService.ts) `DatosCanal`: agregar `nubeTitulo: string | null` y `mlPaqAlto/Ancho/Largo/Peso: number | null`.
- El `setTituloNube(producto.tituloNube ?? "")` de la carga inicial ([línea 612](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx#L612)) se mantiene; el efecto async lo pisa solo si el canal trae título. Igual para `mlPaq*` (líneas 643-646).

### B1 — Indicadores de carga de ML (frontend)

Mientras `cargandoEstado`, mostrar el indicador (mismo estilo que [líneas 2349-2351](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx#L2349-L2351): borde + `SpinnerIcon` + "Cargando datos de ML…") en tres lugares de la sección MercadoLibre:

1. **Categoría** — el área de chips/predicciones ([~2126-2149](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx#L2126-L2149)): mientras carga, en vez del texto "Si cargás Título ML, es obligatorio elegir una categoría…", mostrar el indicador.
2. **Atributos (ficha técnica)** — el bloque donde se renderiza la ficha de la categoría: mientras carga, indicador en lugar del contenido.
3. **Paquete de envío** — la grilla Alto/Ancho/Largo/Peso ([~2279-2304](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx#L2279-L2304)): mientras carga, indicador (o inputs deshabilitados con la leyenda) hasta que llegan los datos.

Detalle: el botón "Predecir categorías" y la edición manual quedan deshabilitados mientras `cargandoEstado` para no competir con la pre-carga async (evita que una predicción manual sea pisada al llegar `e.datos`).

---

## Manejo de errores

- **Canal caído / producto no publicado:** `leerMlPanel` / `leerNubePanel` ya capturan todo y nunca lanzan (devuelven panel de error / nulls). Los campos quedan con el valor del producto (fallback). El indicador de carga se apaga igual en el `.finally` (`setCargandoEstado(false)`).
- **Atributo de paquete con formato inesperado:** si no se puede parsear el número, ese campo queda `null` (no se pre-carga); no rompe la lectura del resto.
- **Campos Nube durante la carga:** mientras `cargandoEstado`, los campos (título, SEO) muestran el indicador "Cargando datos del canal…"; al terminar se llenan con el dato del canal (o quedan vacíos si la publicación realmente no lo tiene).

## Testing

- `MlDatosParser.paquete`: item con los 4 `SELLER_PACKAGE_*` (cm + gramos→kg correctos); item sin ellos → todos null; `value_struct` ausente con fallback a `value_name`; `atributos(...)` sigue omitiéndolos de la ficha.
- `EstadoPublicacionService`: `DatosCanalDTO` incluye paquete ML y título Nube; fallback HOGAR→GASTRO para el título; canal en error → nulls.
- Frontend (smoke manual): abrir producto con publicación ML → paquete se llena desde ML; categoría/atributos/paquete muestran "Cargando datos de ML…" hasta que llega; abrir producto publicado en Nube → título y SEO (title/description/tags) muestran "Cargando datos del canal…" hasta que llegan y luego se llenan desde el canal en ambas pestañas; producto no publicado → conserva valores del producto.
- Guardado: al guardar un producto editado, `tituloNube` y `mlPaq*` quedan en la BD **y** la publicación del canal refleja los cambios (verificar el flujo "actualizar al editar").

## Fuera de alcance (YAGNI)

- Externalizar por completo `tituloNube` / `mlPaq*` (dejar de persistirlos en la BD): se siguen guardando en BD y además en la publicación.
- Reescritura del paralelismo de `EstadoPublicacionService`.
