"use client";
import { useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { ArrowTopRightOnSquareIcon, HomeIcon } from "@heroicons/react/24/outline";
import { ReactNode } from "react";
import { useAuth } from "../../context/AuthContext";
import {
    filterNavigationSections,
    navigationSections,
    sectionLabelColorMap,
    type NavColor,
} from "../navigation/navigationConfig";

const FAVORITES_STORAGE_KEY = "sidebar_favorites_v1";

type MegaMenuProps = {
	isOpen: boolean;
	onClose: () => void;
	toggleRef?: React.RefObject<HTMLButtonElement | null>;
};

type MenuItemProps = {
	href: string;
	label: string;
	icon: ReactNode;
	isActive: boolean;
	description?: string;
	external?: boolean;
	onClick: () => void;
};

const MenuItem = ({ href, label, icon, isActive, description, external = false, onClick }: MenuItemProps) => {
	const className = `group flex items-start gap-3 rounded-xl border px-3 py-2.5 text-sm transition-all ${
		isActive
			? "border-blue-200 bg-blue-600 text-white shadow-sm dark:border-blue-400/40 dark:bg-blue-500"
			: "border-transparent text-gray-600 hover:border-gray-200 hover:bg-gray-50 hover:text-gray-900 dark:text-slate-200 dark:hover:border-slate-600 dark:hover:bg-slate-700/80 dark:hover:text-white"
	}`;

	const content = (
		<>
			<span className={`mt-0.5 shrink-0 ${isActive ? "text-white" : "text-gray-400 dark:text-slate-500"}`}>
				{icon}
			</span>
			<span className="min-w-0 flex-1">
				<span className="flex items-center gap-2">
					<span className="truncate font-medium">{label}</span>
					{(isActive || external) ? <ArrowTopRightOnSquareIcon className={`size-3.5 shrink-0 ${isActive ? "text-white/85" : "text-gray-400 dark:text-slate-500"}`} /> : null}
				</span>
				{description ? (
					<span className={`mt-0.5 block truncate text-[11px] ${isActive ? "text-blue-100/90" : "text-gray-400 dark:text-slate-500"}`}>
						{description}
					</span>
				) : null}
			</span>
		</>
	);

	if (external) {
		return (
			<a href={href} target="_blank" rel="noopener noreferrer" onClick={onClick} className={className}>
				{content}
			</a>
		);
	}
	return (
		<Link href={href} onClick={onClick} className={className}>
			{content}
		</Link>
	);
};

const SectionTitle = ({ label, color, description }: { label: string; color: NavColor; description?: string }) => {
	const tones = sectionLabelColorMap[color];
	return (
		<div className="mb-3 px-1">
			<div className={`text-[10px] font-bold uppercase tracking-[0.22em] ${tones.light} ${tones.dark}`}>
				{label}
			</div>
			{description ? (
				<p className="mt-1 text-xs leading-5 text-gray-400 dark:text-slate-500">
					{description}
				</p>
			) : null}
		</div>
	);
};

const MegaMenu = ({ isOpen, onClose, toggleRef }: MegaMenuProps) => {
	const pathname = usePathname();
	const menuRef = useRef<HTMLDivElement>(null);
	const { hasPermiso, usuario } = useAuth();

	const isActive = (url: string) => {
		if (url === "/") return pathname === "/";
		return pathname === url || pathname.startsWith(url + "/");
	};

	const visibleSections = useMemo(
		() => filterNavigationSections(navigationSections, {
			hasPermission: hasPermiso,
			currentRole: usuario.rol,
		}),
		[hasPermiso, usuario.rol],
	);
	const visibleItems = useMemo(
		() => visibleSections.flatMap((section) => section.items),
		[visibleSections],
	);
	const [favoriteHrefs] = useState<string[]>(() => {
		if (typeof window === "undefined") return [];
		try {
			const savedFavorites = window.localStorage.getItem(FAVORITES_STORAGE_KEY);
			if (!savedFavorites) return [];
			const parsed = JSON.parse(savedFavorites);
			return Array.isArray(parsed) ? parsed.filter((value): value is string => typeof value === "string") : [];
		} catch {
			return [];
		}
	});

	useEffect(() => {
		onClose();
	}, [pathname, onClose]);

	const favoriteItems = useMemo(
		() => favoriteHrefs.map((href) => visibleItems.find((item) => item.href === href)).filter((item): item is typeof visibleItems[number] => Boolean(item)),
		[favoriteHrefs, visibleItems],
	);

	useEffect(() => {
		if (!isOpen) return;
		const handleEsc = (e: KeyboardEvent) => {
			if (e.key === "Escape") onClose();
		};
		const handleClickOutside = (e: MouseEvent) => {
			if (toggleRef?.current?.contains(e.target as Node)) return;
			if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
				onClose();
			}
		};
		document.addEventListener("keydown", handleEsc);
		document.addEventListener("mousedown", handleClickOutside);
		return () => {
			document.removeEventListener("keydown", handleEsc);
			document.removeEventListener("mousedown", handleClickOutside);
		};
	}, [isOpen, onClose, toggleRef]);

	if (!isOpen) return null;

	return (
		<div
			ref={menuRef}
			className="absolute top-full left-0 right-0 z-50 max-h-[calc(100vh-4rem)] overflow-y-auto border-b border-gray-200/90 bg-white shadow-xl dark:border-slate-600/80 dark:bg-[#0a0f1a] dark:shadow-black/60"
		>
			<div className="mx-auto max-w-7xl px-4 py-4 sm:px-6">
				<div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
					<div className="rounded-2xl border border-gray-200/80 bg-white p-4 shadow-sm dark:border-slate-600 dark:bg-slate-800">
						<SectionTitle label="Inicio" color="blue" description="Punto de entrada principal del sistema." />
						<div className="flex flex-col gap-2">
							<MenuItem href="/" label="Inicio" description="Volver al panel principal" icon={<HomeIcon className="size-4" />} isActive={isActive("/")} onClick={onClose} />
						</div>
					</div>
					{favoriteItems.length > 0 ? (
						<div className="rounded-2xl border border-gray-200/80 bg-white p-4 shadow-sm dark:border-slate-600 dark:bg-slate-800">
							<SectionTitle label="Favoritos" color="amber" description="Tus accesos más usados para entrar más rápido." />
							<div className="flex flex-col gap-2">
								{favoriteItems.map((item) => {
									const Icon = item.icon;
									return (
										<MenuItem
											key={`favorite-${item.href}`}
											href={item.href}
											label={item.label}
											description={item.description}
											icon={<Icon className="size-4" />}
											isActive={isActive(item.href)}
											external={item.external}
											onClick={onClose}
										/>
									);
								})}
							</div>
						</div>
					) : null}
					{visibleSections.map((section) => (
						<div key={section.label} className="rounded-2xl border border-gray-200/80 bg-white p-4 shadow-sm dark:border-slate-600 dark:bg-slate-800">
							<SectionTitle label={section.label} color={section.color} description={section.description} />
							<div className="flex flex-col gap-2">
								{section.items.map((item) => {
									const Icon = item.icon;
									return (
										<MenuItem
											key={item.href}
											href={item.href}
											label={item.label}
											description={item.description}
											icon={<Icon className="size-4" />}
											isActive={isActive(item.href)}
											external={item.external}
											onClick={onClose}
										/>
									);
								})}
							</div>
						</div>
					))}
				</div>
			</div>
		</div>
	);
};

export default MegaMenu;
