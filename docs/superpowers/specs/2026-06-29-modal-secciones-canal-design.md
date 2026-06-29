# Modal de producto por secciones de canal — Diseño

**Fecha:** 2026-06-29
**Estado:** aprobado para plan

## Objetivo

Reorganizar el modal de edición/alta de producto para que los campos **específicos de cada canal** vivan en su propia sección (Mercado Libre, Tienda Nube · KT HOGAR, Tienda Nube · KT GASTRO), en vez de la mezcla actual donde la sección "MercadoLibre" contiene también las descripciones de Nube. Además: (1) **editor HTML con vista previa** para las descripciones de Nube, (2) **indicador de carga** en los campos que se llenan desde el canal al abrir, y (3) **destacar el SKU** visualmente por ser el identificador del producto.

## Alcance

**Solo frontend.** No cambia el backend, los endpoints ni los contratos. Es un reflow del JSX de `supermaster-frontend/src/app/productos/ProductoFormModal.tsx` más un componente nuevo `HtmlEditor.tsx`. Los datos y su carga (endpoint `estado-publicacion` → `datos`, export, etc.) ya existen.

**Fuera de alcance:** cambios de datos/persistencia; sección propia para Dux; editor rico WYSIWYG con dependencias; sanitización avanzada de HTML.

## Global Constraints

