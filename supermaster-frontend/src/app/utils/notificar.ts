import { toast } from "sonner";

export type NotificacionTipo = "success" | "error" | "info" | "warning";

export interface NotificacionEvent {
    tipo: NotificacionTipo;
    mensaje: string;
}

const EVENT_NAME = "sm-notificacion";

function emitir(tipo: NotificacionTipo, mensaje: string) {
    if (typeof window !== "undefined") {
        window.dispatchEvent(new CustomEvent<NotificacionEvent>(EVENT_NAME, { detail: { tipo, mensaje } }));
    }
}

export const notificar = {
    success(mensaje: string) {
        toast.success(mensaje);
        emitir("success", mensaje);
    },
    error(mensaje: string) {
        toast.error(mensaje);
        emitir("error", mensaje);
    },
    info(mensaje: string) {
        toast.info(mensaje);
        emitir("info", mensaje);
    },
    warning(mensaje: string) {
        toast.warning(mensaje);
        emitir("warning", mensaje);
    },
};

export { EVENT_NAME };
