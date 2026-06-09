import { ReactNode, ComponentType, SVGProps } from "react";
import { PlusIcon } from "@heroicons/react/24/outline";

type Props = {
    onClick?: () => void | Promise<void>;
    disabled?: boolean;
    children: ReactNode;
    /** Ícono dentro del círculo. Por defecto un "+". */
    icon?: ComponentType<SVGProps<SVGSVGElement>>;
    title?: string;
};

/**
 * Botón de acción primario (crear / nuevo) usado en las tablas: gradiente azul,
 * sombra de color, leve elevación al hover y el ícono en un círculo que rota.
 */
export default function CreateButton({ onClick, disabled = false, children, icon: Icon = PlusIcon, title }: Props) {
    return (
        <button
            type="button"
            onClick={onClick}
            disabled={disabled}
            title={title}
            className="group inline-flex items-center gap-2 rounded-xl bg-gradient-to-r from-blue-600 to-blue-500 px-4 py-2.5 text-sm font-semibold text-white shadow-lg shadow-blue-500/30 transition-all duration-200 hover:-translate-y-0.5 hover:from-blue-500 hover:to-blue-400 hover:shadow-xl hover:shadow-blue-500/40 active:translate-y-0 disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:translate-y-0 disabled:hover:shadow-lg"
        >
            <span className="flex h-5 w-5 items-center justify-center rounded-full bg-white/20 transition-transform duration-200 group-hover:rotate-90">
                <Icon className="h-3.5 w-3.5" />
            </span>
            {children}
        </button>
    );
}
