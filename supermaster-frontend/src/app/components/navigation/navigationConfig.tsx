"use client";

import type { ElementType, SVGProps } from "react";
import {
    AdjustmentsHorizontalIcon,
    ArrowPathIcon,
    ArrowTrendingUpIcon,
    BoltIcon,
    BuildingStorefrontIcon,
    BookOpenIcon,
    ClipboardDocumentIcon,
    BuildingOfficeIcon,
    CalculatorIcon,
    CheckCircleIcon,
    ComputerDesktopIcon,
    CreditCardIcon,
    CurrencyDollarIcon,
    CubeIcon,
    ExclamationTriangleIcon,
    FunnelIcon,
    ShareIcon,
    GlobeAltIcon,
    PresentationChartBarIcon,
    PuzzlePieceIcon,
    RectangleStackIcon,
    ServerStackIcon,
    ShoppingBagIcon,
    ShoppingCartIcon,
    Squares2X2Icon,
    TableCellsIcon,
    TagIcon,
    TruckIcon,
    UsersIcon,
    WrenchScrewdriverIcon,
} from "@heroicons/react/24/outline";

export type NavColor =
    | "blue"
    | "violet"
    | "cyan"
    | "emerald"
    | "amber"
    | "orange"
    | "rose"
    | "slate"
    | "indigo"
    | "teal"
    | "fuchsia";

export type NavItemConfig = {
    href: string;
    label: string;
    description: string;
    icon: ElementType;
    color: NavColor;
    requiredPermission?: string;
    requiredRoles?: string[];
    children?: NavItemConfig[];
    /** Si es true, el href se trata como URL externa: se abre en nueva pestaña con rel="noopener noreferrer". */
    external?: boolean;
};

/**
 * Ícono custom para el logo de KT Gastro. Renderiza la imagen webp respetando
 * proporciones (object-contain) y llenando el contenedor del menú.
 *
 * Se ignora el `className` con tamaño que viene del callsite (size-4 de los
 * heroicons) para que la imagen ocupe todo el wrapper de 28x28 (size-7) en el
 * sidebar; un logo necesita más espacio que un pictograma geométrico.
 */
const KtGastroIcon = (_props: SVGProps<SVGSVGElement> & { className?: string }) => (
    // eslint-disable-next-line @next/next/no-img-element
    <img
        src="/logos/kt-gastro-logo.webp"
        alt="KT Gastro"
        className="w-full h-full object-contain"
    />
);

export type NavSectionConfig = {
    label: string;
    description: string;
    color: NavColor;
    items: NavItemConfig[];
};

