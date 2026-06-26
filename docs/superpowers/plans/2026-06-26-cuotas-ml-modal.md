# Selector de cuotas para Mercado Libre (modal) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** El canal Mercado Libre del modal de producto tiene un select "Cuota del precio" (default CONTADO/0), y la cuota elegida se usa para calcular el precio que se publica en ML.

**Architecture:** El backend ya soporta la cuota (`calcularPrecioFinalParaPublicar(producto, categoryId, int cuotas)`); hoy se le pasa `0` fijo. Se propaga la cuota desde el request del endpoint de export ML a través del flujo (`MlExportService → crear/actualizarItemEnMl`) hasta esa llamada. El frontend agrega el estado, las opciones del canal "ML" y el select, y manda la cuota en el body del export.

**Tech Stack:** Spring Boot 4 / Java 25, Bean Validation (`@NotNull`/`@Valid`), Lombok, Mockito; Next.js 16 / React 19 / TS.

## Global Constraints

- La cuota es **requerida** (`@NotNull`): el modal siempre la manda; un request con body pero sin `cuotas` → 400. No hay default ni fallback en el backend.
- Tests backend OFFLINE con el Maven instalado: `"/c/Program Files (x86)/apache-maven-3.9.12/bin/mvn" -o test` (NO `mvnw`). Acotar con `-Dtest=Clase`.
- DTOs `record`: agregar un componente a `MlExportRequestDTO` rompe los `new MlExportRequestDTO(...)` posicionales existentes; verificar con `mvn -o test` (no solo compile) y actualizar los callers que aparezcan.
- El único caller del flujo de export ML es el modal (`exportarProductosAMlAPI`); `crearItemEnMl`/`actualizarItemEnMl` solo se llaman desde `MlExportService`.
- Frontend: `npx tsc --noEmit` limpio.
- NO ejecutar nada que llame a la API real de ML. Solo tests unitarios offline (Mockito).
- Trabajar en `main`. Cada commit termina con: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

---

## File Structure

**Backend (todos modificados):**
- `apis/ml/dto/MlExportRequestDTO.java` — agregar `cuotas` (`@NotNull`).
- `apis/ml/controller/MlExportController.java` — `@Valid` en el body.
- `apis/ml/service/MlExportService.java` — propagar la cuota a `procesarConProductoCargado` y a crear/actualizar.
- `apis/ml/service/MercadoLibreService.java` — `crearItemEnMl`/`actualizarItemEnMl` reciben `int cuotas` y lo pasan a `calcularPrecioFinalParaPublicar` (reemplaza los dos `0`).
- Test: `apis/ml/MlExportServiceCuotaTest.java` (nuevo).

**Frontend (todos modificados):**
- `src/app/productos/productosService.ts` — `exportarProductosAMlAPI(skus, cuotas)`.
- `src/app/productos/ProductoFormModal.tsx` — estado `cuotaMl`/`cuotasMlOpts`, carga del canal "ML", select en la card de ML, y mandar la cuota al exportar.

---

### Task 1: Backend — propagar la cuota por el flujo de export ML

**Files:**
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/dto/MlExportRequestDTO.java`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/controller/MlExportController.java`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlExportService.java`
- Modify: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/MlExportServiceCuotaTest.java`

**Interfaces:**
- Consumes: `MercadoLibreService.crearItemEnMl(Producto)` y `actualizarItemEnMl(Producto, String)` (hoy sin cuota); `calcularPrecioFinalParaPublicar(Producto, String, int)` (ya acepta cuota).
- Produces: `crearItemEnMl(Producto, int cuotas)`, `actualizarItemEnMl(Producto, String, int cuotas)`, `MlExportService.procesarConProductoCargado(Integer, int cuotas)`, `MlExportRequestDTO(List<String> skus, Integer cuotas)`. Consumidos por el frontend (Task 2) vía el body `{ skus, cuotas }`.

- [ ] **Step 1: Escribir el test de propagación de la cuota (Mockito)**

Crear `MlExportServiceCuotaTest.java`. Verifica que la cuota recibida llega a `crearItemEnMl`/`actualizarItemEnMl` (en vez del `0` fijo de antes). Antes de escribirlo, confirmá los imports reales de `Producto`, `Mla` (la entidad que devuelve `producto.getMla()`) y la factory `ResultadoAltaMl.error(String)` / `ResultadoAltaMl.actualizado(String, String)` mirando `MlExportService.java` y `ResultadoAltaMl.java`. Estructura:

