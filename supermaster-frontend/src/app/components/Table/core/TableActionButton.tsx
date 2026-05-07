"use client";

import type { ButtonHTMLAttributes, ReactNode } from "react";

type TableActionTone =
    | "neutral"
    | "primary"
    | "accent"
    | "success"
    | "warning"
    | "danger";

const TONE_CLASS_MAP: Record<TableActionTone, string> = {
    neutral: "border-slate-200 bg-slate-50 text-slate-700 hover:border-slate-300 hover:bg-slate-100 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200 dark:hover:bg-slate-700",
    primary: "border-blue-200 bg-blue-50 text-blue-700 hover:border-blue-300 hover:bg-blue-100 dark:border-blue-800 dark:bg-blue-500/15 dark:text-blue-200 dark:hover:bg-blue-500/25",
    accent: "border-purple-200 bg-purple-50 text-purple-700 hover:border-purple-300 hover:bg-purple-100 dark:border-purple-800 dark:bg-purple-500/15 dark:text-purple-200 dark:hover:bg-purple-500/25",
    success: "border-emerald-200 bg-emerald-50 text-emerald-700 hover:border-emerald-300 hover:bg-emerald-100 dark:border-emerald-800 dark:bg-emerald-500/15 dark:text-emerald-200 dark:hover:bg-emerald-500/25",
    warning: "border-amber-200 bg-amber-50 text-amber-700 hover:border-amber-300 hover:bg-amber-100 dark:border-amber-800 dark:bg-amber-500/15 dark:text-amber-200 dark:hover:bg-amber-500/25",
    danger: "border-red-200 bg-red-50 text-red-700 hover:border-red-300 hover:bg-red-100 dark:border-red-800 dark:bg-red-500/15 dark:text-red-200 dark:hover:bg-red-500/25",
};

export function getTableActionButtonClasses(tone: TableActionTone = "neutral", extraClassName = "") {
    return [
        "inline-flex items-center justify-center gap-1.5 rounded border px-2.5 py-1 text-xs font-medium whitespace-nowrap transition-colors",
        "disabled:cursor-not-allowed disabled:opacity-50",
        TONE_CLASS_MAP[tone],
        extraClassName,
    ].join(" ").trim();
}

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
    icon?: ReactNode;
    tone?: TableActionTone;
    children?: ReactNode;
};

export default function TableActionButton({
    icon,
    tone = "neutral",
    children,
    className = "",
    type = "button",
    ...props
}: Props) {
    return (
        <button
            type={type}
            className={getTableActionButtonClasses(tone, className)}
            {...props}
        >
            {icon}
            {children}
        </button>
    );
}
