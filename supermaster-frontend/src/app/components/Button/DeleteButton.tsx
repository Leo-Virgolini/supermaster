import { ReactNode, ComponentType, SVGProps } from "react";
import { TrashIcon } from "@heroicons/react/24/outline";

type Props = {
    onClick?: () => void | Promise<void>;
    disabled?: boolean;
    children: ReactNode;
    /** Ícono dentro del círculo. Por defecto un tacho. */
    icon?: ComponentType<SVGProps<SVGSVGElement>>;
    title?: string;
};

/**
 * Botón de acción destructiva (borrar / eliminar) usado en las tablas: gradiente
 * rojo, sombra de color, leve elevación al hover y el ícono en un círculo.
 */
export default function DeleteButton({ onClick, disabled = false, children, icon: Icon = TrashIcon, title }: Props) {
    return (
        <button
            type="button"
            onClick={onClick}
            disabled={disabled}
            title={title}
            className="group inline-flex items-center gap-2 rounded-xl bg-gradient-to-r from-red-600 to-red-500 px-4 py-2.5 text-sm font-semibold text-white shadow-lg shadow-red-500/30 transition-all duration-200 hover:-translate-y-0.5 hover:from-red-500 hover:to-red-400 hover:shadow-xl hover:shadow-red-500/40 active:translate-y-0 disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:translate-y-0 disabled:hover:shadow-lg"
        >
            <span className="flex h-5 w-5 items-center justify-center rounded-full bg-white/20 transition-transform duration-200 group-hover:-rotate-12">
                <Icon className="h-3.5 w-3.5" />
            </span>
            {children}
        </button>
    );
}
