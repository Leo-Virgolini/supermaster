"use client";
import { usePathname } from "next/navigation";
import Link from "next/link";
import { HomeIcon, StarIcon as StarOutlineIcon, ChevronRightIcon } from "@heroicons/react/24/outline";
import { StarIcon as StarSolidIcon } from "@heroicons/react/24/solid";
import { ReactNode, useState } from "react";
import { useSidebar } from "../../context/SidebarContext";
import { useAuth } from "../../context/AuthContext";
import { navigationSections, sectionLabelColorMap, filterNavigationSections } from "../navigation/navigationConfig";
import type { NavColor } from "../navigation/navigationConfig";

const FAVORITES_STORAGE_KEY = "sidebar_favorites_v1";

type NavItemProps = {
    href: string;
    label: string;
    isActive: boolean;
    color?: NavColor;
    isFavorite?: boolean;
    onToggleFavorite?: () => void;
    external?: boolean;
    children: ReactNode;
};

const iconToneMap: Record<NavColor, string> = {
    blue: "text-blue-500 group-hover:text-blue-600 dark:text-blue-300 dark:group-hover:text-blue-200",
    violet: "text-violet-500 group-hover:text-violet-600 dark:text-violet-300 dark:group-hover:text-violet-200",
    cyan: "text-cyan-500 group-hover:text-cyan-600 dark:text-cyan-300 dark:group-hover:text-cyan-200",
    emerald: "text-emerald-500 group-hover:text-emerald-600 dark:text-emerald-300 dark:group-hover:text-emerald-200",
    amber: "text-amber-500 group-hover:text-amber-600 dark:text-amber-300 dark:group-hover:text-amber-200",
    orange: "text-orange-500 group-hover:text-orange-600 dark:text-orange-300 dark:group-hover:text-orange-200",
    rose: "text-rose-500 group-hover:text-rose-600 dark:text-rose-300 dark:group-hover:text-rose-200",
    slate: "text-slate-400 group-hover:text-slate-600 dark:text-slate-400 dark:group-hover:text-slate-200",
    indigo: "text-indigo-500 group-hover:text-indigo-600 dark:text-indigo-300 dark:group-hover:text-indigo-200",
    teal: "text-teal-500 group-hover:text-teal-600 dark:text-teal-300 dark:group-hover:text-teal-200",
    fuchsia: "text-fuchsia-500 group-hover:text-fuchsia-600 dark:text-fuchsia-300 dark:group-hover:text-fuchsia-200",
};

const iconBgToneMap: Record<NavColor, string> = {
    blue: "group-hover:bg-blue-50 dark:group-hover:bg-blue-500/12",
    violet: "group-hover:bg-violet-50 dark:group-hover:bg-violet-500/12",
    cyan: "group-hover:bg-cyan-50 dark:group-hover:bg-cyan-500/12",
    emerald: "group-hover:bg-emerald-50 dark:group-hover:bg-emerald-500/12",
    amber: "group-hover:bg-amber-50 dark:group-hover:bg-amber-500/12",
    orange: "group-hover:bg-orange-50 dark:group-hover:bg-orange-500/12",
    rose: "group-hover:bg-rose-50 dark:group-hover:bg-rose-500/12",
    slate: "group-hover:bg-white dark:group-hover:bg-slate-700/90",
    indigo: "group-hover:bg-indigo-50 dark:group-hover:bg-indigo-500/12",
    teal: "group-hover:bg-teal-50 dark:group-hover:bg-teal-500/12",
    fuchsia: "group-hover:bg-fuchsia-50 dark:group-hover:bg-fuchsia-500/12",
};

