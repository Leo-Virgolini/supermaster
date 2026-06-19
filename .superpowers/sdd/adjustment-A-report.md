# Adjustment A Report: idDux separado en Proveedor y UnidadMedida

**Commit SHA:** `be9d930`
**Fecha:** 2026-06-19

## Objetivo

Separar el campo `idDux` (id Dux externo) del PK interno para `Proveedor` y `UnidadMedida`, de modo que `DuxItemBuilder` use `getIdDux()` en lugar de `getId()` al armar el payload.

## Archivos modificados

### Entidades

- `dominio/proveedor/entity/Proveedor.java`: Agregado `@Column(name = "id_dux") private Integer idDux;` (nullable)
- `dominio/unidad_medida/entity/UnidadMedida.java`:
  - PK: agregado `@GeneratedValue(strategy = GenerationType.IDENTITY)` (ahora autoincremental)
  - Agregado `@Column(name = "id_dux") private Integer idDux;` (nullable)
  - Constructor `UnidadMedida(Integer id)` CONSERVADO (usado por ProductoMapper)

### DTOs de Proveedor

- `dto/ProveedorDTO.java`: agregado `Integer idDux` al record
- `dto/ProveedorCreateDTO.java`: agregado `Integer idDux` (sin validacion - opcional)
- `dto/ProveedorUpdateDTO.java`: agregado `Integer idDux`
- `dto/ProveedorPatchDTO.java`: agregado `JsonNullable<Integer> idDux = JsonNullable.undefined();`

### Servicios de Proveedor

- `service/ProveedorServiceImpl.java`:
  - Guard PATCH vacio: agregado `!presente(patchDto.getIdDux())`
  - Aplicacion PATCH: `entity.setIdDux(leerIntegerOpcional(patchDto.getIdDux(), "idDux"))`
- `service/ProveedorAuditoriaServiceImpl.java`: agregado `idDux` al snapshot

### DuxItemBuilder

- `apis/dux/service/DuxItemBuilder.java`:
  - `id_proveedor`: `getProveedor().getId()` -> `getProveedor().getIdDux()`
  - `id_unidad_medida`: `getUnidadMedida().getId()` -> `getUnidadMedida().getIdDux()`

### Test

- `apis/dux/DuxItemBuilderTest.java`:
  - `prov.setId(77)` -> `prov.setIdDux(77)`
  - `new UnidadMedida(18)` -> `new UnidadMedida(); um.setIdDux(18);`

## Cambios SQL

### dux-mapping-fields.sql (append)
```sql
ALTER TABLE supermaster.proveedores ADD COLUMN id_dux INT NULL;
```

### unidad-medida.sql (reescritura completa)
- PK: `id_unidad_medida INT AUTO_INCREMENT PRIMARY KEY`
- Nueva columna: `id_dux INT NULL`
- Inserts: `(codigo, id_dux)` - PK se autoincrementa
- `id_dux` 1..22 son PROVISORIOS; reemplazar con ids reales de Dux antes de produccion

## Resultados

- Tests `DuxItemBuilderTest` y `DuxClasifResolverTest`: PASAN
- `mvnw.cmd -q -DskipTests compile`: EXITOSO

## Commit SHA

`be9d930`

## Fix (2026-06-19)

- **Fix 1 — test coverage**: Agregado `construir_omiteIdsCuandoIdDuxNull` en `DuxItemBuilderTest` para cubrir la rama donde `proveedor`/`unidadMedida` son non-null pero su `idDux` es null → se verifica que `id_proveedor` e `id_unidad_medida` NO aparecen en el mapa. Total: 3/3 tests pasando.
- **Fix 2 — nota SQL**: Agregado comentario de advertencia en `unidad-medida.sql` indicando que si la tabla ya existe con la versión anterior (PK = id Dux, sin columna `id_dux`), hay que hacer `DROP TABLE IF EXISTS supermaster.unidades_medida;` antes de re-ejecutar el script.
- **Bonus fix**: Corregido `RecalculoAutomaticoIntegrationTest.java` (línea 786) — llamada al constructor `ProveedorUpdateDTO` que faltaba el argumento `idDux` (null) agregado en el commit anterior `be9d930`.