export const navigationSections: NavSectionConfig[] = [
    {
        label: "Apps",
        description: "Aplicaciones externas vinculadas al ecosistema.",
        color: "amber",
        items: [
            {
                href: "http://servidor:4200",
                label: "Showroom",
                description: "Vista del showroom de KT Gastro (app externa)",
                icon: KtGastroIcon,
                color: "amber",
                external: true,
            },
        ],
    },
    {
        label: "Análisis",
        description: "Lectura rápida del negocio y los márgenes.",
        color: "cyan",
        items: [
            {
                href: "/estadisticas",
                label: "Estadísticas",
                description: "Márgenes, markup y distribución por canal",
                icon: PresentationChartBarIcon,
                color: "cyan",
                requiredPermission: "ESTADISTICAS_VER",
            },
        ],
    },
    {
        label: "Control",
        description: "Trazabilidad y revisión transversal del sistema.",
        color: "rose",
        items: [
            {
                href: "/auditoria",
                label: "Auditoría",
                description: "Historial global de cambios auditados del sistema",
                icon: ClipboardDocumentIcon,
                color: "rose",
                requiredPermission: "AUDITORIA_VER",
            },
        ],
    },
    {
        label: "Maestros",
        description: "Entidades base para operar el catálogo.",
        color: "blue",
        items: [
            {
                href: "/productos",
                label: "Productos",
                description: "Catálogo de artículos, costos y clasificaciones",
                icon: CubeIcon,
                color: "blue",
                requiredPermission: "PRODUCTOS_VER",
            },
            {
                href: "/proveedores",
                label: "Proveedores",
                description: "Datos, financiación y lead time de proveedores",
                icon: TruckIcon,
                color: "blue",
                requiredPermission: "MAESTROS_VER",
            },
            {
                href: "/clientes",
                label: "Clientes",
                description: "Listado de clientes habilitados",
                icon: UsersIcon,
                color: "blue",
                requiredPermission: "MAESTROS_VER",
            },
            {
                href: "/catalogos",
                label: "Catálogos",
                description: "Agrupaciones de productos para exportación y listas",
                icon: RectangleStackIcon,
                color: "blue",
                requiredPermission: "MAESTROS_VER",
            },
        ],
    },
    {
        label: "Referencias",
        description: "Taxonomías y datos maestros complementarios.",
        color: "violet",
        items: [
            {
                href: "/mlas",
                label: "MLAs",
                description: "Publicaciones de Mercado Libre vinculadas a productos",
                icon: ShoppingBagIcon,
                color: "violet",
                requiredPermission: "MLAS_VER",
            },
            {
                href: "/marcas",
                label: "Marcas",
                description: "Marcas comerciales de productos",
                icon: TagIcon,
                color: "violet",
                requiredPermission: "MAESTROS_VER",
            },
            {
                href: "/tipos",
                label: "Tipos",
                description: "Tipos de producto",
                icon: AdjustmentsHorizontalIcon,
                color: "violet",
                requiredPermission: "MAESTROS_VER",
            },
            {
                href: "/materiales",
                label: "Materiales",
                description: "Materiales de fabricación",
                icon: PuzzlePieceIcon,
                color: "violet",
                requiredPermission: "MAESTROS_VER",
            },
            {
                href: "/origenes",
                label: "Orígenes",
                description: "País o región de origen",
                icon: GlobeAltIcon,
                color: "violet",
                requiredPermission: "MAESTROS_VER",
            },
            {
                href: "/clasificaciones",
                label: "Clasif. Grales",
                description: "Clasificaciones generales de productos",
                icon: Squares2X2Icon,
                color: "violet",
                requiredPermission: "MAESTROS_VER",
            },
            {
                href: "/clasif-gastro",
                label: "Clasif. Gastro",
                description: "Clasificaciones gastronómicas",
                icon: BuildingOfficeIcon,
                color: "violet",
                requiredPermission: "MAESTROS_VER",
            },
            {
                href: "/aptos",
                label: "Aptos",
                description: "Aptitudes y certificaciones",
                icon: CheckCircleIcon,
                color: "violet",
                requiredPermission: "MAESTROS_VER",
            },
        ],
    },
    {
        label: "Canales",
        description: "Configuración comercial de los canales de venta.",
        color: "cyan",
        items: [
            {
                href: "/canales",
                label: "Canales",
                description: "Canales de venta y sus conceptos de cálculo",
                icon: BuildingStorefrontIcon,
                color: "cyan",
                requiredPermission: "CANALES_VER",
            },
            {
                href: "/canal-regla",
                label: "Reglas de Canal",
                description: "Filtros que definen qué productos forman parte del canal",
                icon: FunnelIcon,
                color: "cyan",
                requiredPermission: "CANALES_VER",
            },
            {
                href: "/canal-formula",
                label: "Fórmula del Canal",
                description: "Pipeline visual de conceptos y reglas que arman el precio",
                icon: ShareIcon,
                color: "cyan",
                requiredPermission: "CANALES_VER",
            },
            {
                href: "/calculadora-precios",
                label: "Calculadora de Precios",
                description: "Simular el PVP de un producto hipotético contra cualquier canal",
                icon: CalculatorIcon,
                color: "cyan",
                requiredPermission: "CANALES_VER",
            },
        ],
    },
    {
        label: "Precios",
        description: "Herramientas para seguimiento y reglas de precio.",
        color: "emerald",
        items: [
            {
                href: "/producto-canal-precios",
                label: "Monitor de Precios",
                description: "Precios actuales por producto y canal",
                icon: ComputerDesktopIcon,
                color: "emerald",
                requiredPermission: "PRECIOS_VER",
            },
            {
                href: "/conceptos-gastos",
                label: "Conceptos Cálculo",
                description: "Comisiones, costos y reglas de precio",
                icon: CalculatorIcon,
                color: "emerald",
                requiredPermission: "PRECIOS_VER",
            },
            {
                href: "/canal-concepto-cuotas",
                label: "Cuotas por Canal",
                description: "Configuración de cuotas e intereses por canal",
                icon: CreditCardIcon,
                color: "emerald",
                requiredPermission: "PRECIOS_VER",
            },
            {
                href: "/canal-concepto-regla",
                label: "Reglas de Excepción",
                description: "Excluir o incluir conceptos por canal y producto",
                icon: ExclamationTriangleIcon,
                color: "emerald",
                requiredPermission: "PRECIOS_VER",
            },
            {
                href: "/reglas-descuento",
                label: "Reglas Descuento",
                description: "Descuentos automáticos por volumen o canal",
                icon: TagIcon,
                color: "emerald",
                requiredPermission: "PRECIOS_VER",
            },
            {
                href: "/precios-inflados",
                label: "Precios Inflados",
                description: "Precios con recargo para cuotas",
                icon: ArrowTrendingUpIcon,
                color: "emerald",
                requiredPermission: "PRECIOS_VER",
            },
        ],
    },
    {
        label: "Compras",
        description: "Circuito de abastecimiento y reposición.",
        color: "fuchsia",
        items: [
            {
                href: "/ordenes-compra",
                label: "Órdenes de Compra",
                description: "Crear, enviar y recibir órdenes a proveedores",
                icon: ShoppingCartIcon,
                color: "fuchsia",
                requiredPermission: "ORDENES_COMPRA_VER",
            },
            {
                href: "/reposicion",
                label: "Reposición",
                description: "Cálculo automático de stock sugerido",
                icon: ArrowPathIcon,
                color: "fuchsia",
                requiredPermission: "REPOSICION_VER",
                requiredRoles: ["ADMIN", "OPERADOR"],
            },
        ],
    },
    {
        label: "Integraciones",
        description: "Procesos externos y herramientas de intercambio.",
        color: "orange",
        items: [
            {
                href: "/operaciones-ml",
                label: "Operaciones ML",
                description: "Costos de envío, comisiones y configuración de envíos ML",
                icon: BoltIcon,
                color: "orange",
                requiredPermission: "INTEGRACIONES_VER",
                requiredRoles: ["ADMIN"],
            },
            {
                href: "/herramientas-excel",
                label: "Herramientas Excel",
                description: "Importar costos y exportar listas de precios",
                icon: TableCellsIcon,
                color: "orange",
                requiredPermission: "EXCEL_VER",
                requiredRoles: ["ADMIN"],
            },
            {
                href: "/catalogos-pdf",
                label: "Catálogos PDF",
                description: "Generación manual y automática de catálogos PDF",
                icon: BookOpenIcon,
                color: "orange",
                requiredPermission: "CATALOGOS_PDF_VER",
                requiredRoles: ["ADMIN"],
            },
            {
                href: "/automatizacion-precios",
                label: "Automatización Precios KT",
                description: "Sincronización de precios entre ML, DUX y TiendaNube",
                icon: CurrencyDollarIcon,
                color: "orange",
                requiredPermission: "INTEGRACIONES_VER",
                requiredRoles: ["ADMIN"],
            },
            {
                href: "/dux",
                label: "DUX ERP",
                description: "Importar productos desde el ERP DUX",
                icon: ServerStackIcon,
                color: "orange",
                requiredPermission: "INTEGRACIONES_VER",
                requiredRoles: ["ADMIN"],
                children: [
                    {
                        href: "/dux-operaciones",
                        label: "Operaciones DUX",
                        description: "Sincronización automática y manual con DUX, y operaciones auxiliares",
                        icon: ServerStackIcon,
                        color: "orange",
                        requiredPermission: "INTEGRACIONES_VER",
                        requiredRoles: ["ADMIN"],
                    },
                    {
                        href: "/dux-deudas",
                        label: "Deudas Clientes",
                        description: "Consulta de deudas de clientes desde DUX",
                        icon: ServerStackIcon,
                        color: "orange",
                        requiredPermission: "INTEGRACIONES_VER",
                        requiredRoles: ["ADMIN"],
                    },
                ],
            },
        ],
    },
    {
        label: "Configuración",
        description: "Parámetros clave del ecosistema.",
        color: "indigo",
        items: [
            {
                href: "/usuarios",
                label: "Usuarios y Accesos",
                description: "Usuarios del sistema, roles y permisos heredados",
                icon: UsersIcon,
                color: "indigo",
                requiredPermission: "USUARIOS_VER",
                requiredRoles: ["ADMIN"],
            },
            {
                href: "/config-automatizacion",
                label: "Config. Automatización Precios KT",
                description: "Parámetros clave-valor del sistema",
                icon: WrenchScrewdriverIcon,
                color: "indigo",
                requiredPermission: "CONFIGURACION_VER",
                requiredRoles: ["ADMIN"],
            },
        ],
    },
    {
        label: "Ayuda",
        description: "Documentación y guía de uso del sistema.",
        color: "slate",
        items: [
            {
                href: "/manual",
                label: "Manual de Usuario",
                description: "Flujos, referencias y operaciones del sistema",
                icon: BookOpenIcon,
                color: "slate",
            },
        ],
    },
];

