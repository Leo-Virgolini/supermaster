# Mejoras de UI del modal de producto (canales, carga progresiva, editor) — Diseño

**Fecha:** 2026-06-30
**Estado:** Aprobado (pendiente de review del usuario)
**Grupo:** 1 de 2 (el Grupo 2 — clientes→rubros — va en su propio spec)

## Contexto

Tras el smoke de los Temas A/B, surgieron 9 mejoras de UI sobre el modal de producto ([ProductoFormModal.tsx](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx)) y la sección "Canales de venta". Todas son de presentación/experiencia; ninguna cambia el modelo de datos.

Estado actual relevante:
- La lectura de canales es **un solo endpoint** (`GET /api/productos/{id}/estado-publicacion`) que espera a los 4 canales (paralelo interno con `join`) y devuelve `EstadoPublicacionDTO` (4 `EstadoCanalDTO` + un `DatosCanalDTO` monolítico). El frontend usa un único flag `cargandoEstado`.
- Las 4 tarjetas de canal comparten `canalCardClassName` (fondo `bg-slate-50`).
- La descripción Nube usa `HtmlEditor` (textarea con HTML crudo + vista previa lado a lado + toolbar que envuelve tags).
- `EstadoCanalDTO` tiene `publicado, estado, precio, promo, stock, peso, dimensiones, error`.

## Objetivos (9)

1. Color de fondo distinto por tarjeta de canal.
2. Editor WYSIWYG de la descripción Nube (editar sobre la vista previa, sin HTML crudo).
3. Indicador de carga en el paquete de envío de Nube.
4. Carga progresiva de canales: mostrar cada tarjeta apenas su canal responde.
5. Tooltip con las rutas de imágenes normales y crudas.
6. Cantidad de imágenes subidas por canal, en cada tarjeta.
7. Botón "ver publicación en ML" en cada fila de la tabla de MLAs.
8. Link "editar publicación en ML" en el modal.
9. Validación: categoría ML solo-lectura si ya está publicado.

## Decisiones tomadas (brainstorming)

- **#4:** un endpoint por canal (no SSE). El frontend llama los 4 en paralelo, con un flag de carga por canal.
- **#9:** bloquear (solo-lectura) la categoría + aviso si ya está publicado en ML.
- **#2:** `contentEditable` propio con `document.execCommand` (sin dependencias nuevas).
- **#7/#8:** las URLs de ML se construyen en el frontend desde el código MLA (sin cambios de backend).

---

## Diseño

### A) Carga progresiva: un endpoint por canal (#4)

**Backend** — reemplazar el endpoint único por cuatro, en `EstadoPublicacionController`:

```
GET /api/productos/{id}/estado-publicacion/ml      → MlCanalDTO
GET /api/productos/{id}/estado-publicacion/hogar   → NubeCanalDTO
GET /api/productos/{id}/estado-publicacion/gastro  → NubeCanalDTO
GET /api/productos/{id}/estado-publicacion/dux      → DuxCanalDTO
```

- `EstadoPublicacionService` expone métodos por canal: `leerMl(sku)`, `leerNube(sku, store)`, `leerDux(sku)` (ya existen internamente como `leerMlPanel`/`leerNubePanel`/`estadoDux`; se hacen públicos y devuelven el DTO del canal). Se elimina el `leer(id)` monolítico (y el `EstadoPublicacionDTO`/`DatosCanalDTO` monolíticos quedan reemplazados por los DTOs por canal).
- **DTOs por canal** (reemplazan al `DatosCanalDTO` mezclado):
  - `MlCanalDTO(EstadoCanalDTO estado, String categoryId, String categoryNombre, List<MlAtributoDTO> atributos, String descripcion, String mlaResuelto, Double mlPaqAlto, Double mlPaqAncho, Double mlPaqLargo, Double mlPaqPeso)`.
  - `NubeCanalDTO(EstadoCanalDTO estado, String descripcion, SeoCanalDTO seo, String titulo, String peso, String profundidad, String ancho, String alto)`.
  - `DuxCanalDTO(EstadoCanalDTO estado)`.
