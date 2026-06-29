# Modal de producto — mejoras round 2 — Diseño

**Fecha:** 2026-06-29
**Estado:** aprobado para plan

## Objetivo

Pulir el modal de producto recién reorganizado por secciones de canal, con 5 mejoras: (A) ocultar las secciones de canal según los checkboxes de "Canales de venta"; (B) toolbar de formato en el editor HTML de las descripciones de Nube; (C) simplificar el render del SEO (sacar el atenuado redundante); (D) pre-cargar el SEO de Nube leyéndolo del canal (como ya se hace con las descripciones); (E) reordenar el panel "Estado de publicación" para que coincida con "Canales de venta" (Dux primero).

## Alcance

A, B, C y E son **frontend** (`ProductoFormModal.tsx`, `HtmlEditor.tsx`, `productosService.ts` para tipos). D toca **backend** (lectura: `DatosCanalDTO` + `EstadoPublicacionService`) **y frontend** (pre-carga + tipos). Todo cohesivo con la idea de "leer del canal".

**Fuera de alcance:** persistir SEO en la BD (sigue sin persistirse; solo se lee del canal y se manda al sincronizar); WYSIWYG con dependencias; cambios en la lógica de export/guardado.

**Nota (no es parte de este trabajo):** en `ProductoFormModal.tsx` hay una validación de "proveedor obligatorio" comentada (`// PRUEBA … descartar fallo en Dux`) — es debugging del usuario, se deja intacta a pedido.

## Global Constraints