export const sectionLabelColorMap: Record<NavColor, { light: string; dark: string }> = {
    blue: { light: "text-blue-600", dark: "dark:text-blue-400" },
    violet: { light: "text-purple-600", dark: "dark:text-purple-400" },
    cyan: { light: "text-cyan-600", dark: "dark:text-cyan-400" },
    emerald: { light: "text-emerald-600", dark: "dark:text-emerald-400" },
    amber: { light: "text-amber-600", dark: "dark:text-amber-400" },
    orange: { light: "text-orange-600", dark: "dark:text-orange-400" },
    rose: { light: "text-rose-600", dark: "dark:text-rose-400" },
    slate: { light: "text-gray-500", dark: "dark:text-slate-400" },
    indigo: { light: "text-indigo-600", dark: "dark:text-indigo-400" },
    teal: { light: "text-teal-600", dark: "dark:text-teal-400" },
    fuchsia: { light: "text-fuchsia-600", dark: "dark:text-fuchsia-400" },
};

export function filterNavigationSections(
    sections: NavSectionConfig[],
    options?: {
        hasPermission?: (permission: string) => boolean;
        currentRole?: string | null;
    },
): NavSectionConfig[] {
    const hasPermission = options?.hasPermission;
    const currentRole = (options?.currentRole || "").trim().toUpperCase();

    if (!hasPermission && !currentRole) return sections;

    return sections
        .map((section) => ({
            ...section,
            items: section.items.filter((item) => {
                const allowedByPermission = !item.requiredPermission || !!hasPermission?.(item.requiredPermission);
                const allowedByRole = !item.requiredRoles?.length || item.requiredRoles.map((role) => role.toUpperCase()).includes(currentRole);
                return allowedByPermission && allowedByRole;
            }),
        }))
        .filter((section) => section.items.length > 0);
}
