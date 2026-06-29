# Comparar cruda vs carátula generada — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** En el modal de producto, al generar la carátula mostrar la imagen cruda (original) y la generada lado a lado, con botones Aceptar / Volver a generar / Cancelar.

**Architecture:** El endpoint de generar pasa a devolver también la imagen cruda en base64 (reutilizando los bytes que ya lee), vía un DTO ampliado. El modal muestra las dos imágenes y los tres botones.

**Tech Stack:** Spring Boot 4 / Java 25 / Maven / JUnit 5 + AssertJ; frontend Next.js/React/TS.

## Global Constraints

- Tests backend: `mvn -o test` (offline, `mvn` del PATH, NO `mvnw`). Frontend: `npx tsc --noEmit -p tsconfig.json` exit 0, sin errores `error` de lint nuevos. No hay tests automáticos de frontend.
- Trabajar en `main`. Commits terminan con `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. `git add` solo de archivos específicos, nunca `-A` ni `.superpowers/`.
- Paquete base backend: `ar.com.leo.super_master_backend`.

## File Structure

**Backend:**
- Create: `dominio/imagen/service/GeneracionCaratula.java` (record interno cruda+generada).
- Create: `src/test/.../dominio/imagen/controller/ImagenControllerMimeTest.java` (test del helper MIME).
- Modify: `dominio/imagen/service/CaratulaService.java` (`generar` devuelve `GeneracionCaratula`).
- Modify: `dominio/imagen/dto/CaratulaGeneradaDTO.java` (agrega `crudaBase64`, `crudaFormato`).
- Modify: `dominio/imagen/controller/ImagenController.java` (endpoint generar + helper `subtipoMimeDe`).

**Frontend:**
- Modify: `src/app/productos/productosService.ts` (tipo de retorno de `generarCaratulaAPI`).
- Modify: `src/app/productos/ProductoFormModal.tsx` (estados cruda, handler cancelar, bloque de preview con 2 imágenes + 3 botones).

---

## Task 1: Backend — devolver la imagen cruda junto con la generada

**Files:**
- Create: `supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/imagen/service/GeneracionCaratula.java`
- Modify: `.../dominio/imagen/service/CaratulaService.java`
- Modify: `.../dominio/imagen/dto/CaratulaGeneradaDTO.java`
- Modify: `.../dominio/imagen/controller/ImagenController.java`
- Test: `supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/imagen/controller/ImagenControllerMimeTest.java`

**Interfaces:**
- Produces:
  - `GeneracionCaratula(byte[] cruda, String crudaNombre, byte[] generada)`.
  - `CaratulaService.generar(String sku): GeneracionCaratula` (antes devolvía `byte[]`).
  - `CaratulaGeneradaDTO(String imagenBase64, String formato, String crudaBase64, String crudaFormato)`.
  - `ImagenController.subtipoMimeDe(String nombre): String` (static, package-private).
- Consumes: `ImagenService.resolverCrudaPorSku`, `leerCrudaBytes`; `OpenAiImagenService.generarCaratula(byte[], String)`; `caratulaService.formato()`; `NotFoundException`.

- [ ] **Step 1: Crear el record `GeneracionCaratula`.**

Create `GeneracionCaratula.java`:

```java
package ar.com.leo.super_master_backend.dominio.imagen.service;

/** Resultado de generar una carátula: la imagen cruda usada y la generada (bytes en memoria, sin guardar). */
public record GeneracionCaratula(byte[] cruda, String crudaNombre, byte[] generada) {}
```

- [ ] **Step 2: Cambiar `CaratulaService.generar` para devolver `GeneracionCaratula`.**

En `CaratulaService.java`, reemplazar el método `generar` por:

```java
    /** Genera (sin guardar) la carátula a partir de la cruda del SKU; devuelve cruda + generada. */
    public GeneracionCaratula generar(String sku) {
        String crudaNombre = imagenService.resolverCrudaPorSku(sku);
        if (crudaNombre == null) throw new NotFoundException("No hay imagen cruda para el SKU " + sku);
        byte[] cruda = imagenService.leerCrudaBytes(crudaNombre);
        byte[] generada = openAiImagenService.generarCaratula(cruda, crudaNombre);
        return new GeneracionCaratula(cruda, crudaNombre, generada);
    }
```

(El resto de `CaratulaService` —`formato()`, `guardar(...)`, `extDe(...)`— no cambia.)

- [ ] **Step 3: Ampliar `CaratulaGeneradaDTO`.**

Replace `CaratulaGeneradaDTO.java` con:

```java
package ar.com.leo.super_master_backend.dominio.imagen.dto;