- Frontend: Next.js/React/TS. `cd supermaster-frontend && npx tsc --noEmit` exit 0; sin errores `error` de lint nuevos. Sin dependencias nuevas.
- Backend: Java 25 / Spring Boot 4; Jackson 3 (`tools.jackson`); `mvn -o test`. Cambio aditivo al contrato (no romper).
- Trabajar en `main`. Commits terminan con `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. `git add` solo de archivos específicos (hay cambios sueltos de backend en el working tree —Dux/proveedor— que NO deben tocarse ni agregarse), nunca `-A` ni `.superpowers/`.
- Records: agregar componentes rompe constructores posicionales en tests; correr `mvn -o test`.

## A. Secciones de canal ocultables (frontend)

Cada `fieldset` de canal se renderiza solo si su checkbox está tildado:
- Sección **Mercado Libre** → envuelta en `{subirMl && (...)}`.
- Sección **Tienda Nube · KT HOGAR** → `{subirKtHogar && (...)}`.
- Sección **Tienda Nube · KT GASTRO** → `{subirKtGastro && (...)}`.

La validación del form ya exige los campos de cada canal solo cuando su checkbox está activo, así que ocultar no genera errores de campos invisibles. En edición los checkboxes arrancan en `true` (se ve todo). Dux no tiene sección (sin cambio).

## B. Toolbar en `HtmlEditor` (frontend, sin dependencias)

Sobre el `textarea` de HTML se agrega una barra de botones que **envuelven la selección actual** con el tag correspondiente, manteniendo el modelo "HTML crudo + vista previa":

- **Negrita** → `<b>…</b>` · **Cursiva** → `<i>…</i>` · **Subrayado** → `<u>…</u>`.
- **Color** → `<input type="color">`; al cambiar, envuelve la selección con `<span style="color:#RRGGBB">…</span>`.
- **Lista** → envuelve la selección en `<ul><li>…</li></ul>`; si la selección tiene varias líneas, cada línea no vacía se vuelve un `<li>`; si no, un único `<li>`.

Mecánica: el componente usa un `ref` al `textarea` para leer `selectionStart`/`selectionEnd`, construye el nuevo `value` (prefijo + envoltura + sufijo), llama `onChange`, y tras el re-render reubica el cursor/selección sobre el contenido insertado (vía `ref` + `requestAnimationFrame` o equivalente). Sin selección, inserta el tag vacío con el cursor en el medio. La vista previa se actualiza sola. Respeta `disabled` y modo oscuro. Las props públicas (`value`, `onChange`, `disabled?`, `placeholder?`, `rows?`, `id?`) no cambian.

## C. Simplificar `renderSeoNube` (frontend)

Como las secciones Nube ahora se ocultan cuando el canal no está tildado (A), dentro de una sección visible el canal siempre está activo. Se elimina el parámetro/uso de `activoCanal` en `renderSeoNube` (el `opacity-50` y los `disabled={!activoCanal}`): el bloque SEO queda siempre habilitado. El botón "Generar SEO con IA" sigue con su propio estado `generandoSeo`.

## D. Pre-cargar SEO de Nube desde el canal (backend + frontend)

Hoy el SEO de Nube no se lee del canal (arranca vacío). Se agrega su lectura, reutilizando el JSON del producto Nube que `EstadoPublicacionService` ya obtiene por tienda (sin llamadas nuevas).

**Backend:**
- Nuevo record `SeoCanalDTO(String title, String description, String tags)` en `dominio/producto/estado/dto/`.
- `DatosCanalDTO` suma dos componentes: `SeoCanalDTO seoHogar`, `SeoCanalDTO seoGastro` (ambos pueden ser null si la tienda no tiene producto/SEO).
- `EstadoPublicacionService.leer`: de cada JSON de producto Nube (hogar/gastro) extrae el SEO con un parser nuevo `NubeSeoParser.parse(JsonNode product)`:
  - `title` = `product.path("seo_title")`; si es objeto → `.path("es").asString(null)`, si es textual → `asString(null)`.
  - `description` = `product.path("seo_description")` con el mismo criterio i18n/textual.
  - `tags` = `product.path("tags")`; si es array → unir sus elementos con `", "`; si es textual → `asString(null)`; null si ausente.
  - Devuelve `null` si `product` es null; si no, un `SeoCanalDTO` (con campos null donde no haya dato).
- `DatosCanalDTO.vacio()` se actualiza para incluir `seoHogar`/`seoGastro` en null.

**Frontend:**
- `productosService.ts`: `DatosCanal` suma `seoHogar` y `seoGastro` de tipo `{ title: string | null; description: string | null; tags: string | null } | null`.
- Al abrir (en el `.then` de `getEstadoPublicacionAPI`), pre-cargar `seoHogar`/`seoGastro` del modal desde `e.datos.seoHogar`/`e.datos.seoGastro`, mapeando a `{ title, description, tags }` (cada campo `?? ""`). Si vienen null, quedan vacíos.
- En alta (sin producto) siguen vacíos.

**Round-trip:** igual que las descripciones — se lee lo publicado, el usuario edita, y al sincronizar se manda (el export ya manda el SEO por tienda vía `SeoGeneradoDTO`). El SEO sigue **sin persistirse** en la BD del sistema.

## E. Reordenar el panel "Estado de publicación" (frontend)

El panel hoy muestra: Mercado Libre · KT HOGAR · KT GASTRO · Dux. Se reordena a **Dux · KT HOGAR · KT GASTRO · Mercado Libre**, igual que "Canales de venta". Solo se cambia el orden de las llamadas a `renderEstadoCanal` (y el grid si hace falta); los datos (`estadoCanales.dux/hogar/gastro/ml`) no cambian.

## Data flow (D)

1. Abrir modal → `GET /api/productos/{id}/estado-publicacion` (una sola GET por canal, como hoy).
2. `EstadoPublicacionService` arma `datos` con descripciones (ya existente) **+ SEO por tienda** (nuevo, del mismo JSON).
3. El modal pre-llena descripciones (ya) y ahora también `seoHogar`/`seoGastro`.

## Manejo de errores

- Sin cambios: si una tienda Nube falla/no existe, su SEO viene null → campos vacíos; el panel marca el estado como hoy.
- La toolbar nunca falla (manipula string); si no hay selección, inserta tags vacíos.

## Testing

- Backend: `mvn -o test` verde. Nuevo test `NubeSeoParserTest` (objeto i18n, textual, tags array vs string, product null). Ajustar cualquier test que construya `DatosCanalDTO` por constructor posicional (suma 2 componentes — ver memoria de records) y `EstadoPublicacionServiceTest` si corresponde.
- Frontend: `npx tsc --noEmit` exit 0. Verificación manual: (A) tildar/destildar canales muestra/oculta secciones; (B) seleccionar texto y aplicar negrita/cursiva/subrayado/color/lista envuelve y la preview lo refleja; (C) el SEO se ve habilitado dentro de una sección visible; (D) abrir un producto con SEO en Nube precarga title/description/tags; (E) el panel ordena Dux→Hogar→Gastro→ML.
