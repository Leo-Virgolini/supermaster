# Datos de canal fieles a la API en el modal de producto

**Fecha:** 2026-07-01
**Estado:** Aprobado (diseño)

## Objetivo

Que el modal de edición de productos muestre, para cada canal, **lo que realmente hay en la API del canal**, sin rellenar automáticamente con datos locales del producto al editar. La única ayuda desde datos locales al editar es el botón "Componer descripción sugerida". Al **crear** un producto nuevo el autocompletado desde datos locales sí tiene sentido (no hay canal de dónde leer) y se conserva.

Alcance concreto (según pedido de Leo + capturas):

1. **Título ML** debe venir de la API de ML (hoy sale de la columna local `titulo_ml`).
2. **Atributos ML** deben venir de la API de ML al editar (hoy se pisan con datos locales: dimensiones, Marca, Material).
3. **Tienda Nube** ya cumple (SEO/título/descripción vienen de la API; la descripción sugerida solo se compone con el botón). No requiere cambios.
4. **Tooltip de imágenes**: mostrar siempre el path real de las carpetas en vez del fallback `app.imagenes-dir` / `app.imagenes-raw-dir`.

## Contexto actual

- El modal es [ProductoFormModal.tsx](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx). El modo se distingue por la prop `producto`: presente = **edición** (setea `editandoProductoId`), ausente = **creación**.
- Al abrir en edición se llaman 4 endpoints de estado por canal: `/api/productos/{id}/estado-publicacion/{ml|hogar|gastro|dux}`.
- El canal ML se lee en `EstadoPublicacionService.leerMl()` → resuelve el MLA por SKU contra la API, lee el ítem RAW y arma `MlCanalDTO` con estado/categoría/atributos/descripción/paquete vía `MlDatosParser`. **Hoy `MlCanalDTO` no incluye el título.**
- El título ML hoy es una **columna persistida** `productos.titulo_ml`, presente en la entidad `Producto`, 6 DTOs (`ProductoDTO`, `ProductoCreateDTO`, `ProductoUpdateDTO`, `ProductoPatchDTO`, `ProductoConPreciosDTO`, `ProductoResumenDTO`), `ProductoMapper`, auditoría (`ProductoAuditoriaServiceImpl`), export de Excel (`ExcelServiceImpl:3136`) y en la publicación a ML (`MercadoLibreService`: family_name y **predicción de categoría** vía `resolverCategoriaMl`).
- Precedente: categoría ML, atributos ML y descripción ya se externalizaron (columnas dropeadas, campos `@Transient`, se leen del canal al abrir y se mandan al publicar en `MlExportRequestDTO`). Este trabajo cierra ese patrón sumando el título ML.

## Sección 1 — Título ML: externalizar

Seguir el mismo patrón que categoría/atributos/descripción.

**Backend**
- Dropear la columna `titulo_ml` (script SQL manual; `ddl-auto=validate` exige correrlo en cada entorno antes de desplegar). `db/2026-07-01-drop-titulo-ml.sql`, idempotente (condicionado a `information_schema`).
- En `Producto`, `tituloMl` pasa de `@Column(name="titulo_ml")` a `@Transient`.
- `MlDatosParser` parsea el `title` del ítem ML; `MlCanalDTO` gana un campo `titulo`; `leerMl()` lo completa.
- `MlExportRequestDTO` suma `String tituloMl`; el publish desde el modal lo setea en la entidad cargada antes de publicar. **Null = no tocar**: en re-sync por lote va null y no se pisa el título del ítem (igual que descripción/atributos hoy).
- La predicción de categoría (`resolverCategoriaMl`) sigue funcionando en el flujo del modal porque el título viaja en el request; el lote no predice categoría nueva.
- Quitar `tituloMl` de la persistencia: mapper, patch service (`ProductoServiceImpl` ~1123/1193), auditoría snapshot, y de la columna de `ExcelServiceImpl`.