const NavItem = ({ href, label, isActive, color = "slate", isFavorite = false, onToggleFavorite, external = false, children }: NavItemProps) => {
    const FavoriteIcon = isFavorite ? StarSolidIcon : StarOutlineIcon;

    const linkClassName = `relative flex items-center gap-2 px-2 py-1.5 rounded-xl border text-sm font-medium transition-all duration-200 ${
        isActive
            ? "border-blue-500/80 bg-blue-600 text-white shadow-[0_10px_24px_-14px_rgba(37,99,235,0.9)] dark:border-blue-400/70 dark:bg-blue-500 dark:shadow-[0_12px_28px_-16px_rgba(59,130,246,0.7)]"
            : "border-transparent text-gray-700 dark:text-slate-200 hover:-translate-y-[1px] hover:border-blue-100 hover:bg-gradient-to-r hover:from-blue-50 hover:to-white hover:text-slate-900 hover:shadow-[0_10px_24px_-20px_rgba(37,99,235,0.45)] dark:hover:border-slate-600 dark:hover:from-slate-800 dark:hover:to-slate-800/90 dark:hover:text-white dark:hover:shadow-[0_12px_28px_-20px_rgba(15,23,42,0.8)]"
    }`;

    const LinkOrAnchor = external
        ? ({ children: linkChildren }: { children: ReactNode }) => (
            <a
                href={href}
                target="_blank"
                rel="noopener noreferrer"
                className={linkClassName}
            >
                {linkChildren}
            </a>
        )
        : ({ children: linkChildren }: { children: ReactNode }) => (
            <Link href={href} className={linkClassName}>
                {linkChildren}
            </Link>
        );

    return (
        <div className="group">
            <LinkOrAnchor>
                <span className={`flex size-7 shrink-0 items-center justify-center rounded-md transition-colors ${
                    isActive
                        ? "bg-white/15 text-white"
                        : `${iconToneMap[color]} ${iconBgToneMap[color]}`
                }`}>
                    {children}
                </span>
                <span className="min-w-0 flex-1 leading-5 break-words">{label}</span>
                {onToggleFavorite ? (
                    <button
                        type="button"
                        onClick={(e) => {
                            e.preventDefault();
                            e.stopPropagation();
                            onToggleFavorite();
                        }}
                        className={`flex size-6 shrink-0 items-center justify-center rounded-md transition ${
                            isFavorite
                                ? "text-amber-500 hover:bg-amber-50 dark:text-amber-300 dark:hover:bg-amber-500/15"
                                : "text-gray-300 hover:text-amber-500 hover:bg-amber-50 dark:text-slate-600 dark:hover:bg-amber-500/15 dark:hover:text-amber-300 opacity-0 group-hover:opacity-100"
                        }`}
                        title={isFavorite ? "Quitar de favoritos" : "Agregar a favoritos"}
                        aria-label={isFavorite ? "Quitar de favoritos" : "Agregar a favoritos"}
                    >
                        <FavoriteIcon className="size-4" />
                    </button>
                ) : null}
            </LinkOrAnchor>
        </div>
    );
};

type SectionLabelProps = {
    label: string;
    color: keyof typeof sectionLabelColorMap;
};

const SectionLabel = ({ label, color }: SectionLabelProps) => {
    const tones = sectionLabelColorMap[color];
    return (
        <div className={`px-1 text-[10px] font-bold uppercase tracking-[0.18em] whitespace-nowrap ${tones.light} ${tones.dark}`}>
            {label}
        </div>
    );
};

const Separator = () => <div className="mx-1 my-2 border-t border-gray-200 dark:border-slate-700/70" />;

