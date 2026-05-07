"use client";
import Link from "next/link";
import { useAuth } from "./context/AuthContext";
import { ClockIcon, ChevronRightIcon } from "@heroicons/react/24/outline";
import { navigationSections, filterNavigationSections } from "./components/navigation/navigationConfig";
import { getRoleBadgeClasses } from "./utils/roleBadge";

const themeMap = {
    blue: {
        icon: "bg-blue-50 text-blue-600 ring-blue-100 dark:bg-blue-500/12 dark:text-blue-300 dark:ring-blue-400/15",
        accent: "text-blue-600 dark:text-blue-300",
    },
    violet: {
        icon: "bg-violet-50 text-violet-600 ring-violet-100 dark:bg-violet-500/12 dark:text-violet-300 dark:ring-violet-400/15",
        accent: "text-violet-600 dark:text-violet-300",
    },
    cyan: {
        icon: "bg-cyan-50 text-cyan-600 ring-cyan-100 dark:bg-cyan-500/12 dark:text-cyan-300 dark:ring-cyan-400/15",
        accent: "text-cyan-600 dark:text-cyan-300",
    },
    emerald: {
        icon: "bg-emerald-50 text-emerald-600 ring-emerald-100 dark:bg-emerald-500/12 dark:text-emerald-300 dark:ring-emerald-400/15",
        accent: "text-emerald-600 dark:text-emerald-300",
    },
    amber: {
        icon: "bg-amber-50 text-amber-600 ring-amber-100 dark:bg-amber-500/12 dark:text-amber-300 dark:ring-amber-400/15",
        accent: "text-amber-600 dark:text-amber-300",
    },
    orange: {
        icon: "bg-orange-50 text-orange-600 ring-orange-100 dark:bg-orange-500/12 dark:text-orange-300 dark:ring-orange-400/15",
        accent: "text-orange-600 dark:text-orange-300",
    },
    rose: {
        icon: "bg-rose-50 text-rose-600 ring-rose-100 dark:bg-rose-500/12 dark:text-rose-300 dark:ring-rose-400/15",
        accent: "text-rose-600 dark:text-rose-300",
    },
    slate: {
        icon: "bg-slate-100 text-slate-600 ring-slate-200 dark:bg-slate-700/70 dark:text-slate-300 dark:ring-slate-500/25",
        accent: "text-slate-600 dark:text-slate-300",
    },
    indigo: {
        icon: "bg-indigo-50 text-indigo-600 ring-indigo-100 dark:bg-indigo-500/12 dark:text-indigo-300 dark:ring-indigo-400/15",
        accent: "text-indigo-600 dark:text-indigo-300",
    },
    teal: {
        icon: "bg-teal-50 text-teal-600 ring-teal-100 dark:bg-teal-500/12 dark:text-teal-300 dark:ring-teal-400/15",
        accent: "text-teal-600 dark:text-teal-300",
    },
    fuchsia: {
        icon: "bg-fuchsia-50 text-fuchsia-600 ring-fuchsia-100 dark:bg-fuchsia-500/12 dark:text-fuchsia-300 dark:ring-fuchsia-400/15",
        accent: "text-fuchsia-600 dark:text-fuchsia-300",
    },
} as const;

function DashCard({ item }: { item: (typeof navigationSections)[number]["items"][number] }) {
    const Icon = item.icon;
    const theme = themeMap[item.color];

    return (
        <Link
            href={item.href}
            className="group rounded-2xl border border-slate-200/80 bg-white/90 p-4 shadow-sm transition duration-200 hover:-translate-y-0.5 hover:border-slate-300 hover:shadow-md dark:border-slate-700/80 dark:bg-slate-800/90 dark:hover:border-slate-500"
        >
            <div className="flex items-start gap-4">
                <div className={`flex size-11 shrink-0 items-center justify-center rounded-2xl ring-1 transition-colors ${theme.icon}`}>
                    <Icon className="size-5" />
                </div>
                <div className="min-w-0 flex-1">
                    <div className="flex items-start justify-between gap-3">
                        <p className="text-sm font-semibold leading-6 text-slate-900 dark:text-slate-100">{item.label}</p>
                    </div>
                    <p className="mt-1 text-sm leading-6 text-slate-500 dark:text-slate-400">{item.description}</p>
                    <div className={`mt-3 inline-flex items-center gap-1 text-xs font-semibold ${theme.accent}`}>
                        Abrir
                        <ChevronRightIcon className="size-3.5 transition-transform group-hover:translate-x-0.5" />
                    </div>
                </div>
            </div>
        </Link>
    );
}

function SectionBlock({ section }: { section: (typeof navigationSections)[number] }) {
    return (
        <section className="space-y-4">
            <div className="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
                <div>
                    <p className="text-[11px] font-bold uppercase tracking-[0.22em] text-slate-400 dark:text-slate-400">{section.label}</p>
                    <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{section.description}</p>
                </div>
                <p className="text-xs font-medium text-slate-400 dark:text-slate-500">{section.items.length} módulo{section.items.length === 1 ? "" : "s"}</p>
            </div>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 2xl:grid-cols-3">
                {section.items.map((item) => (
                    <DashCard key={item.href} item={item} />
                ))}
            </div>
        </section>
    );
}

