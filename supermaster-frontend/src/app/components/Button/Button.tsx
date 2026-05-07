import Link from "next/link";
import { ReactNode } from "react";

type Props = {
    text?: string;
    url?: string;
    full?: boolean;
    variant?: "light" | "dark" | "danger" | "warning" | "outline";
    active?: boolean;
    children?: ReactNode;
    onClick?: () => void | Promise<void>;
    disabled?: boolean;
};

const Button = ({
    text = "",
    url,
    full = false,
    variant = "light",
    active = false,
    children,
    onClick,
    disabled = false,
}: Props) => {
    let variantClasses = "";
    if (active && url) {
        variantClasses = "bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 border-l-2 border-blue-500 [&_svg]:text-blue-600 dark:[&_svg]:text-blue-400";
    } else {
        switch (variant) {
            case "light":
                variantClasses = "bg-white dark:bg-slate-700 hover:bg-gray-100 dark:hover:bg-slate-600 text-gray-700 dark:text-slate-200 border border-gray-300 dark:border-slate-600";
                break;
            case "dark":
                variantClasses = "bg-primary hover:bg-primary-light text-gray-100";
                break;
            case "danger":
                variantClasses = "bg-red-50 hover:bg-red-100 text-red-600 border border-red-200 dark:bg-red-900/20 dark:hover:bg-red-900/40 dark:text-red-400 dark:border-red-800";
                break;
            case "warning":
                variantClasses = "bg-amber-500 hover:bg-amber-600 text-white";
                break;
            case "outline":
                variantClasses = "bg-transparent hover:bg-blue-50 dark:hover:bg-blue-900/20 text-blue-600 dark:text-blue-400 border border-blue-300 dark:border-blue-700";
                break;
        }
    }

    const className = `${variantClasses} ${full ? "w-full" : ""} ${disabled ? "opacity-50 cursor-not-allowed" : "cursor-pointer"}
    text-sm tracking-tighter inline-flex items-center gap-2 p-2 rounded transition duration-200 ease-in-out`;

    //* NO TIENE URL Y NO DEBE EJECUTAR UNA FUNCION
    return url && !onClick ?
        (
            <Link href={url} className={className}>
                {children}
                {text}
            </Link>
        )
        :
        //* TIENE URL Y DEBE EJECUTAR UNA FUNCION
        (
            <button onClick={onClick} className={className} disabled={disabled}>
                {children}
                {text}
            </button>
        );
};

export default Button;