- Cada endpoint es read-only, `@PreAuthorize` de lectura, y nunca lanza (mismo manejo de error que hoy: estado `ofError` si el canal falla).

**Frontend** — en `ProductoFormModal`:
- Reemplazar `getEstadoPublicacionAPI(id)` por `getEstadoMlAPI(id)` / `getEstadoHogarAPI(id)` / `getEstadoGastroAPI(id)` / `getEstadoDuxAPI(id)`.
- Reemplazar el flag único `cargandoEstado` por cuatro: `cargandoMl`, `cargandoHogar`, `cargandoGastro`, `cargandoDux`.
- En el efecto de edición, disparar las 4 llamadas **sin `Promise.all`**: cada `.then` setea su parte del estado y baja su flag en el `.finally`; cada una con guard `cancelled`.
- **Re-mapeo de indicadores de carga** (de los Temas A/B y existentes) a su flag:
  - Categoría, atributos (ficha), paquete ML, descripción ML → `cargandoMl`.
  - Título Nube, SEO Nube, descripción Nube (HOGAR/GASTRO), paquete Nube → `cargandoHogar`/`cargandoGastro` según la sección.
- La precarga de campos editables (descripción/SEO/categoría/atributos/paquete/título) se mueve al `.then` del canal correspondiente, conservando el patrón "solo si `!= null`" (canal manda, fallback al producto).

### B) Cantidad de imágenes por canal (#6) + indicador paquete Nube (#3)

- **#6:** sumar `Integer imagenes` a `EstadoCanalDTO`. `MlEstadoParser` cuenta `item.path("pictures").size()`; `NubeEstadoParser` cuenta `product.path("images").size()`; Dux → `null`. En la tarjeta de canal (`renderEstadoBody`), mostrar "Imágenes: {n}" cuando `imagenes != null` (junto al stock).
- **#3:** envolver la grilla de "Paquete de envío · Tienda Nube" con `{cargandoHogar ? indicadorCarga("Cargando datos del canal…") : (...)}` (helper ya unificado).

### C) Colores por tarjeta de canal (#1)

Parametrizar el fondo de `canalCardClassName` por canal, con tintes suaves alineados a los íconos (reusando el estilo de `SECTION_TINT`):
- Dux → índigo; KT HOGAR → azul; KT GASTRO → esmeralda; Mercado Libre → amarillo.
Definir un `CANAL_TINT = { dux, hogar, gastro, ml }` y aplicarlo al `<div>` de cada tarjeta (manteniendo el resto de `canalCardClassName`).

### D) Editor WYSIWYG de descripción Nube (#2)

Reescribir [HtmlEditor.tsx](../../../supermaster-frontend/src/app/productos/HtmlEditor.tsx) conservando su API pública (`value`, `onChange`, `disabled`, `placeholder`, `rows`, `id`) para no tocar los 3 usos:
- Reemplazar el `textarea` + `preview` por **un único `<div contentEditable>`** con `dangerouslySetInnerHTML` inicial = `value`, estilizado como la vista previa actual (clases `prose`).
- En `onInput`, emitir `onChange((e.target as HTMLDivElement).innerHTML)`.
- Toolbar con `document.execCommand` sobre la selección dentro del editor: `bold`, `italic`, `underline`, `insertUnorderedList`, y color con `foreColor` (el `<input type="color">` aplica `execCommand("foreColor", false, color)`).
- **Sincronización controlada:** solo re-escribir `el.innerHTML` desde `value` cuando el editor **no tiene foco** y el `value` externo difiere del `innerHTML` actual (evita resetear el cursor mientras se tipea). Patrón con `useRef` + `useEffect`.
- `document.execCommand` está deprecado pero es la opción estándar sin dependencias y funciona en todos los navegadores objetivo. Contenido interno/confiable (igual que hoy, que ya usa `dangerouslySetInnerHTML`).