public record CaratulaGeneradaDTO(String imagenBase64, String formato, String crudaBase64, String crudaFormato) {}
```

- [ ] **Step 4: Escribir el test (rojo) del helper MIME.**

Create `ImagenControllerMimeTest.java`:

```java
package ar.com.leo.super_master_backend.dominio.imagen.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImagenControllerMimeTest {

    @Test
    void subtipoMimeDe_mapeaExtensiones() {
        assertThat(ImagenController.subtipoMimeDe("foto.jpg")).isEqualTo("jpeg");
        assertThat(ImagenController.subtipoMimeDe("foto.JPEG")).isEqualTo("jpeg");
        assertThat(ImagenController.subtipoMimeDe("X.png")).isEqualTo("png");
        assertThat(ImagenController.subtipoMimeDe("X.webp")).isEqualTo("webp");
    }
}
```

- [ ] **Step 5: Correr el test y verlo fallar.**

Run: `cd supermaster-backend && mvn -o test -Dtest=ImagenControllerMimeTest`
Expected: FAIL de compilación (no existe `subtipoMimeDe`).

- [ ] **Step 6: Modificar `ImagenController` (endpoint generar + helper).**

En `ImagenController.java`:

- Asegurar imports: `import java.util.Base64;` (ya está) y `import java.util.Locale;`, y `import ar.com.leo.super_master_backend.dominio.imagen.service.GeneracionCaratula;`.
- Reemplazar el método del endpoint generar por:

```java
    @PostMapping("/caratula/generar/{sku}")
    @PreAuthorize(Permisos.INTEGRACIONES_EDITAR)
    public ResponseEntity<CaratulaGeneradaDTO> generarCaratula(@PathVariable String sku) {
        GeneracionCaratula g = caratulaService.generar(sku);
        String generadaB64 = Base64.getEncoder().encodeToString(g.generada());
        String crudaB64 = Base64.getEncoder().encodeToString(g.cruda());
        return ResponseEntity.ok(new CaratulaGeneradaDTO(
                generadaB64, caratulaService.formato(), crudaB64, subtipoMimeDe(g.crudaNombre())));
    }
```

- Agregar el helper `static` (package-private) en la misma clase:

```java
    /** Subtipo MIME para data:image/{x} derivado de la extensión del archivo crudo (jpg→jpeg). */
    static String subtipoMimeDe(String nombre) {
        int dot = nombre.lastIndexOf('.');
        String ext = dot >= 0 ? nombre.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        return ext.equals("jpg") ? "jpeg" : ext;
    }
```

- [ ] **Step 7: Correr el test del helper y la suite.**

Run: `cd supermaster-backend && mvn -o test -Dtest=ImagenControllerMimeTest`
Expected: PASS.
Run: `mvn -o test`
Expected: BUILD SUCCESS (verificá que ningún test que use `CaratulaService.generar` quede roto por el cambio de tipo de retorno).

- [ ] **Step 8: Commit.**

```bash
git add supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/imagen/service/GeneracionCaratula.java \
  supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/imagen/service/CaratulaService.java \
  supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/imagen/dto/CaratulaGeneradaDTO.java \
  supermaster-backend/src/main/java/ar/com/leo/super_master_backend/dominio/imagen/controller/ImagenController.java \
  supermaster-backend/src/test/java/ar/com/leo/super_master_backend/dominio/imagen/controller/ImagenControllerMimeTest.java
git commit -m "feat(imagen-ia): el endpoint de generar carátula devuelve también la imagen cruda

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Frontend — preview con cruda + generada y 3 botones

**Files:**
- Modify: `supermaster-frontend/src/app/productos/productosService.ts`
- Modify: `supermaster-frontend/src/app/productos/ProductoFormModal.tsx`

**Interfaces:**
- Consumes (de Task 1): `POST /api/imagenes/caratula/generar/{sku}` → `{ imagenBase64, formato, crudaBase64, crudaFormato }`.

- [ ] **Step 1: Tipar el retorno de `generarCaratulaAPI`.**

En `productosService.ts`, reemplazar la firma de `generarCaratulaAPI` por:

```ts
export async function generarCaratulaAPI(sku: string): Promise<{ imagenBase64: string; formato: string; crudaBase64: string; crudaFormato: string }> {
	const r = await fetchAPI(`${API_BASE_URL}/api/imagenes/caratula/generar/${encodeURIComponent(sku)}`, { method: "POST" });
	return r.json();
}
```

(El cuerpo no cambia; solo el tipo de retorno.)

- [ ] **Step 2: Estados de la cruda en `ProductoFormModal`.**

Junto al estado `const [caratulaFormato, setCaratulaFormato] = useState<string>("jpeg");`, agregar:

```ts
    const [caratulaCruda, setCaratulaCruda] = useState<string | null>(null);
    const [caratulaCrudaFormato, setCaratulaCrudaFormato] = useState<string>("jpeg");
```