export default function Home() {
    const { usuario, hasPermiso } = useAuth();
    const now = new Date();
    const dateStr = now.toLocaleDateString("es-AR", {
        weekday: "long",
        day: "numeric",
        month: "long",
        year: "numeric",
    });
    const hour = now.getHours();
    const greeting = hour < 12 ? "Buenos días" : hour < 19 ? "Buenas tardes" : "Buenas noches";
    const roleLabel = (usuario.rol || "Usuario").toUpperCase();
    const roleBadgeClasses = getRoleBadgeClasses(usuario.rol);
    const visibleSections = filterNavigationSections(navigationSections, {
        hasPermission: hasPermiso,
        currentRole: usuario.rol,
    });
    const homeSections = [
        ...visibleSections.filter((section) => section.label === "Ayuda"),
        ...visibleSections.filter((section) => section.label !== "Ayuda"),
    ];

    return (
        <main className="min-h-full bg-[radial-gradient(circle_at_top_left,_rgba(59,130,246,0.08),_transparent_28%),radial-gradient(circle_at_top_right,_rgba(16,185,129,0.08),_transparent_24%),linear-gradient(180deg,_#f8fbff_0%,_#f6f8fc_44%,_#ffffff_100%)] px-3 py-4 md:px-5 md:py-6 xl:px-6 xl:py-8 2xl:px-8 dark:bg-[radial-gradient(circle_at_top_left,_rgba(59,130,246,0.14),_transparent_28%),radial-gradient(circle_at_top_right,_rgba(16,185,129,0.12),_transparent_24%),linear-gradient(180deg,_#0f172a_0%,_#0b1220_44%,_#0f172a_100%)]">
            <div className="mx-auto flex w-full max-w-[1680px] flex-col gap-8 pb-8 md:pb-12 xl:pb-16">
                <section className="space-y-6">
                    <div className="relative w-full overflow-hidden rounded-[28px] border border-white/70 bg-white p-6 shadow-[0_24px_64px_-36px_rgba(15,23,42,0.45)] ring-1 ring-slate-200/70 md:p-7 dark:border-slate-700/80 dark:bg-slate-900 dark:ring-slate-700/80">
                        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_right,_rgba(37,99,235,0.10),_transparent_26%),radial-gradient(circle_at_bottom_left,_rgba(16,185,129,0.10),_transparent_24%)] dark:bg-[radial-gradient(circle_at_top_right,_rgba(59,130,246,0.16),_transparent_28%),radial-gradient(circle_at_bottom_left,_rgba(16,185,129,0.14),_transparent_24%)]" />
                        <div className="relative flex flex-col gap-3">
                            <div className="inline-flex max-w-full flex-wrap items-center gap-3 self-start rounded-2xl border border-slate-200/80 bg-white/75 px-4 py-3 shadow-sm dark:border-slate-700/80 dark:bg-slate-900/80">
                                <span className="relative inline-flex text-xl font-extrabold tracking-tight">
                                    <span className="text-slate-900 dark:text-slate-100">Super</span>
                                    <span className="text-blue-600 dark:text-blue-400">Master</span>
                                    <span className="header-shimmer-text" aria-hidden="true">
                                        <span className="font-extrabold">Super</span>
                                        <span className="font-extrabold">Master</span>
                                    </span>
                                </span>
                                <span className="h-5 w-px bg-slate-200 dark:bg-slate-700" />
                                <img src="/logos/linea-ge.webp" alt="Linea GE" className="h-8 w-8 rounded object-contain" />
                                <img src="/logos/kt-logo.webp" alt="Kitchen Tools" className="h-8 object-contain" />
                            </div>

                            <div className="flex flex-wrap items-center gap-2 text-[11px] font-bold uppercase tracking-[0.22em] text-slate-400 dark:text-slate-500">
                                <span>{dateStr}</span>
                                <span className="inline-flex items-center gap-1 rounded-full border border-slate-200/70 bg-white/80 px-2.5 py-1 text-[10px] text-slate-500 dark:border-slate-700/80 dark:bg-slate-900/80 dark:text-slate-300">
                                    <ClockIcon className="size-3.5" />
                                    Inicio
                                </span>
                            </div>

                            <div className="max-w-3xl">
                                <div className="flex flex-wrap items-center gap-3">
                                    <h1 className="text-3xl font-bold tracking-tight text-slate-900 md:text-4xl dark:text-slate-100">
                                        {greeting}, {usuario.nombreCompleto}
                                    </h1>
                                    <span className={`inline-flex items-center self-center rounded-full border px-3 py-1 text-xs font-bold uppercase tracking-[0.16em] ${roleBadgeClasses}`}>
                                        {roleLabel}
                                    </span>
                                </div>
                                <p className="mt-3 max-w-2xl text-sm leading-7 text-slate-500 md:text-[15px] dark:text-slate-400">
                                    Elegí una sección para empezar a trabajar.
                                </p>
                            </div>
                        </div>
                    </div>
                </section>

                <div className="grid gap-8">
                    {homeSections.map((section) => (
                        <SectionBlock key={section.label} section={section} />
                    ))}
                </div>
            </div>
        </main>
    );
}