```java
package ar.com.leo.super_master_backend.apis.ml;

import ar.com.leo.super_master_backend.apis.ml.dto.ResultadoAltaMl;
import ar.com.leo.super_master_backend.apis.ml.service.MercadoLibreService;
import ar.com.leo.super_master_backend.apis.ml.service.MlExportService;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.mla.entity.Mla; // confirmá el package real de Mla
import ar.com.leo.super_master_backend.dominio.producto.mla.service.MlaService;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MlExportServiceCuotaTest {

    private final ProductoRepository productoRepository = mock(ProductoRepository.class);
    private final MercadoLibreService mercadoLibreService = mock(MercadoLibreService.class);
    private final MlaService mlaService = mock(MlaService.class);
    private final MlExportService service = new MlExportService(productoRepository, mercadoLibreService, mlaService);

    @Test
    void sinPublicacion_propagaLaCuotaAlAlta() {
        Producto p = new Producto();
        p.setId(1);
        p.setSku("SKU1");
        when(productoRepository.findById(1)).thenReturn(Optional.of(p));
        when(mercadoLibreService.buscarMlaPorSku("SKU1")).thenReturn(null);
        when(mercadoLibreService.crearItemEnMl(p, 6)).thenReturn(ResultadoAltaMl.error("irrelevante"));

        service.procesarConProductoCargado(1, 6);

        verify(mercadoLibreService).crearItemEnMl(p, 6);
        verify(mercadoLibreService, never()).crearItemEnMl(any(), eq(0));
    }

    @Test
    void conPublicacion_propagaLaCuotaAlUpdate() {
        Producto p = mock(Producto.class);
        Mla mla = mock(Mla.class);
        when(p.getId()).thenReturn(1);
        when(p.getMla()).thenReturn(mla);
        when(mla.getMla()).thenReturn("MLA123");
        when(productoRepository.findById(1)).thenReturn(Optional.of(p));
        when(mercadoLibreService.actualizarItemEnMl(p, "MLA123", 3))
                .thenReturn(ResultadoAltaMl.actualizado("MLA123", null));

        service.procesarConProductoCargado(1, 3);

        verify(mercadoLibreService).actualizarItemEnMl(p, "MLA123", 3);
    }
}
```

- [ ] **Step 2: Correr el test (debe fallar a compilar — las firmas con cuota aún no existen)**

Run: `"/c/Program Files (x86)/apache-maven-3.9.12/bin/mvn" -o -Dtest=MlExportServiceCuotaTest test`
Expected: FAIL de compilación (`procesarConProductoCargado(int,int)` / `crearItemEnMl(Producto,int)` no existen).

- [ ] **Step 3: Agregar `cuotas` al request DTO**

`MlExportRequestDTO.java` completo:
```java
package ar.com.leo.super_master_backend.apis.ml.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record MlExportRequestDTO(
        List<String> skus,
        @NotNull(message = "La cuota es obligatoria") Integer cuotas) {}
```

- [ ] **Step 4: Validar el body en el controller**

En `MlExportController.java`, agregar `@Valid` al parámetro (import `jakarta.validation.Valid`):
```java
    public ResponseEntity<ExportCanalResultDTO> exportar(@Valid @RequestBody(required = false) MlExportRequestDTO request) {
        return ResponseEntity.ok(mlExportService.exportar(request));
    }
```
(Mantener `required = false`: un POST sin body sigue cayendo al caso "vacío" de `exportar`; cuando hay body, `@Valid` exige `cuotas` no-null.)

- [ ] **Step 5: Propagar la cuota en `MlExportService`**