**Frontend**
- En edición, precargar `tituloMl` desde el `MlCanalDTO` (campo `titulo`) en vez de `producto.tituloMl` ([línea 636](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx#L636)). Vacío si el producto no está publicado.
- En creación, arranca vacío (lo escribe el usuario), como hoy.
- Al publicar desde el modal, incluir `tituloMl` en el request de export ML.

**Consecuencias aceptadas**
- La columna "Título ML" del **export de Excel** queda vacía/removida (igual que pasó con "Categoría ML"): el dato ya no se persiste.
- La **auditoría** deja de registrar cambios de título ML.
- Se rompen constructores posicionales de records en tests de los 6 DTOs → ajustar tests (`mvnw test`, no `compile`).

## Sección 2 — Atributos ML: fieles al canal al editar

Los valores del form de atributos ML deben venir solo de la API al editar; el espejado desde datos locales se conserva solo al crear.

Los 3 `useEffect` de espejado en [ProductoFormModal.tsx:1125-1199](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx#L1125-L1199) se **condicionan al modo creación** (`!editandoProductoId`):
- 1126-1143: dimensiones (HEIGHT/WIDTH/LENGTH/DIAMETER/MOUTH_DIAMETER/BASE_DIAMETER/CAPACITY/THICKNESS) desde campos físicos.
- 1165-1180: `BRAND` desde la Marca maestra.
- 1183-1199: `MATERIAL` desde el Material maestro.

- **Creación:** corren → pre-llenan Marca, Material y dimensiones (ayuda para la primera carga).
- **Edición:** no corren → los atributos quedan poblados solo desde `e.atributos` (API de ML).

Se conservan sin cambios los dos `useEffect` que no inventan datos: podar atributos "stale" de otra categoría (1112-1123) y limpiar el flag `noAplica` en required (1147-1161).

**Consecuencia aceptada:** al editar un producto no publicado, el form de atributos arranca vacío y hay que completar a mano.

## Sección 3 — Tienda Nube: sin cambios

Verificado que SEO (title/description/tags), título y descripción vigente vienen de la API de Nube al abrir (`EstadoPublicacionService.leerNube` → `NubeCanalDTO`), y que la descripción sugerida (las "Características Principales") solo se compone al apretar "Componer descripción sugerida" (`NubeDescripcionSugeridaBuilder`, desde datos locales, endpoint `descripcion-sugerida?canal=nube`). No hay autocompletado automático al abrir. No se toca nada.

## Sección 4 — Tooltip de imágenes: path real siempre

El tooltip en [ProductoFormModal.tsx:1975-1976](../../../supermaster-frontend/src/app/productos/ProductoFormModal.tsx#L1975-L1976) ya intenta mostrar `crudasDisp?.destinoDir.ruta` / `crudasDisp?.crudaDir.ruta`, pero `crudasDisp` solo se carga al abrir el selector de carátula (`abrirSelectorCaratula`), así que la mayor parte del tiempo cae al fallback literal `app.imagenes-dir` / `app.imagenes-raw-dir` (nombre de la property, no path).

**Cambio:** cargar los paths de las carpetas al abrir el modal (llamar `getCrudasAPI(sku)` una vez al montar cuando hay SKU, poblando `crudasDisp`), para que el tooltip muestre siempre los paths reales. Manejar error en silencio (fallback actual) para no romper el modal si la carpeta no es legible.

## Testing

- Backend: ajustar tests de records afectados por quitar/mover `tituloMl`; verificar `leerMl()` parsea el título; verificar que el publish por lote no pisa el título (null = no tocar) y el publish del modal sí lo aplica. Correr con `mvn -o test` (ver memoria de tests offline).
- Frontend: smoke visual de Leo — editar un producto publicado en ML (título y atributos deben reflejar el canal), crear uno nuevo (espejado local funciona), verificar tooltip con paths reales.

## Pasos manuales (operativos)

- Aplicar `db/2026-07-01-drop-titulo-ml.sql` en cada entorno antes de desplegar (ddl-auto=validate). Script idempotente.

## Orden sugerido de implementación

1. Backend título ML: `MlDatosParser` + `MlCanalDTO.titulo` + `leerMl()` (leer de la API).
2. Backend externalización: `@Transient`, quitar de mapper/patch/auditoría/excel, SQL drop, `MlExportRequestDTO.tituloMl` + setear en publish (null = no tocar).
3. Frontend título ML: precarga desde canal en edición, incluir en request de publicación.
4. Frontend atributos: condicionar los 3 `useEffect` de espejado a creación.
5. Frontend tooltip: cargar `crudasDisp` al abrir el modal.
6. Ajustar tests de records; correr `mvn -o test`.