- [ ] **Step 3: Guardar la cruda al generar y limpiar la cruda al guardar/cancelar.**

En `generarCaratula`, después de `setCaratulaFormato(r.formato);`, agregar:

```ts
            setCaratulaCruda(r.crudaBase64);
            setCaratulaCrudaFormato(r.crudaFormato);
```

En `guardarCaratula`, donde hoy hace `setCaratulaPreview(null);` tras guardar, agregar al lado:

```ts
            setCaratulaCruda(null);
```

Agregar un handler de cancelar (cerca de `guardarCaratula`):

```ts
    const cancelarCaratula = () => {
        setCaratulaPreview(null);
        setCaratulaCruda(null);
    };
```

- [ ] **Step 4: Reescribir el bloque de preview (2 imágenes + 3 botones).**

En el JSX, dentro de `{editandoProductoId && ( ... )}`, reemplazar el bloque actual del botón + preview por:

```tsx
                                {editandoProductoId && (
                                    <div className="mt-2">
                                        {!caratulaPreview && (
                                            <Button variant="dark" onClick={generarCaratula} disabled={generandoCaratula}>
                                                {generandoCaratula ? <SpinnerIcon /> : <SparklesIcon className="h-4 w-4" />}
                                                {generandoCaratula ? "Generando..." : "Mejorar carátula con IA"}
                                            </Button>
                                        )}
                                        {caratulaPreview && (
                                            <div className="mt-3 rounded-2xl border border-slate-200 p-3 dark:border-slate-700">
                                                <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-center">
                                                    <figure className="flex-1">
                                                        <figcaption className="mb-1 text-center text-xs text-slate-500">Original</figcaption>
                                                        {caratulaCruda && <img src={`data:image/${caratulaCrudaFormato};base64,${caratulaCruda}`} alt="Imagen original" className="mx-auto max-h-64" />}
                                                    </figure>
                                                    <figure className="flex-1">
                                                        <figcaption className="mb-1 text-center text-xs text-slate-500">Generada con IA</figcaption>
                                                        <img src={`data:image/${caratulaFormato};base64,${caratulaPreview}`} alt="Carátula generada" className="mx-auto max-h-64" />
                                                    </figure>
                                                </div>
                                                <div className="mt-2 flex justify-end gap-2">
                                                    <Button variant="light" onClick={cancelarCaratula} disabled={guardandoCaratula || generandoCaratula}>Cancelar</Button>
                                                    <Button variant="light" onClick={generarCaratula} disabled={generandoCaratula || guardandoCaratula}>
                                                        {generandoCaratula ? <SpinnerIcon /> : <SparklesIcon className="h-4 w-4" />}
                                                        {generandoCaratula ? "Generando..." : "Volver a generar"}
                                                    </Button>
                                                    <Button variant="dark" onClick={guardarCaratula} disabled={guardandoCaratula || generandoCaratula}>
                                                        {guardandoCaratula ? <SpinnerIcon /> : <CheckIcon className="h-4 w-4" />}
                                                        {guardandoCaratula ? "Guardando..." : "Aceptar"}
                                                    </Button>
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                )}
```

(Usá los nombres reales del archivo: `SpinnerIcon`, `SparklesIcon`, `CheckIcon`, `Button` ya están importados; `generandoCaratula`, `guardandoCaratula`, `caratulaPreview`, `caratulaFormato` ya existen.)

- [ ] **Step 5: Verificar tsc/lint.**

Run: `cd supermaster-frontend && npx tsc --noEmit -p tsconfig.json`
Expected: exit 0.
Run: `npm run lint`
Expected: sin errores `error` nuevos en los archivos tocados.

- [ ] **Step 6: Commit.**

```bash
git add supermaster-frontend/src/app/productos/productosService.ts supermaster-frontend/src/app/productos/ProductoFormModal.tsx
git commit -m "feat(modal): preview de carátula muestra original vs generada con aceptar/regenerar/cancelar

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Cierre (tras las 2 tareas)

- Backend `mvn -o test` verde; frontend `tsc --noEmit` 0.
- Review final de la rama.

## Notas de auto-revisión (cobertura del spec)

- Devolver la cruda en la respuesta de generar (opción A): Task 1 (record + DTO + controller).
- `crudaFormato` derivado de la extensión (jpg→jpeg): Task 1 `subtipoMimeDe` + test.
- Dos imágenes lado a lado (Original | Generada) responsive: Task 2 Step 4.
- Tres botones Aceptar/Volver a generar/Cancelar con disabled coherentes: Task 2 Step 4.
- Cancelar limpia cruda+preview; guardar limpia ambos: Task 2 Step 3.
- Sin cambios en guardar/config/migración: respetado (solo se tocan generar + UI).