En `exportar`, tras el guard, leer la cuota y pasarla al procesamiento. Cambiar la línea `ResultadoAltaMl r = self.procesarConProductoCargado(productoId);` por:
```java
            ResultadoAltaMl r = self.procesarConProductoCargado(productoId, request.cuotas());
```
Y la firma + cuerpo de `procesarConProductoCargado`:
```java
    @Transactional(readOnly = true)
    public ResultadoAltaMl procesarConProductoCargado(Integer productoId, int cuotas) {
        Producto p = productoRepository.findById(productoId).orElse(null);
        if (p == null) return ResultadoAltaMl.error("producto no encontrado");

        String mla = (p.getMla() != null) ? p.getMla().getMla() : null;
        String mlauHallado = null;
        if (mla == null) {
            var encontrado = mercadoLibreService.buscarMlaPorSku(p.getSku());
            if (encontrado != null) { mla = encontrado.mla(); mlauHallado = encontrado.mlau(); }
        }
        if (mla != null && !mla.isBlank()) {
            ResultadoAltaMl r = mercadoLibreService.actualizarItemEnMl(p, mla, cuotas);
            if (mlauHallado != null && r.estado() == ResultadoAltaMl.Estado.ACTUALIZADO) {
                ResultadoAltaMl conMlau = ResultadoAltaMl.actualizado(mla, mlauHallado);
                return r.advertencia() != null ? conMlau.conAdvertencia(r.advertencia()) : conMlau;
            }
            return r;
        }
        return mercadoLibreService.crearItemEnMl(p, cuotas);
    }
```

- [ ] **Step 6: Recibir y usar la cuota en `MercadoLibreService`**

(a) `crearItemEnMl`: cambiar la firma `public ResultadoAltaMl crearItemEnMl(...Producto producto)` por `(...Producto producto, int cuotas)`, y dentro reemplazar:
```java
        precioFinal = calcularPrecioFinalParaPublicar(producto, categoryId, 0); // 0 = contado (Fase 1)
```
por:
```java
        precioFinal = calcularPrecioFinalParaPublicar(producto, categoryId, cuotas);
```

(b) `actualizarItemEnMl`: cambiar la firma `public ResultadoAltaMl actualizarItemEnMl(...Producto producto, String mla)` por `(...Producto producto, String mla, int cuotas)`, y dentro reemplazar:
```java
                precioFinal = calcularPrecioFinalParaPublicar(producto, categoryIdActual, 0); // 0 = contado (Fase 1)
```
por:
```java
                precioFinal = calcularPrecioFinalParaPublicar(producto, categoryIdActual, cuotas);
```

- [ ] **Step 7: Correr el test (debe pasar) + suite ML**

Run: `"/c/Program Files (x86)/apache-maven-3.9.12/bin/mvn" -o -Dtest="MlExportServiceCuotaTest,CrearItemEnMlTest,ActualizarItemEnMlTest" test`
Expected: BUILD SUCCESS, todos verdes. Si algún otro test/clase usa `new MlExportRequestDTO(...)` o `crearItemEnMl(...)`/`actualizarItemEnMl(...)` con la firma vieja y rompe la compilación, actualizá esos callsites agregando la cuota (corré `mvn -o test-compile` para detectarlos) y reportalo.

- [ ] **Step 8: Commit**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/dto/MlExportRequestDTO.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/controller/MlExportController.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MlExportService.java supermaster-backend/src/main/java/ar/com/leo/super_master_backend/apis/ml/service/MercadoLibreService.java supermaster-backend/src/test/java/ar/com/leo/super_master_backend/apis/ml/MlExportServiceCuotaTest.java
git commit -m "feat(ml): export usa la cuota elegida para el precio (en vez de contado fijo)"
```

---

### Task 2: Frontend — select de cuotas en la card de Mercado Libre

**Files:**
- Modify: `supermaster-frontend/src/app/productos/productosService.ts:311-319`
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`

**Interfaces:**
- Consumes: el endpoint de export ML que ahora espera `{ skus, cuotas }` (Task 1); `getCuotasPorCanalAPI`, `searchCanales`, el tipo `CuotaOpcion`, la constante `selectBaseClassName` y el helper `etiquetaCuota` (ya existen en el modal, usados por los selects de Nube).
- Produces: nada para otras tareas (es la capa de UI final).

- [ ] **Step 1: `exportarProductosAMlAPI` manda la cuota**

En `productosService.ts`, reemplazar la función actual por:
```typescript
export const exportarProductosAMlAPI = async (skus: string[], cuotas: number): Promise<ExportCanalResultDTO> => {
	const res = await fetchAPI(`${API_BASE_URL}/api/ml/exportar-productos`, {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify({ skus, cuotas }),
	});
	if (!res.ok) throw new Error(await extraerMensajeError(res, "No se pudo subir el producto a Mercado Libre"));
	return await res.json();
};
```

- [ ] **Step 2: Estado de la cuota de ML en el modal**

