# EQUIPAMIENTO en KT GASTRO — Diseño

**Fecha:** 2026-06-29
**Estado:** aprobado para plan

## Objetivo

Al subir un producto a **Tienda Nube — canal KT GASTRO**, si el producto es de categoría **EQUIPAMIENTO**, marcar la publicación: agregar `*` al final del **título** y un bullet **"ENVIO A COTIZAR"** al final de la **descripción**.

## Alcance

Solo **backend**, y solo el canal **KT GASTRO** (alta y actualización). No afecta KT HOGAR, Mercado Libre, Dux, ni el dato persistido del producto.

**Fuera de alcance:** KT HOGAR; cambiar la categoría/resolución de Nube; UI.

## Global Constraints

- Backend: Java 25 / Spring Boot 4; `mvn -o test` (offline, `mvn` del PATH, NO `mvnw`). Jackson 3 donde aplique.
- OSIV off: el armado del payload (que toca asociaciones LAZY como `clasifGastro.getPadre()`) corre dentro del `@Transactional(readOnly=true)` de `NubeExportService.exportar` (ya es así).
- Trabajar en `main`. Commits terminan con `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. `git add` solo de archivos específicos (hay WIP de Dux en el working tree que NO se toca), nunca `-A` ni `.superpowers/`.

## Detección (`esEquipamiento`)

Un producto es "EQUIPAMIENTO" si, recorriendo la jerarquía de su **categoría de Nube** —la **clasificación gastronómica** si existe, sino la **general**—, **algún nodo** (hoja, padre, abuelo, …) tiene nombre **"EQUIPAMIENTO"** (comparación con `equalsIgnoreCase` sobre el nombre `trim()`eado). Es la misma fuente de categoría que ya usa Nube para resolver categorías (`clasifGastro` preferida, sino `clasifGral`), recorrida por `getPadre()`.

## Comportamiento (solo KT GASTRO + esEquipamiento)

- **Título (`name` de Nube):** se agrega `*` **pegado** al final (`titulo + "*"`). **Idempotente:** si el título ya termina en `*`, no se duplica.
- **Descripción:** se agrega al final el bullet en **texto plano** `<ul><li>ENVIO A COTIZAR</li></ul>`. **Idempotente:** si la descripción ya contiene "ENVIO A COTIZAR", no se vuelve a agregar. Si la descripción base está vacía, el payload de descripción queda solo con el bullet.

En cualquier otro caso (KT HOGAR, o KT GASTRO sin EQUIPAMIENTO) el título y la descripción van como hoy.

## Implementación

Se reutiliza el patrón de campos `@Transient` ya usado en `Producto`:

- **`Producto`:** nuevo `@Transient boolean equipamientoGastro` (no persistido).
- **`NubeExportService.exportar`** (loop producto × tienda): para cada par, antes de publicar, setear `producto.setEquipamientoGastro(TiendaNubeService.STORE_GASTRO.equals(tienda) && esEquipamiento(producto))`. Se setea **en cada iteración** (también a `false`), porque el mismo objeto `producto` se reutiliza para ambas tiendas. La función `esEquipamiento(Producto)` vive como helper estático (en `NubeExportService` o una clase util de Nube) y recorre la jerarquía gastro/gral.
- **Helpers de transformación** (para DRY entre alta y update), por ejemplo en una clase `NubeEquipamiento`:
  - `tituloConSufijo(String titulo, boolean eq)` → `eq && !titulo.endsWith("*") ? titulo + "*" : titulo`.
  - `descripcionConBullet(String desc, boolean eq)` → si `!eq` devuelve `desc`; si `desc` ya contiene "ENVIO A COTIZAR" devuelve `desc`; si no, `(desc == null ? "" : desc) + "<ul><li>ENVIO A COTIZAR</li></ul>"`.
- **Alta — `NubeProductoPayloadBuilder.construir`:** el `name` usa `NubeEquipamiento.tituloConSufijo(p.getTituloNube()…, p.isEquipamientoGastro())`; la descripción usa `NubeEquipamiento.descripcionConBullet(NubeDescripcionBuilder.construir(p), p.isEquipamientoGastro())` y se incluye en el payload si queda no vacía.
- **Update — PATCH en `TiendaNubeService`:** el `name` y la `description` del body aplican los mismos dos helpers con `producto.isEquipamientoGastro()`.

El título Nube es un campo **compartido** entre tiendas; por eso el `*` se aplica **al construir el payload de GASTRO** (vía el flag transient), sin tocar el valor guardado ni el payload de KT HOGAR.

## Manejo de errores / bordes

- Producto sin clasif gastro ni general → `esEquipamiento` = false (no aplica).
- Descripción vacía + equipamiento → la descripción enviada es solo el bullet.
- Re-publicación → idempotente (no duplica `*` ni el bullet).

## Testing

- Backend `mvn -o test` verde. Tests unitarios nuevos:
  - `esEquipamiento`: true cuando un nodo (hoja/padre) de la clasif gastro o general es "EQUIPAMIENTO" (case-insensitive); false si no; usa gastro si existe, sino general.
  - `NubeEquipamiento.tituloConSufijo`: agrega `*` si eq y no termina en `*`; idempotente; no toca si `!eq`.
  - `NubeEquipamiento.descripcionConBullet`: agrega el bullet si eq; idempotente (no duplica); no toca si `!eq`; maneja desc null/vacía.
  - `NubeProductoPayloadBuilder` (alta): con flag true, `name` termina en `*` y `description.es` contiene "ENVIO A COTIZAR"; con flag false, no.
- Ajustar tests existentes de `NubeProductoPayloadBuilder` si el flag default (false) cambia algo (no debería: default false = comportamiento actual).
