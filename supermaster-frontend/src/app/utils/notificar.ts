import { toast } from "sonner";

export type NotificacionTipo = "success" | "error" | "info" | "warning";

export interface NotificacionEvent {
    tipo: NotificacionTipo;
    mensaje: string;
    /**
     * Texto largo opcional (ej: lista de SKUs problemáticos tras un recálculo masivo).
     * No se muestra en el toast (sería ruidoso); se persiste en la notificación del
     * panel y se puede copiar al portapapeles desde ahí.
     */
    detalle?: string | null;
}

const EVENT_NAME = "sm-notificacion";

// Hace que sonner respete los saltos de línea ("\n") del mensaje. Inofensivo
// para mensajes de una sola línea.
const TOAST_OPTS = { style: { whiteSpace: "pre-line" as const } };

function emitir(tipo: NotificacionTipo, mensaje: string, detalle?: string | null) {
    if (typeof window !== "undefined") {
        window.dispatchEvent(new CustomEvent<NotificacionEvent>(EVENT_NAME, { detail: { tipo, mensaje, detalle } }));
    }
}

export const notificar = {
    success(mensaje: string, detalle?: string | null) {
        toast.success(mensaje, TOAST_OPTS);
        emitir("success", mensaje, detalle);
    },
    error(mensaje: string, detalle?: string | null) {
        toast.error(mensaje, TOAST_OPTS);
        emitir("error", mensaje, detalle);
    },
    info(mensaje: string, detalle?: string | null) {
        toast.info(mensaje, TOAST_OPTS);
        emitir("info", mensaje, detalle);
    },
    warning(mensaje: string, detalle?: string | null) {
        toast.warning(mensaje, TOAST_OPTS);
        emitir("warning", mensaje, detalle);
    },
    /**
     * Solo persiste al panel de notificaciones — no emite toast.
     * Útil cuando ya se mostró un toast custom (ej: con botón de acción) y no
     * queremos duplicar la información, pero sí queremos que quede registro.
     */
    persistir(tipo: NotificacionTipo, mensaje: string, detalle?: string | null) {
        emitir(tipo, mensaje, detalle);
    },
};

export { EVENT_NAME };