const Sidebar = () => {
    const pathname = usePathname();
    const { isOpen } = useSidebar();
    const { hasPermiso, usuario } = useAuth();
    const [favoriteHrefs, setFavoriteHrefs] = useState<string[]>(() => {
        if (typeof window === "undefined") return [];
        try {
            const saved = window.localStorage.getItem(FAVORITES_STORAGE_KEY);
            if (!saved) return [];
            const parsed = JSON.parse(saved);
            return Array.isArray(parsed)
                ? parsed.filter((value): value is string => typeof value === "string")
                : [];
        } catch {
            return [];
        }
    });

    const [expandedItems, setExpandedItems] = useState<Set<string>>(new Set());

    const toggleExpanded = (href: string) => {
        setExpandedItems((prev) => {
            const next = new Set(prev);
            if (next.has(href)) next.delete(href); else next.add(href);
            return next;
        });
    };

    const isActive = (url: string) => {
        if (url === "/") return pathname === "/";
        return pathname === url || pathname.startsWith(url + "/");
    };

    const visibleSections = filterNavigationSections(navigationSections, {
        hasPermission: hasPermiso,
        currentRole: usuario.rol,
    });
    const allItems = visibleSections.flatMap((section) =>
        section.items.flatMap((item) => [item, ...(item.children ?? [])])
    );
    const favoriteItems = favoriteHrefs
        .map((href) => allItems.find((item) => item.href === href))
        .filter((item): item is (typeof navigationSections)[number]["items"][number] => Boolean(item));

    const toggleFavorite = (href: string) => {
        setFavoriteHrefs((prev) => {
            const next = prev.includes(href) ? prev.filter((item) => item !== href) : [...prev, href];
            if (typeof window !== "undefined") {
                window.localStorage.setItem(FAVORITES_STORAGE_KEY, JSON.stringify(next));
            }
            return next;
        });
    };

    return (
        <aside className={`relative bg-white dark:bg-slate-900 col-span-1 flex flex-col gap-0 border-r border-gray-200 dark:border-slate-700/70 transition-all duration-200 ${isOpen ? "p-1.5 opacity-100" : "p-0 opacity-0 pointer-events-none"}`}>
            <NavItem href="/" label="Inicio" isActive={isActive("/")} color="blue">
                <HomeIcon className="size-4" />
            </NavItem>

            <div className="mt-2 flex-1 min-h-0 overflow-y-auto overflow-x-hidden flex flex-col gap-1.5 pb-3 pr-1">
                {favoriteItems.length > 0 ? (
                    <div className="rounded-xl border border-amber-100 bg-amber-50/60 px-1 py-1.5 dark:border-amber-500/25 dark:bg-amber-500/10">
                        <SectionLabel label="Favoritos" color="amber" />
                        <div className="mt-0.5 flex flex-col gap-0.5">
                            {favoriteItems.map((item) => {
                                const Icon = item.icon;
                                return (
                                    <NavItem
                                        key={`favorite-${item.href}`}
                                        href={item.href}
                                        label={item.label}
                                        isActive={isActive(item.href)}
                                        color={item.color}
                                        isFavorite
                                        external={item.external}
                                        onToggleFavorite={() => toggleFavorite(item.href)}
                                    >
                                        <Icon className="size-4" />
                                    </NavItem>
                                );
                            })}
                        </div>
                    </div>
                ) : null}

                {visibleSections.map((section, index) => (
                    <div key={section.label} className="rounded-2xl border border-transparent px-1 py-1.5 transition-colors hover:border-slate-200/80 hover:bg-white/65 dark:hover:border-slate-700/70 dark:hover:bg-slate-800/60">
                        {index > 0 && section.label === "Configuración" ? <Separator /> : null}
                        <SectionLabel label={section.label} color={section.color} />
                        <div className="mt-0.5 flex flex-col gap-0.5">
                            {section.items.map((item) => {
                                const Icon = item.icon;
                                const hasChildren = item.children && item.children.length > 0;
                                const isChildActive = hasChildren && item.children!.some((c) => isActive(c.href));
                                const isExpanded = expandedItems.has(item.href);
                                const showChildren = isExpanded || isChildActive;

                                return (
                                    <div key={item.href}>
                                        {hasChildren ? (
                                            <div
                                                onMouseEnter={() => setExpandedItems((prev) => new Set(prev).add(item.href))}
                                                onMouseLeave={() => { if (!isChildActive) setExpandedItems((prev) => { const next = new Set(prev); next.delete(item.href); return next; }); }}
                                            >
                                                <div className="flex items-center">
                                                    <div className="flex-1">
                                                        <NavItem
                                                            href={item.href}
                                                            label={item.label}
                                                            isActive={isActive(item.href)}
                                                            color={item.color}
                                                            isFavorite={favoriteHrefs.includes(item.href)}
                                                            external={item.external}
                                                            onToggleFavorite={() => toggleFavorite(item.href)}
                                                        >
                                                            <Icon className="size-4" />
                                                        </NavItem>
                                                    </div>
                                                    <ChevronRightIcon className={`size-3.5 mr-1 text-gray-400 dark:text-slate-500 transition-transform ${showChildren ? "rotate-90" : ""}`} />
                                                </div>
                                                {showChildren && (
                                                    <div className="ml-5 mt-0.5 flex flex-col gap-0.5 border-l-2 border-gray-200 dark:border-slate-700 pl-2">
                                                        {item.children!.map((child) => {
                                                            const ChildIcon = child.icon;
                                                            return (
                                                                <NavItem
                                                                    key={child.href}
                                                                    href={child.href}
                                                                    label={child.label}
                                                                    isActive={isActive(child.href)}
                                                                    color={child.color}
                                                                    isFavorite={favoriteHrefs.includes(child.href)}
                                                                    external={child.external}
                                                                    onToggleFavorite={() => toggleFavorite(child.href)}
                                                                >
                                                                    <ChildIcon className="size-4" />
                                                                </NavItem>
                                                            );
                                                        })}
                                                    </div>
                                                )}
                                            </div>
                                        ) : (
                                            <NavItem
                                                href={item.href}
                                                label={item.label}
                                                isActive={isActive(item.href)}
                                                color={item.color}
                                                isFavorite={favoriteHrefs.includes(item.href)}
                                                external={item.external}
                                                onToggleFavorite={() => toggleFavorite(item.href)}
                                            >
                                                <Icon className="size-4" />
                                            </NavItem>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    </div>
                ))}
            </div>
        </aside>
    );
};

export default Sidebar;
