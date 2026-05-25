/**
 * Extrae un mensaje legible de un error desconocido (capturado en un `catch`).
 * Centralizado para reemplazar el patrón repetido `catch (e: unknown) { getErrorMessage(e) }`
 * por algo type-safe que funciona con `catch (e: unknown)`.
 *
 * Cubre los casos comunes:
 * - `Error` real (incluido `AxiosError`): usa `.message`.
 * - Objeto plano con `message: string`: lo usa (algunos handlers custom).
 * - String tirado como excepción: lo usa tal cual.
 * - Cualquier otra cosa: devuelve el fallback.
 */
export function getErrorMessage(err: unknown, fallback: string = "Error"): string {
  if (err instanceof Error) {
    return err.message || fallback;
  }
  if (typeof err === "string") {
    return err || fallback;
  }
  if (err && typeof err === "object" && "message" in err) {
    const msg = (err as { message: unknown }).message;
    if (typeof msg === "string" && msg.length > 0) {
      return msg;
    }
  }
  return fallback;
}