- Frontend: Next.js/React/TS. `cd supermaster-frontend && npx tsc --noEmit` exit 0; sin errores `error` de lint nuevos. No hay tests automáticos de frontend.
- Trabajar en `main`. Commits terminan con `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. `git add` solo de archivos específicos, nunca `-A` ni `.superpowers/`.
- No agregar dependencias nuevas (el editor HTML es sin librerías).
- Mantener el comportamiento funcional actual: mismos estados, mismas llamadas de guardado/export, misma pre-carga desde `estado.datos`. Es un cambio de **presentación/orden**, no de lógica.

## Estructura de secciones (apiladas)

Orden de fieldsets en la pestaña "Datos":

1. **Estado de publicación** (panel, arriba — como está hoy).
2. **Canales de venta** (checkboxes de sincronización + cuotas — como está).
3. **Identificación (Generales):** SKU (destacado, ver abajo), código externo, **título Dux**, combo/activo, uxb, moq, stock, tags, **EAN** (movido acá desde ML), imágenes del SKU.
4. **Económicos** · **Reposición y Stock** · **Márgenes** (como están).
5. **Clasificación y Relaciones:** marca, origen, tipo, clasif. general/gastro, proveedor, material, **sector de depósito** (todos generales; Dux usa título Dux + sector, que quedan acá).
6. **Catálogos y Clientes** (como está).
7. **Dimensiones Físicas** (largo/ancho/alto/capacidad/diám./espesor — como está).
8. **Mercado Libre:** título ML · bloque MLA (código MLA, MLAU, precio envío, comisión, tope + botón "Autocompletar desde MercadoLibre") · categoría ML + predictor · ficha técnica (atributos) · paquete de envío ML (alto/ancho/largo/peso) · **Descripción ML** (textarea texto plano).
9. **Tienda Nube · KT HOGAR:** título Nube (compartido, sincronizado) · SEO Hogar (title/description/tags) · **Descripción Hogar** (editor HTML).
10. **Tienda Nube · KT GASTRO:** título Nube (mismo, sincronizado) · SEO Gastro · **Descripción Gastro** (editor HTML).
11. **Precios Inflados por Canal** (multi-canal — como está).
12. **Estado de subidas** (toast/resumen post-guardado, si algo falló — como está).

La sección "SEO de Tienda Nube" actual (que junta el SEO de ambas tiendas) se **divide** y su contenido se reparte en las secciones 9 y 10. Las 3 descripciones (hoy juntas bajo "MercadoLibre") se reparten: ML → sección 8, Hogar → sección 9, Gastro → sección 10.

Dux **no** tiene sección propia (sus campos de presentación son generales; su estado se ve en el panel).

## Título Nube sincronizado

`titulo_nube` es una sola propiedad (una columna). Se renderiza un input en KT HOGAR y otro en KT GASTRO, ambos atados al mismo estado `tituloNube`/`setTituloNube`: editar cualquiera actualiza el otro (es el mismo dato). Cada uno con una nota corta ("compartido entre KT HOGAR y KT GASTRO").

## SKU destacado

El input del SKU (sección Identificación) recibe un acento **índigo/azul**: borde índigo, fondo levemente tintado, texto en negrita monoespaciada, y la etiqueta "SKU" en índigo. Es solo estilo (clases Tailwind); no cambia validación ni comportamiento. Debe respetar modo oscuro (variantes `dark:`).

## Editor HTML (descripciones de Nube)

Componente nuevo `src/app/productos/HtmlEditor.tsx` (reusable), sin dependencias:

- Props: `value: string`, `onChange: (v: string) => void`, `disabled?: boolean`, `placeholder?: string`, `rows?: number`, `id?: string`.
- Layout: un `textarea` con el HTML crudo + una **vista previa en vivo** en un recuadro al lado (en pantallas anchas) o debajo (apiladas en angostas), que renderiza el HTML con `dangerouslySetInnerHTML`. La preview tiene un rótulo "Vista previa" y estilos de prosa básicos.
- El HTML es contenido propio/interno (lo genera el sistema o lo edita el usuario); se renderiza solo en la preview del modal. No se agrega sanitización (fuera de alcance; se documenta el supuesto de contenido confiable).
- Se usa en **Descripción Hogar** y **Descripción Gastro**. La **Descripción ML** sigue siendo un `textarea` plano (ML es texto plano, sin HTML).
- El botón "Componer descripción sugerida" de cada descripción Nube sigue funcionando: pre-llena el `value` del editor.

## Indicador de carga

Al abrir el modal en modo edición se dispara `getEstadoPublicacionAPI` (una sola llamada que trae estado + `datos`). Se reutiliza la bandera existente `cargandoEstado` para marcar como "cargando" los campos que se llenan desde el canal mientras esa llamada está en vuelo:

- Tarjetas del panel de estado: ya muestran "Leyendo estado…" (sin cambios).
- **Descripción ML, Descripción Hogar, Descripción Gastro:** mientras `cargandoEstado`, mostrar un estado de carga (skeleton o spinner + input deshabilitado).
- **Categoría ML + ficha técnica (atributos):** ídem, indicador de carga mientras `cargandoEstado`.

Como es una sola llamada, los indicadores aparecen/desaparecen a la vez, pero quedan señalizados por sección. En alta (sin producto) no hay carga: los campos arrancan vacíos y editables.

## Componentes y data flow

- `ProductoFormModal.tsx`: se reordena el JSX en las secciones de arriba. **No cambian** los `useState`, los handlers de guardado/creación/export, ni la pre-carga desde `estado.datos`. Los campos se mueven de lugar, no de lógica.
- `HtmlEditor.tsx` (nuevo): encapsula textarea + preview. Reemplaza los dos `textarea` de descripción Nube.
- Sin cambios en `productosService.ts` ni en `types.ts`.

## Manejo de errores

- Sin cambios respecto de hoy: errores de carga de estado → toast (`notificar.error`), `esSesionExpirada` suprime el toast en 401. La preview del editor HTML nunca falla (si el HTML es inválido, el navegador lo renderiza best-effort).

## Testing

- `npx tsc --noEmit` exit 0. Verificación manual: (a) cada sección muestra sus campos y nada cruzado; (b) editar el título Nube en una tienda actualiza la otra; (c) la preview del editor HTML refleja el HTML; (d) al abrir un producto publicado se ven los indicadores de carga y luego los datos; (e) el SKU se ve destacado en claro y oscuro; (f) guardar y exportar siguen funcionando igual.