En `ProductoFormModal.tsx`, junto a los estados `cuotaHogar`/`cuotaGastro`/`cuotasHogarOpts`/`cuotasGastroOpts`, agregar:
```typescript
    const [cuotaMl, setCuotaMl] = useState<number>(0);
    const [cuotasMlOpts, setCuotasMlOpts] = useState<CuotaOpcion[]>([]);
```

- [ ] **Step 3: Cargar las opciones del canal "ML" en el `useEffect` de cuotas**

En el `useEffect` que hoy carga las cuotas de Nube (el que llama `cargarCuotasCanal("KT HOGAR")` y `cargarCuotasCanal("KT GASTRO")`), agregar la carga del canal `"ML"` al `Promise.all` y setear su estado. Reemplazar el bloque interno:
```typescript
        const [hogar, gastro] = await Promise.all([
            cargarCuotasCanal("KT HOGAR"),
            cargarCuotasCanal("KT GASTRO"),
        ]);
        if (cancelado) return;
        setCuotasHogarOpts(hogar);
        setCuotasGastroOpts(gastro);
        if (hogar.length && !hogar.some(c => c.cuotas === -1)) setCuotaHogar(hogar[0].cuotas);
        if (gastro.length && !gastro.some(c => c.cuotas === 6)) setCuotaGastro(gastro[0].cuotas);
```
por:
```typescript
        const [hogar, gastro, ml] = await Promise.all([
            cargarCuotasCanal("KT HOGAR"),
            cargarCuotasCanal("KT GASTRO"),
            cargarCuotasCanal("ML"),
        ]);
        if (cancelado) return;
        setCuotasHogarOpts(hogar);
        setCuotasGastroOpts(gastro);
        setCuotasMlOpts(ml);
        if (hogar.length && !hogar.some(c => c.cuotas === -1)) setCuotaHogar(hogar[0].cuotas);
        if (gastro.length && !gastro.some(c => c.cuotas === 6)) setCuotaGastro(gastro[0].cuotas);
        // Default ML: CONTADO (0). El canal ML trae la cuota 0; si no, cae a la primera opción.
        if (ml.length && !ml.some(c => c.cuotas === 0)) setCuotaMl(ml[0].cuotas);
```

- [ ] **Step 4: Agregar el select en la card de Mercado Libre**

En la card de ML (la del checkbox `subirMl`, con `ShoppingBagIcon`), después del `<div className="flex items-center gap-3">…</div>` que contiene el checkbox y antes de cerrar la card, agregar el bloque del select (mismo patrón que los de Nube):
```tsx
                                {subirMl && (
                                    <div className="flex items-center gap-2 border-t border-slate-200/70 pt-2 dark:border-slate-700/60">
                                        <span className="text-xs font-normal text-slate-500 dark:text-slate-400">Cuota del precio</span>
                                        <Tooltip content="Plan de cuotas con el que se publica el precio en Mercado Libre (cada plan aplica su recargo de financiación)." className="flex-1">
                                            <select className={`${selectBaseClassName} w-full`} value={cuotaMl} onChange={e => setCuotaMl(Number(e.target.value))}>
                                                {(cuotasMlOpts.length ? cuotasMlOpts : [{cuotas:0,descripcion:"Contado"}]).map(c => (
                                                    <option key={c.cuotas} value={c.cuotas}>{c.descripcion}</option>
                                                ))}
                                            </select>
                                        </Tooltip>
                                    </div>
                                )}
```

- [ ] **Step 5: Mandar la cuota al exportar a ML**

En `ejecutarExportsCanales`, en la tarea de "Mercado Libre", cambiar `const r = await exportarProductosAMlAPI([skuExport]);` por:
```typescript
                    const r = await exportarProductosAMlAPI([skuExport], cuotaMl);
```

- [ ] **Step 6: Type-check**

Run: `cd supermaster-frontend && npx tsc --noEmit`
Expected: exit 0.

- [ ] **Step 7: Commit**

```bash
git add supermaster-frontend/src/app/productos/productosService.ts supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(front/productos): selector de cuotas para Mercado Libre (default contado)"
```

---

## Notas de verificación final

- El precio que ML publica ahora depende del plan elegido (ej. "6 CUOTAS ML" → +13.2%); CONTADO (0) reproduce el comportamiento previo.
- Smoke manual (requiere backend + front, NO automatizado): en el modal, elegir una cuota ≠ contado para ML y subir; verificar que el precio publicado refleja el recargo. Con CONTADO, el precio es el de hoy.
