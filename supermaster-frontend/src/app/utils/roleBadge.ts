"use client";

export function getRoleBadgeClasses(role?: string | null) {
    const normalized = (role || "").trim().toLowerCase();

    if (normalized.includes("admin")) {
        return "border-blue-200 bg-blue-50 text-blue-700 dark:border-blue-800 dark:bg-blue-500/15 dark:text-blue-200";
    }

    if (normalized.includes("oper")) {
        return "border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-800 dark:bg-emerald-500/15 dark:text-emerald-200";
    }

    if (normalized.includes("visor") || normalized.includes("consulta") || normalized.includes("viewer") || normalized.includes("lectura")) {
        return "border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-800 dark:bg-amber-500/15 dark:text-amber-200";
    }

    if (normalized.includes("super") || normalized.includes("manager")) {
        return "border-violet-200 bg-violet-50 text-violet-700 dark:border-violet-800 dark:bg-violet-500/15 dark:text-violet-200";
    }

    return "border-slate-200 bg-slate-50 text-slate-700 dark:border-slate-700 dark:bg-slate-700/70 dark:text-slate-200";
}