### E) Links a ML (#7, #8)

URLs construidas en el frontend desde el código MLA (`mlaResuelto` en el modal; el código de la fila en la tabla):
- **Ver publicación (#7, tabla MLAs):** `https://articulo.mercadolibre.com.ar/{MLA-con-guion}` donde `MLA-con-guion` inserta un guion tras "MLA"/"MLAU" (p. ej. `MLA1234` → `MLA-1234`); ML redirige al artículo. Botón "Ver" por fila, `target="_blank"`. *(Si en smoke el redirect no resuelve bien, alternativa: exponer `item.permalink` desde el backend; se evalúa solo si hace falta.)*
- **Editar publicación (#8, modal):** `https://www.mercadolibre.com.ar/publicaciones/{MLA}/modificar` (código completo, sin guion). Link "Editar en ML" junto al MLA resuelto, `target="_blank"`, solo visible si hay `mlaResuelto`.

### F) Validación categoría ML solo-lectura si publicado (#9)

En la sección ML del modal, si `estadoMl?.estado?.publicado` (o `mlaResuelto != null`):
- Mostrar la categoría seleccionada como **texto solo-lectura** (sin botón "Predecir", sin quitar, sin elegir predicciones).
- Aviso: "Mercado Libre no permite cambiar la categoría de una publicación existente. Para cambiarla hay que republicar (se elimina la publicación con sus visitas y ventas)."
- Si NO está publicado, comportamiento actual (predecir/elegir).
- Coherente con el backend, que ya aplica la categoría solo al crear.

### G) Tooltip de rutas de imágenes (#5)

En el encabezado de "Imágenes (por SKU)", extender el tooltip (o el ícono de info ya presente) para indicar dónde se guardan las imágenes **normales** (carpeta `app.imagenes-dir`) y las **crudas** (`app.imagenes-raw-dir`). Las rutas reales ya las devuelve `GET /api/imagenes/caratula/crudas/{sku}` (`crudaDir.ruta`, `destinoDir.ruta`); el modal ya las tiene en `crudasDisp` cuando se abre el selector, o se pueden mostrar como texto fijo del tooltip. Mostrar las rutas reales si están disponibles; si no, describir las carpetas.

---

## Manejo de errores

- **Endpoints por canal:** cada uno captura su error y devuelve `estado = ofError()` (igual que hoy). Una caída de un canal no afecta a los otros (ahora incluso visualmente independientes).
- **Editor WYSIWYG:** contenido confiable; si `value` viene vacío, el editor muestra vacío (placeholder vía CSS `:empty`).
- **Links ML:** se construyen client-side; si el código MLA es inválido, el link no resuelve en ML (no rompe el modal).
- **Cantidad de imágenes:** si el canal no expone imágenes (Dux) o falla, `imagenes = null` → no se muestra.

## Testing

- Backend: `MlEstadoParser`/`NubeEstadoParser` cuentan imágenes (`pictures`/`images`); endpoints por canal devuelven el DTO correcto y `ofError` ante fallo del canal; los DTOs por canal compilan (records posicionales).
- Frontend (smoke manual): abrir un producto publicado → cada tarjeta carga independiente (ML antes que Dux), con su "cargando"; cantidad de imágenes por canal; colores por canal; editor WYSIWYG (formato B/I/U/lista/color sobre la vista previa, sin HTML crudo); categoría ML solo-lectura con aviso si publicado; links "Ver"/"Editar en ML" abren la publicación; tooltip con rutas.

## Fuera de alcance (YAGNI)

- SSE / websockets (se eligió endpoint por canal).
- Cambiar el modelo de datos (eso es el Grupo 2 — rubros).
- Exponer `permalink` de ML salvo que el redirect por código falle en smoke.
- Editor de texto rico con librería externa (se eligió contentEditable sin deps).
