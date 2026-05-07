"use client";
import { useMemo, useState } from "react";
import {
    BoltIcon,
    BookOpenIcon,
    BuildingStorefrontIcon,
    ComputerDesktopIcon,
    CubeIcon,
    TruckIcon,
    TagIcon,
    ArrowTrendingUpIcon,
    ShoppingCartIcon,
    ArrowPathIcon,
    TableCellsIcon,
    Cog6ToothIcon,
    ExclamationTriangleIcon,
    MagnifyingGlassIcon,
    PlusIcon,
    RectangleStackIcon,
    ShoppingBagIcon,
    TrashIcon,
    PencilSquareIcon,
    ArrowDownTrayIcon,
    ArrowUpTrayIcon,
    UsersIcon,
    Squares2X2Icon,
    CreditCardIcon,
    WrenchScrewdriverIcon,
    ClipboardDocumentIcon,
    PresentationChartBarIcon,
    ServerStackIcon,
    FunnelIcon,
    BanknotesIcon,
    BellAlertIcon,
    CalculatorIcon,
} from "@heroicons/react/24/outline";

// ── Tipos ─────────────────────────────────────────────────────────────────────

interface Section {
    id: string;
    title: string;
    icon: React.ComponentType<{ className?: string }>;
    summary?: string;
    keywords?: string[];
    content: React.ReactNode;
}

// ── Utilidades de presentación ────────────────────────────────────────────────

function Badge({ text, color = "blue" }: { text: string; color?: "blue" | "green" | "red" | "gray" | "yellow" }) {
    const colors = {
        blue: "bg-blue-100 text-blue-800 dark:bg-blue-500/15 dark:text-blue-300",
        green: "bg-green-100 text-green-800 dark:bg-green-500/15 dark:text-green-300",
        red: "bg-red-100 text-red-800 dark:bg-red-500/15 dark:text-red-300",
        gray: "bg-gray-100 text-gray-700 dark:bg-slate-700 dark:text-slate-300",
        yellow: "bg-yellow-100 text-yellow-800 dark:bg-amber-500/15 dark:text-amber-300",
    };
    return (
        <span className={`inline-block px-2 py-0.5 rounded text-xs font-semibold ${colors[color]}`}>
            {text}
        </span>
    );
}

function Tip({ children }: { children: React.ReactNode }) {
    return (
        <div className="flex gap-2 rounded-lg border border-blue-200 bg-blue-50 p-3 text-sm text-blue-800 dark:border-blue-800 dark:bg-blue-500/10 dark:text-blue-200">
            <ExclamationTriangleIcon className="w-5 h-5 shrink-0 mt-0.5 text-blue-500 dark:text-blue-300" />
            <span>{children}</span>
        </div>
    );
}

function Warning({ children }: { children: React.ReactNode }) {
    return (
        <div className="flex gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800 dark:border-amber-800 dark:bg-amber-500/10 dark:text-amber-200">
            <ExclamationTriangleIcon className="w-5 h-5 shrink-0 mt-0.5 text-amber-500 dark:text-amber-300" />
            <span>{children}</span>
        </div>
    );
}

function Step({ n, title, children }: { n: number; title: string; children: React.ReactNode }) {
    return (
        <div className="flex gap-4">
            <div className="shrink-0 w-7 h-7 rounded-full bg-blue-600 text-white text-sm font-bold flex items-center justify-center dark:bg-blue-500">{n}</div>
            <div>
                <p className="font-semibold text-gray-800 dark:text-slate-100">{title}</p>
                <p className="text-sm text-gray-600 mt-0.5 dark:text-slate-400">{children}</p>
            </div>
        </div>
    );
}

function SectionCard({ title, icon: Icon, children }: { title: string; icon: React.ComponentType<{ className?: string }>; children: React.ReactNode }) {
    return (
        <div className="w-full min-w-0 overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-900">
            <div className="flex items-center gap-3 border-b border-gray-100 bg-gray-50 px-6 py-4 dark:border-slate-700 dark:bg-slate-800/70">
                <Icon className="w-6 h-6 text-gray-600 dark:text-slate-300" />
                <h2 className="text-lg font-bold text-gray-800 dark:text-slate-100">{title}</h2>
            </div>
            <div className="w-full min-w-0 px-6 py-5 flex flex-col gap-4 text-sm leading-relaxed text-gray-700 dark:text-slate-300">
                {children}
            </div>
        </div>
    );
}

// ── Contenido del manual ──────────────────────────────────────────────────────

const sections: Section[] = [
    {
        id: "navegacion",
        title: "Navegación General",
        icon: BookOpenIcon,
        summary: "Sidebar, mega menú, favoritos, búsqueda, exportación, orden y funciones comunes de tabla.",
        keywords: ["sidebar", "menu", "favoritos", "buscar", "exportar", "orden", "tabla"],
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    La aplicación se organiza en un <strong>sidebar lateral izquierdo</strong> con secciones:
                    Análisis, Control, Maestros, Referencias, Canales, Precios, Compras, Integraciones, Configuración y Ayuda.
                    También podés acceder a todas las secciones desde el <strong>mega menu</strong> en el header
                    (icono de grilla junto al logo), que muestra todas las secciones organizadas en columnas.
                </p>
                <p>
                    La jerarquía general es: <strong>Análisis</strong> para lectura del negocio y <strong>Control</strong>{" "}
                    para trazabilidad transversal del sistema. Luego siguen <strong>Maestros</strong> para entidades operativas base
                    y <strong>Referencias</strong> para clasificaciones auxiliares, <strong>Canales</strong> y <strong>Precios</strong> para la lógica comercial, <strong>Compras</strong> para abastecimiento e <strong>Integraciones</strong> para conexiones
                    con sistemas externos. Dentro de <strong>Configuración</strong> quedan los parámetros del sistema.
                    En <strong>Control</strong> está disponible la pantalla de <strong>Auditoría</strong>, donde se puede consultar
                    el historial global de cambios auditados del sistema.
                </p>
                <p>
                    La <strong>home</strong> funciona como portada del sistema: muestra el saludo inicial y accesos directos
                    a los módulos principales. En el sidebar también podés marcar opciones como <strong>favoritas</strong>{" "}
                    para tenerlas agrupadas arriba del menú y llegar más rápido a los módulos que usás todos los días.
                </p>
                <p>
                    Cada módulo tiene su propia tabla principal. Las acciones comunes son:
                </p>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    {[
                        { icon: MagnifyingGlassIcon, label: "Buscar", desc: "Barra de búsqueda global con placeholder descriptivo y foco automático al abrir la tabla." },
                        { icon: PlusIcon, label: "Crear", desc: "Botón azul oscuro en la esquina superior derecha." },
                        { icon: PencilSquareIcon, label: "Editar", desc: "Hacé clic directamente sobre la celda para editar su valor. Confirmá con Enter o ✓." },
                        { icon: TrashIcon, label: "Eliminar", desc: "Seleccioná filas con el checkbox y presioná Borrar." },
                        { icon: ClipboardDocumentIcon, label: "Copiar", desc: "Seleccioná filas y presioná Copiar para copiar al portapapeles (pegable en Excel)." },
                        { icon: ArrowDownTrayIcon, label: "Exportar Excel", desc: "Descarga todos los registros (con filtros aplicados) como archivo .xlsx formateado." },
                    ].map(({ icon: Icon, label, desc }) => (
                        <div key={label} className="flex gap-3 bg-gray-50 rounded-lg p-3 border border-gray-100">
                            <Icon className="w-5 h-5 text-gray-500 shrink-0 mt-0.5" />
                            <div>
                                <p className="font-semibold text-gray-800">{label}</p>
                                <p className="text-gray-600 text-xs">{desc}</p>
                            </div>
                        </div>
                    ))}
                </div>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Funciones de tabla:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>Edición inline</strong>: Hacé clic sobre una celda editable para modificar su valor directamente. Confirmá con <strong>Enter</strong> o ✓, cancelá con <strong>Esc</strong> o ✕. Las columnas editables se identifican con un icono <strong>✓</strong> en el encabezado.</li>
                        <li><strong>Campos relacionales</strong>: Las celdas de proveedor, marca, clasificación, etc. abren un buscador con autocompletado. Escribí para filtrar y seleccioná una opción.</li>
                        <li><strong>Columnas visibles</strong>: El botón <strong>⫸</strong> en la toolbar permite ocultar/mostrar columnas. Un badge azul indica cuántas están ocultas.</li>
                        <li><strong>Filtros activos</strong>: Debajo de la toolbar aparece una barra con chips de búsqueda y orden activo. Se pueden quitar de a uno o limpiar todo.</li>
                        <li><strong>Tabla vacía</strong>: Cuando no hay registros, la tabla muestra un icono de búsqueda con el mensaje &quot;No hay registros para mostrar&quot;.</li>
                        <li><strong>Footer</strong>: Muestra la cantidad de filas en la página y el total de registros, con la paginación centrada.</li>
                        <li><strong>Persistencia</strong>: El tamaño de página y la visibilidad de columnas se guardan automáticamente y se mantienen entre sesiones.</li>
                    </ul>
                </div>
                <Tip>
                    La exportación a Excel descarga <strong>todos los registros</strong> que coincidan con los filtros activos,
                    no solo la página visible. Las columnas de UI (checkbox, acciones, detalle) se excluyen automáticamente.
                    Las columnas de relación (marca, tipo, etc.) muestran el nombre resuelto en lugar del ID.
                </Tip>
                <Tip>
                    Hacé clic derecho sobre el encabezado de una columna para filtrar por ese campo específico.
                    Las columnas también se pueden redimensionar arrastrando el borde derecho del encabezado.
                </Tip>
                <Tip>
                    Hacé clic en el encabezado de una columna para ordenar por ese campo. Para ordenar por
                    varias columnas a la vez, usá <strong>Ctrl+Click</strong> en cada columna adicional. Se muestra
                    un número de prioridad junto a cada columna ordenada. Una barra debajo de la toolbar muestra los
                    ordenamientos activos, con botones para quitar cada uno o limpiar todo.
                </Tip>
                <Tip>
                    Presioná <strong>F11</strong> o el botón de expandir en la esquina superior derecha de la tabla
                    para verla en pantalla completa. Para salir, presioná <strong>Esc</strong> o <strong>F11</strong> de nuevo.
                </Tip>
                <Tip>
                    En el sidebar, al pasar el mouse sobre una opción aparece una estrella para agregarla o quitarla de
                    {" "}<strong>Favoritos</strong>. Esa selección se guarda en tu navegador y se mantiene entre recargas.
                </Tip>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Elementos del header global (visible en todas las páginas):</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>Mega menu</strong> (icono de grilla): índice rápido de todos los módulos organizados por sección.</li>
                        <li><strong>Buscador global</strong>: lookup rápido por SKU / MLA / nombre que abre el detalle del producto.</li>
                        <li><strong>Badge de procesos activos</strong>: aparece cuando hay un proceso largo corriendo en background
                            (recálculo masivo, importación DUX, cálculo de envíos ML, automatización de precios, aplicación de
                            recálculo pendiente, etc.). Muestra el nombre del proceso y enlaza a la pantalla relevante.</li>
                        <li><strong>Banner de recálculos pendientes</strong> (ámbar): aparece cuando hay cambios marcados que esperan
                            ser aplicados. Click en él para ver el detalle por motivo y aplicar (ver sección dedicada).</li>
                    </ul>
                </div>
            </div>
        ),
    },
    {
        id: "productos",
        title: "Productos",
        icon: CubeIcon,
        summary: "Catálogo principal con SKU, costo, IVA, proveedor, clasificaciones y detalle de precios.",
        keywords: ["catalogo", "sku", "costo", "iva", "proveedor", "clasificacion"],
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    El módulo de <strong>Productos</strong> es el núcleo del sistema. Cada producto tiene SKU, descripción,
                    costo, IVA, proveedor, clasificaciones y más.
                </p>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Campos clave:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>SKU</strong>: Código único del producto.</li>
                        <li><strong>Costo + IVA</strong>: Base para el cálculo de precios.</li>
                        <li><strong>Proveedor</strong>: Determina a quién se le compra en la reposición.</li>
                        <li><strong>Clasificación Gral / Gastro</strong>: Categorías para filtros y reglas.</li>
                        <li><strong>Es Máquina</strong>: Flag especial para reglas de canal.</li>
                    </ul>
                </div>
                <Tip>
                    Al hacer clic en el ícono de detalles de un producto, se abre un modal con pestañas: <strong>Precios</strong> (por defecto),
                    Margen, Precios Inflados, Historial, Aptos, Catálogos y Clientes. En la pestaña Precios, las columnas están coloreadas por grupo
                    para facilitar la lectura (verde: PVP/Inflado, ámbar: Ganancia, azul: Costos/Ingreso Neto, púrpura: Márgenes).
                </Tip>
                <Tip>
                    Las columnas <strong>Catálogo</strong>, <strong>Apto</strong> y <strong>Cliente</strong> muestran las asociaciones
                    many-to-many de cada producto. Podés filtrarlas con clic derecho en el encabezado.
                </Tip>
                <Tip>
                    <strong>Vistas guardadas</strong>: la barra superior tiene un selector de vistas que persiste filtros, ordenamiento
                    y columnas visibles. Guardá vistas con un nombre para volver a aplicarlas en un solo clic. Útil para escenarios
                    repetidos como &quot;Productos sin costo&quot;, &quot;Catálogo MercadoLibre&quot; o &quot;Reposición urgente&quot;.
                </Tip>
                <Tip>
                    El campo <strong>Imagen</strong> abre un <strong>selector visual</strong> que lista los archivos disponibles en la
                    carpeta de imágenes del backend. También se puede abrir desde el botón <strong>Seleccionar imagen</strong>{" "}
                    en el formulario de creación.
                </Tip>
                <Warning>
                    Si cambiás el costo o el IVA, los precios calculados quedan <strong>marcados como pendientes</strong>.
                    Aplicalos desde el banner ámbar del header (ver sección &quot;Recálculos Pendientes&quot;) cuando estés
                    listo. El recálculo no corre automáticamente para evitar disparar N veces si hacés N ediciones seguidas.
                </Warning>
            </div>
        ),
    },
    {
        id: "crear-producto",
        title: "Dar de Alta un Producto",
        icon: PlusIcon,
        summary: "Guía paso a paso para crear un producto y completar su formulario.",
        keywords: ["nuevo producto", "alta", "crear", "formulario"],
        content: (
            <div className="flex flex-col gap-5">
                <p>
                    Guía paso a paso para dar de alta un nuevo producto en el sistema.
                    Algunos campos del formulario son selectores que dependen de datos cargados en otras tablas.
                </p>

                <div className="bg-amber-50 border border-amber-200 rounded-lg p-4 dark:bg-amber-500/10 dark:border-amber-800">
                    <p className="font-bold text-amber-800 dark:text-amber-200 mb-2">Antes de empezar: datos que deben existir</p>
                    <p className="text-sm text-amber-700 dark:text-amber-300 mb-3">
                        El formulario de producto tiene selectores que buscan datos en estas tablas. Si la opción
                        que necesitás no aparece, primero hay que crearla en su módulo correspondiente.
                    </p>
                    <div className="overflow-x-auto">
                        <table className="text-xs border-collapse w-full">
                            <thead>
                                <tr className="bg-amber-100/50 text-amber-800 dark:bg-amber-500/15 dark:text-amber-200">
                                    <th className="px-3 py-2 border border-amber-200 dark:border-amber-800 text-left font-bold">Tabla</th>
                                    <th className="px-3 py-2 border border-amber-200 dark:border-amber-800 text-left font-bold">Donde crearla</th>
                                    <th className="px-3 py-2 border border-amber-200 dark:border-amber-800 text-center font-bold">Obligatoria</th>
                                </tr>
                            </thead>
                            <tbody className="text-amber-900 dark:text-amber-200">
                                {[
                                    { tabla: "Orígenes", donde: "Referencias > Orígenes", obligatoria: true },
                                    { tabla: "Clasif. Generales (Rubros)", donde: "Referencias > Clasificaciones", obligatoria: true },
                                    { tabla: "Tipos", donde: "Referencias > Tipos", obligatoria: true },
                                    { tabla: "Marcas", donde: "Referencias > Marcas", obligatoria: false },
                                    { tabla: "Proveedores", donde: "Maestros > Proveedores", obligatoria: false },
                                    { tabla: "Materiales", donde: "Referencias > Materiales", obligatoria: false },
                                    { tabla: "Clasif. Gastro (Subrubros)", donde: "Referencias > Clasif. Gastro", obligatoria: false },
                                    { tabla: "MLAs", donde: "Referencias > MLAs", obligatoria: false },
                                ].map(({ tabla, donde, obligatoria }) => (
                                    <tr key={tabla} className="odd:bg-white even:bg-amber-50/50 dark:odd:bg-slate-900 dark:even:bg-amber-500/5">
                                        <td className="px-3 py-1.5 border border-amber-200 dark:border-amber-800 font-semibold">{tabla}</td>
                                        <td className="px-3 py-1.5 border border-amber-200 dark:border-amber-800">{donde}</td>
                                        <td className="px-3 py-1.5 border border-amber-200 dark:border-amber-800 text-center">
                                            {obligatoria ? <Badge text="Sí" color="red" /> : <Badge text="No" color="gray" />}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                    <Tip>
                        Las tablas obligatorias (<strong>Origen</strong>, <strong>Clasif. General</strong> y <strong>Tipo</strong>)
                        deben tener al menos un registro. Sin ellos no se puede guardar el producto.
                    </Tip>
                </div>

                <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 dark:bg-blue-500/10 dark:border-blue-800">
                    <p className="font-bold text-blue-800 dark:text-blue-200 mb-3">Pasos para crear el producto</p>
                    <div className="flex flex-col gap-4">
                        <Step n={1} title="Abrir el formulario">
                            Ir a <strong>Maestros &gt; Productos</strong> y presionar el botón <strong>&quot;Crear Producto&quot;</strong>
                            en la esquina superior derecha. Se abre un modal con el formulario dividido en secciones.
                        </Step>

                        <Step n={2} title='Completar "Identificación"'>
                            Primera sección del formulario. Datos básicos del producto:
                        </Step>
                        <div className="ml-11 grid grid-cols-2 gap-2">
                            {[
                                { campo: "SKU", desc: "Código único e irrepetible (max 45 caracteres). Ej: CUT-001", req: true },
                                { campo: "Cod. Ext.", desc: "Código externo del proveedor o ERP. Opcional", req: false },
                                { campo: "Título Web", desc: "Nombre corto para publicaciones (max 100 car.)", req: true },
                                { campo: "Descripción", desc: "Nombre completo y detallado (max 100 car.)", req: true },
                                { campo: "Imagen", desc: "Selector visual de archivos existente en la carpeta de imágenes del backend. Opcional", req: false },
                                { campo: "Es Combo", desc: "Checkbox. Marcar si el producto es un combo/kit", req: false },
                                { campo: "UxB", desc: "Unidades por bulto (mínimo 1, default 1)", req: false },
                                { campo: "Activo", desc: "Checkbox. Activado por defecto", req: false },
                            ].map(({ campo, desc, req }) => (
                                <div key={campo} className="bg-white rounded border border-blue-100 p-2 dark:bg-slate-800 dark:border-blue-800">
                                    <span className="font-semibold text-blue-800 dark:text-blue-200 text-xs">
                                        {campo} {req && <span className="text-red-500">*</span>}
                                    </span>
                                    <p className="text-[11px] text-gray-500">{desc}</p>
                                </div>
                            ))}
                        </div>

                        <Step n={3} title='Completar "Económicos"'>
                            Costo e IVA del producto. Son la base para el cálculo de precios:
                        </Step>
                        <div className="ml-11 grid grid-cols-2 gap-2">
                            <div className="bg-white rounded border border-blue-100 p-2 dark:bg-slate-800 dark:border-blue-800">
                                <span className="font-semibold text-blue-800 dark:text-blue-200 text-xs">Costo Base ($) <span className="text-red-500">*</span></span>
                                <p className="text-[11px] text-gray-500">Costo de compra al proveedor. No puede ser negativo.</p>
                            </div>
                            <div className="bg-white rounded border border-blue-100 p-2 dark:bg-slate-800 dark:border-blue-800">
                                <span className="font-semibold text-blue-800 dark:text-blue-200 text-xs">IVA (%) <span className="text-red-500">*</span></span>
                                <p className="text-[11px] text-gray-500">Porcentaje de IVA. Por defecto 21%. Rango: 0-100.</p>
                            </div>
                        </div>
                        <div className="ml-11">
                            <Warning>Si después modificás el costo o IVA, el cambio queda <strong>pendiente</strong> y se aplica al apretar el banner global (ver &quot;Recálculos Pendientes&quot;).</Warning>
                        </div>

                        <Step n={4} title='Completar "Reposición y Stock"'>
                            Datos para el módulo de reposición (opcional):
                        </Step>
                        <div className="ml-11 grid grid-cols-3 gap-2">
                            {[
                                { campo: "Stock", desc: "Stock actual. Default 0" },
                                { campo: "MOQ", desc: "Cantidad mínima de pedido al proveedor" },
                                { campo: "Tag Reposición", desc: "PRIO (prioritario) o LIQ (liquidación)" },
                            ].map(({ campo, desc }) => (
                                <div key={campo} className="bg-white rounded border border-blue-100 p-2 dark:bg-slate-800 dark:border-blue-800">
                                    <span className="font-semibold text-blue-800 dark:text-blue-200 text-xs">{campo}</span>
                                    <p className="text-[11px] text-gray-500">{desc}</p>
                                </div>
                            ))}
                        </div>

                        <Step n={5} title='Completar "Clasificación y Relaciones"'>
                            Selectores que buscan en las tablas de referencia. Escribir para buscar:
                        </Step>
                        <div className="ml-11 grid grid-cols-2 gap-2">
                            {[
                                { campo: "Origen", desc: "País o región de procedencia", req: true },
                                { campo: "Clasif. General", desc: "Rubro principal (ej: Cocina, Mesa, Bazar)", req: true },
                                { campo: "Tipo", desc: "Tipología (ej: Olla, Sartén, Cuchillo)", req: true },
                                { campo: "Marca", desc: "Marca comercial (ej: Tramontina, Oster)", req: false },
                                { campo: "Proveedor", desc: "Proveedor que lo suministra", req: false },
                                { campo: "Material", desc: "Material de fabricación (ej: Acero inox.)", req: false },
                                { campo: "Clasif. Gastro", desc: "Subrubro gastronómico específico", req: false },
                                { campo: "MLA", desc: "Publicación de MercadoLibre asociada", req: false },
                            ].map(({ campo, desc, req }) => (
                                <div key={campo} className="bg-white rounded border border-blue-100 p-2 dark:bg-slate-800 dark:border-blue-800">
                                    <span className="font-semibold text-blue-800 dark:text-blue-200 text-xs">
                                        {campo} {req && <span className="text-red-500">*</span>}
                                    </span>
                                    <p className="text-[11px] text-gray-500">{desc}</p>
                                </div>
                            ))}
                        </div>
                        <div className="ml-11">
                            <Tip>
                                Los selectores son de búsqueda: empezá a escribir y aparecen las opciones que coincidan.
                                Si no aparece lo que necesitás, primero crealo en su tabla correspondiente (ver cuadro de arriba).
                            </Tip>
                        </div>

                        <Step n={6} title='Completar "Dimensiones Físicas" (opcional)'>
                            Medidas del producto. Todos los campos son opcionales:
                        </Step>
                        <div className="ml-11">
                            <p className="text-xs text-gray-600">
                                Capacidad (texto libre, ej: &quot;500ml&quot;), Largo (cm), Ancho (cm), Alto (cm),
                                Diámetro boca, Diámetro base y Espesor (mm).
                            </p>
                        </div>

                        <Step n={7} title="Crear Producto">
                            Presionar <strong>&quot;Crear Producto&quot;</strong>. El sistema valida los campos obligatorios.
                            Si hay errores, se marcan en rojo con un mensaje debajo del campo.
                            Si todo está correcto, el producto se crea y aparece en la tabla.
                        </Step>
                    </div>
                </div>

                <div className="bg-gray-50 border border-gray-200 rounded-lg p-4 dark:bg-slate-800/70 dark:border-slate-700">
                    <p className="font-bold text-gray-700 dark:text-slate-100 mb-2">Después de crear el producto</p>
                    <p className="text-sm text-gray-600 dark:text-slate-400 mb-3">
                        El producto ya está dado de alta. Opcionalmente podés completar estos datos adicionales
                        desde el <strong>modal de detalle</strong> (clic en el ícono de ojo en la tabla):
                    </p>
                    <ul className="list-disc list-inside space-y-1.5 text-sm text-gray-600">
                        <li><strong>Margen</strong>: Configurar margen minorista/mayorista (porcentual o fijo). Necesario para calcular precios.</li>
                        <li><strong>Precios Inflados</strong>: Asignar regla de precio inflado por canal (ej: DÓLAR BLUE).</li>
                        <li><strong>Historial</strong>: Ver el historial de cambios del producto.</li>
                        <li><strong>Aptos</strong>: Asignar certificaciones (Apto horno, Apto lavavajillas, etc.).</li>
                        <li><strong>Catálogos</strong>: Incluir el producto en catálogos para exportar listas de precios.</li>
                        <li><strong>Clientes</strong>: Asociar clientes específicos.</li>
                        <li><strong>Precios</strong>: Ver y recalcular los precios por canal y cuota.</li>
                    </ul>
                </div>
            </div>
        ),
    },
    {
        id: "proveedores",
        title: "Proveedores",
        icon: TruckIcon,
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    Los proveedores se asocian a los productos. En la reposición de stock, se agrupan las órdenes de compra por proveedor.
                </p>
                <p>
                    Campos: <Badge text="Nombre / Razón Social" color="gray" /> (obligatorio),
                    <Badge text="Apodo / Alias" color="gray" /> (obligatorio),
                    <Badge text="Plazo Pago" color="gray" />,
                    <Badge text="% Financiación" color="gray" />,
                    <Badge text="Lead Time (días)" color="gray" />,
                    <Badge text="¿Realiza entregas?" color="gray" />.
                </p>
                <Tip>
                    El <strong>% Financiación</strong>{" "}se aplica automáticamente como recargo sobre el costo cuando el canal tiene un concepto con &quot;Aplica Sobre&quot; = Financiación Proveedor.
                </Tip>
            </div>
        ),
    },
    {
        id: "clientes",
        title: "Clientes",
        icon: UsersIcon,
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    El módulo de <strong>Clientes</strong> permite gestionar los clientes habilitados en el sistema.
                    Los clientes se pueden asociar a productos para definir precios especiales o condiciones particulares.
                </p>
                <p>
                    Campos: <Badge text="Nombre" color="gray" /> (obligatorio).
                </p>
                <Tip>
                    Desde el detalle de un producto podés ver y gestionar qué clientes tiene asociados.
                    También podés usar el botón <strong>Ver productos</strong> en la tabla para ir directamente
                    a Productos filtrados por ese cliente.
                </Tip>
            </div>
        ),
    },
    {
        id: "referencias",
        title: "Referencias (Tablas auxiliares)",
        icon: Squares2X2Icon,
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    Las <strong>Referencias</strong> son tablas auxiliares que clasifican y organizan los productos.
                    Todas funcionan igual: tabla con búsqueda, crear, editar y eliminar.
                </p>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Tablas disponibles:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>MLAs</strong>: Publicaciones de MercadoLibre que se pueden vincular con productos.</li>
                        <li><strong>Marcas</strong>: Marcas comerciales (ej: Tramontina, Oster). Soporta jerarquía padre-hijo (&quot;Pertenece a&quot;).</li>
                        <li><strong>Tipos</strong>: Tipología del producto (ej: Olla, Sartén, Cuchillo). Soporta jerarquía padre-hijo.</li>
                        <li><strong>Materiales</strong>: Material de fabricación (ej: Acero inoxidable, Aluminio).</li>
                        <li><strong>Orígenes</strong>: País o región de procedencia.</li>
                        <li><strong>Clasif. Generales</strong>: Clasificación amplia (ej: Cocina, Mesa, Bazar). Soporta jerarquía padre-hijo.</li>
                        <li><strong>Clasif. Gastro</strong>: Clasificación gastronómica específica. Soporta jerarquía padre-hijo y tiene el flag <strong>Máquina</strong>.</li>
                        <li><strong>Aptos</strong>: Aptitudes o certificaciones (ej: Apto horno, Apto lavavajillas).</li>
                    </ul>
                </div>
                <Tip>
                    Al crear o editar un producto, estas referencias aparecen como selectores desplegables.
                    Si necesitás agregar una nueva opción, podés hacerlo directamente desde su módulo.
                </Tip>
                <Tip>
                    Las tablas de <strong>Aptos</strong> y <strong>Clientes</strong> tienen un botón
                    {" "}<strong>Ver productos</strong> en cada fila que te lleva directamente a la tabla de Productos filtrada
                    por esa entidad.
                </Tip>
            </div>
        ),
    },
    {
        id: "catalogos",
        title: "Catálogos",
        icon: RectangleStackIcon,
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    <strong>Catálogos</strong> agrupa productos para exportaciones, listas comerciales y vistas
                    operativas del surtido. Por eso forma parte de <strong>Maestros</strong> y no de Referencias.
                </p>
                <p>
                    Cada catálogo puede contener muchos productos, y un mismo producto puede pertenecer a más de un catálogo.
                    Esta relación se usa luego en exportaciones, catálogos PDF y filtros del sistema.
                </p>
                <p>
                    Campos: <Badge text="Nombre" color="gray" /> (obligatorio),
                    <Badge text="¿Incluye IVA?" color="gray" /> (toggle para incluir o no IVA en precios exportados),
                    <Badge text="Recargo (%)" color="gray" /> (porcentaje de recargo adicional, default 0%).
                </p>
                <Tip>
                    Desde la tabla de <strong>Catálogos</strong> podés usar <strong>Ver productos</strong> para abrir
                    la tabla de Productos ya filtrada por el catálogo seleccionado.
                </Tip>
            </div>
        ),
    },
    {
        id: "canales",
        title: "Canales y Precios",
        icon: BuildingStorefrontIcon,
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    Un <strong>Canal</strong> representa un punto de venta (ej: MercadoLibre, Local, Mayorista).
                    Cada canal tiene su propia estructura de costos y precios.
                </p>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Sub-módulos relacionados:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>MLAs</strong>: Publicaciones de MercadoLibre vinculadas a productos, disponibles desde Referencias.</li>
                        <li><strong>Conceptos de Cálculo</strong>: Gastos que se suman al precio (IVA, comisiones, flete). Ver sección dedicada.</li>
                        <li><strong>Fórmula del Canal</strong>: Visualización paso a paso de la cadena de cálculo (ver sección dedicada).</li>
                        <li><strong>Reglas de Canal</strong>: Definen qué productos forman parte del canal (ver sección dedicada).</li>
                        <li><strong>Cuotas por Canal</strong>: Configuración de cuotas/intereses por canal.</li>
                        <li><strong>Reglas de Excepción</strong>: Excluir o incluir gastos específicos para combinaciones canal+producto.</li>
                        <li><strong>Reglas de Descuento</strong>: Descuentos automáticos según canal y monto (ver sección dedicada).</li>
                        <li><strong>Precios Inflados</strong>: Multiplicadores para precios inflados (ej: DOLAR_BLUE).</li>
                        <li><strong>Monitor de Precios</strong>: Vista comparativa de precios, márgenes y descuentos por canal (ver sección dedicada).</li>
                        <li><strong>Calculadora de Precios</strong>: Simulador del PVP de un producto hipotético en un canal (ver sección dedicada).</li>
                    </ul>
                </div>
                <Tip>
                    Desde la tabla de <strong>Canales</strong>, hacé clic en una fila para abrir el modal de detalle del canal,
                    que tiene cuatro pestañas: <strong>Conceptos</strong> (gastos asignados, agrupados por etapa,
                    con búsqueda y validaciones cruzadas), <strong>Cuotas</strong> (planes de financiación),
                    <strong> Reglas</strong> (excepciones de conceptos) y <strong>Descuentos</strong> (reglas de descuento por monto).
                    Cada pestaña permite gestionar las relaciones del canal sin salir de la pantalla.
                </Tip>
                <Tip>
                    En la pestaña <strong>Conceptos</strong> del modal, los conceptos quedan agrupados por etapa
                    (Costo / Margen / Impuestos / Precio / Post-Precio). El selector de &quot;Agregar concepto&quot;
                    los muestra organizados por etapa con sus porcentajes o ⚑ para flags. Un panel de
                    <strong> validaciones cruzadas</strong> alerta sobre flags duplicados o conceptos contradictorios
                    (ej: IIBB sin IVA, ambos flags de margen activos).
                </Tip>
                <Tip>
                    Al <strong>crear un canal</strong>, hay un selector opcional <strong>&quot;Copiar conceptos de&quot;</strong>{" "}
                    que clona conceptos + reglas de excepción de un canal existente al canal nuevo. Útil para crear
                    canales similares (ej: &quot;ML Junio&quot; basado en &quot;ML Mayo&quot;) sin tener que asignarlos uno a uno.
                </Tip>
                <Tip>
                    Diferencia clave entre <strong>Reglas de Canal</strong> y <strong>Reglas de Excepción</strong>:
                    las <strong>Reglas de Canal</strong> deciden <em>qué productos pertenecen al canal</em> (eligibilidad).
                    Las <strong>Reglas de Excepción</strong> deciden <em>qué conceptos de cálculo aplican a productos que ya están en el canal</em>.
                </Tip>
                <Tip>
                    Las <strong>Reglas de Excepción</strong> permiten, por ejemplo, que en MercadoLibre NO se cobre el concepto &quot;Flete Local&quot; para productos de cierta marca.
                </Tip>
                <Tip>
                    Un canal puede tener un <strong>Canal Base</strong> configurado: en ese caso su PVP se calcula
                    a partir del PVP del canal padre (no del costo del producto). Es útil para definir variantes
                    como &quot;ML Mayorista&quot; basado en &quot;Mayorista&quot;. Cuando se modifica algo del padre
                    (cuotas, conceptos, reglas, descuentos), los <strong>subcanales se marcan automáticamente como
                    pendientes</strong> también — sus precios dependen del padre y se mantienen sincronizados.
                </Tip>
            </div>
        ),
    },
    {
        id: "conceptos-calculo",
        title: "Conceptos de Cálculo",
        icon: BanknotesIcon,
        summary: "Gastos que se suman al precio del producto en cada canal (IVA, comisiones, flete, etc.).",
        keywords: ["concepto", "gasto", "iva", "comision", "flete", "aplica sobre"],
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    Los <strong>Conceptos de Cálculo</strong> son los componentes que se suman al costo
                    del producto para llegar al PVP. Cada concepto define un porcentaje (o flag) y dónde se
                    aplica en la fórmula. Después se asignan a uno o más canales con su porcentaje específico.
                </p>

                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Campos del concepto:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>Nombre</strong>: Identificador descriptivo (ej: &quot;IVA&quot;, &quot;Comisión ML&quot;, &quot;Flete Local&quot;).</li>
                        <li><strong>Porcentaje</strong>: Default a usar (puede ir entre -100 y 100; negativo = descuento).</li>
                        <li><strong>Aplica Sobre</strong>: Define en qué punto de la fórmula se calcula. Es lo más importante.</li>
                        <li><strong>Descripción</strong>: Notas internas (no afecta el cálculo).</li>
                    </ul>
                </div>

                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Las 5 etapas del cálculo (orden de aplicación):</p>
                    <p className="text-xs text-gray-500 dark:text-slate-400">
                        Cada concepto pertenece a una etapa según su <strong>Aplica Sobre</strong>. La fórmula
                        avanza Costo → Margen → Impuestos → Precio → Post-Precio. Los flags (⚑) habilitan
                        comportamiento sin usar el porcentaje.
                    </p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>📦 Costo Base</strong> — punto de partida del cálculo:
                            <span className="font-mono text-xs"> GASTO_SOBRE_COSTO</span>,
                            <span className="font-mono text-xs"> ⚑ FLAG_FINANCIACION_PROVEEDOR</span>.</li>
                        <li><strong>💰 Margen</strong> — selección y ajuste de la ganancia objetivo:
                            <span className="font-mono text-xs"> AJUSTE_MARGEN_PUNTOS</span>,
                            <span className="font-mono text-xs"> AJUSTE_MARGEN_PROPORCIONAL</span>,
                            <span className="font-mono text-xs"> ⚑ FLAG_USAR_MARGEN_MINORISTA</span>,
                            <span className="font-mono text-xs"> ⚑ FLAG_USAR_MARGEN_MAYORISTA</span>,
                            <span className="font-mono text-xs"> GASTO_POST_GANANCIA</span>.</li>
                        <li><strong>📊 Impuestos</strong> — IVA y otros impuestos:
                            <span className="font-mono text-xs"> ⚑ FLAG_APLICAR_IVA</span>,
                            <span className="font-mono text-xs"> IMPUESTO_ADICIONAL</span>,
                            <span className="font-mono text-xs"> GASTO_POST_IMPUESTOS</span>.</li>
                        <li><strong>💲 Precio</strong> — comisiones, envíos y conversión a PVP:
                            <span className="font-mono text-xs"> ⚑ FLAG_INCLUIR_ENVIO</span>,
                            <span className="font-mono text-xs"> COMISION_SOBRE_PVP</span>,
                            <span className="font-mono text-xs"> ⚑ FLAG_COMISION_ML</span>,
                            <span className="font-mono text-xs"> ⚑ FLAG_INFLACION_ML</span>,
                            <span className="font-mono text-xs"> INFLACION_SOBRE_PVP</span>,
                            <span className="font-mono text-xs"> CALCULO_SOBRE_CANAL_BASE</span> (propio o reseller).</li>
                        <li><strong>🏷 Post-Precio</strong> — recargos por cuotas, descuentos e inflados:
                            <span className="font-mono text-xs"> RECARGO_CUPON</span>,
                            <span className="font-mono text-xs"> DESCUENTO_PORCENTUAL</span>,
                            <span className="font-mono text-xs"> INFLACION_DIVISOR</span>,
                            <span className="font-mono text-xs"> GASTO_FUERA_PVP</span>,
                            <span className="font-mono text-xs"> ⚑ FLAG_APLICAR_PRECIO_INFLADO</span>.</li>
                    </ul>
                </div>

                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Aplica Sobre destacados:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>FLAG_INFLACION_ML / INFLACION_SOBRE_PVP</strong>: igual que <em>Comisión ML / Comisión sobre PVP</em> en el cálculo de PVP, pero <strong>no se cuentan como costo de venta</strong> (no reducen la ganancia neta). Útil para inflar el PVP de KT GASTRO MAQUINA sin tocar los conceptos de ML.</li>
                        <li><strong>GASTO_FUERA_PVP</strong>: costo del dueño que <strong>NO se traslada al PVP</strong> (el cliente no lo ve) pero sí cuenta como costo de venta. Ej: comisión interna del vendedor que el dueño absorbe.</li>
                        <li><strong>CALCULO_SOBRE_CANAL_BASE</strong> (canal propio): factor sobre el PVP del canal base. Escala <em>tanto el PVP como el ingreso del dueño</em>.</li>
                        <li><strong>CALCULO_SOBRE_CANAL_BASE_RESELLER</strong>: variante reseller. Escala el PVP, pero el ingreso del dueño se &quot;corta&quot; — el reseller agrega su markup encima. Ej: <span className="font-mono">LIZZY GASTRO</span> compra a mayorista×0,72 (reseller) y vende ×1,5 (canal propio).</li>
                    </ul>
                </div>

                <Tip>
                    El toolbar de la tabla incluye un botón de guía (<strong>ℹ</strong>) que abre un modal
                    con la explicación visual de cada tipo de &quot;Aplica Sobre&quot; y dónde encaja en la fórmula.
                    Útil para entender el orden de aplicación.
                </Tip>

                <Tip>
                    En la tabla, los conceptos tipo <strong>flag</strong> muestran el icono <strong>⚑</strong> en
                    lugar del porcentaje. Los porcentajes están <strong>coloreados</strong>: verde para positivos
                    (recargos), rojo para negativos (descuentos).
                </Tip>

                <Tip>
                    La tabla aparece <strong>ordenada por nombre</strong> de manera ascendente por defecto.
                </Tip>

                <Warning>
                    Modificar el <strong>porcentaje</strong> o el <strong>Aplica Sobre</strong> de un concepto
                    afecta a <strong>todos los canales</strong> que lo usan + sus subcanales. Se marcan como
                    pendientes y al apretar Aplicar se recalculan todos los productos de esos canales.
                </Warning>

                <Warning>
                    El porcentaje específico de un concepto en un canal se define en la pestaña
                    <strong> Conceptos</strong> del modal de canal — el porcentaje del concepto en sí es solo
                    un <em>default</em>. Si lo modificás, no propaga a las asignaciones existentes.
                </Warning>
            </div>
        ),
    },
    {
        id: "formula-canal",
        title: "Fórmula del Canal",
        icon: ArrowTrendingUpIcon,
        summary: "Visualización paso a paso de cómo se construye el PVP en un canal específico.",
        keywords: ["formula", "pipeline", "etapas", "visualizacion", "como se calcula"],
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    La <strong>Fórmula del Canal</strong> muestra de manera visual la <strong>cadena de
                    cálculo</strong> que arma el PVP de un canal: qué conceptos se aplican, en qué orden,
                    sobre qué base, y cómo se ramifica cuando hay reglas de excepción. Desde acá también se
                    pueden <strong>asignar, quitar y editar conceptos</strong> directamente sobre el pipeline.
                </p>

                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Para qué sirve:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li>Entender de un vistazo <strong>qué conceptos están activos</strong> en cada canal.</li>
                        <li>Visualizar las <strong>ramas</strong> que generan las reglas de excepción
                            (ej: &quot;si el producto es de marca X, este concepto se excluye&quot;).</li>
                        <li>Detectar conceptos mal configurados o duplicados.</li>
                        <li>Mostrar la fórmula a personas no técnicas para validar el modelo de negocio.</li>
                        <li>Construir o ajustar la fórmula del canal sin salir de la pantalla, con el modo edición.</li>
                    </ul>
                </div>

                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Estructura visual:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li>Cada <strong>etapa</strong> de la fórmula es un bloque coloreado:
                            📦 Costo Base, 💰 Margen, 📊 Impuestos, 💲 Precio, 🏷 Post-Precio.</li>
                        <li>Dentro de cada etapa aparecen los conceptos asignados al canal con su porcentaje
                            específico (o ⚑ si son flags).</li>
                        <li>Las <strong>reglas de excepción</strong> aparecen como ramificaciones marcadas
                            con &quot;si X entonces&quot; al expandir un concepto con reglas.</li>
                        <li>Debajo del pipeline, una tarjeta <strong>Fórmula final</strong> muestra la
                            composición simbólica y un ejemplo numérico paso a paso (con costo, IVA y plan de
                            cuotas configurables).</li>
                    </ul>
                </div>

                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Modo edición:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li>El botón <strong>Editar</strong> en el header del canal activa el modo edición y
                            muestra todas las etapas (incluso las vacías).</li>
                        <li><strong>+ Agregar</strong> en cada etapa abre un dropdown filtrado solo con los
                            conceptos compatibles con esa etapa, excluyendo los ya asignados.</li>
                        <li>Click en el porcentaje de un concepto para editarlo inline. Confirmá con
                            <strong> Enter</strong> o ✓, cancelá con <strong>Esc</strong>.</li>
                        <li>El botón <strong>×</strong> al lado del concepto lo quita del canal (con
                            confirmación; las reglas asociadas también se eliminan).</li>
                    </ul>
                </div>

                <Tip>
                    Cambiá el canal en el selector superior para comparar fórmulas entre canales. Es la mejor
                    manera de entender por qué un mismo producto tiene precios distintos según dónde se vende.
                </Tip>

                <Tip>
                    Si el canal tiene un <strong>Canal Base</strong> configurado, la fórmula muestra que el
                    cálculo arranca desde el PVP del padre en lugar del costo del producto. Solo los conceptos
                    de etapa <em>Precio</em> con &quot;Cálculo sobre canal base&quot; (variantes propio /
                    reseller) se aplican efectivamente — los demás se ignoran en este canal.
                </Tip>

                <Warning>
                    Editar el porcentaje de un concepto desde acá afecta al concepto en
                    <strong> todos los canales</strong> donde esté asignado y dispara recálculo de precios
                    (queda pendiente, se aplica desde el banner global). Si querés un porcentaje exclusivo
                    para este canal, mejor usá un concepto distinto.
                </Warning>
            </div>
        ),
    },
    {
        id: "mlas",
        title: "MLAs (MercadoLibre)",
        icon: ShoppingBagIcon,
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    Los <strong>MLAs</strong> son publicaciones de MercadoLibre. Cada MLA puede estar vinculado a uno o más productos del sistema.
                </p>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Columnas editables:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>MLA</strong>: Código de la publicación (ej: MLA1234567890).</li>
                        <li><strong>MLAU</strong>: Código alternativo opcional.</li>
                        <li><strong>Precio Envío ($)</strong>: Costo de envío sin IVA guardado.</li>
                        <li><strong>Comisión (%)</strong>: Porcentaje de comisión de ML.</li>
                        <li><strong>Tope Promo</strong>: Tope de promoción.</li>
                        <li><strong>F. Cálc. Envío</strong>: Fecha del último cálculo de costo de envío.</li>
                        <li><strong>F. Cálc. Comisión</strong>: Fecha del último cálculo de comisión.</li>
                    </ul>
                </div>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Acciones por fila:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>Envío</strong>: Calcula el costo de envío consultando la API de ML con el precio actual de la publicación.</li>
                        <li><strong>Comisión</strong>: Obtiene el porcentaje de comisión actual desde la API de ML.</li>
                        <li><strong>Ver SKUs</strong>: Muestra los productos asociados a ese MLA.</li>
                    </ul>
                </div>
                <p>
                    El botón <strong>Recálculo Masivo</strong> despliega dos paneles (envío y comisión) que no pueden
                    ejecutarse en paralelo. También se puede hacer desde <strong>Operaciones ML</strong>.
                </p>
                <Tip>
                    Los cálculos de envío y comisión quedan registrados en la <strong>Auditoría</strong> del sistema.
                </Tip>
            </div>
        ),
    },
    {
        id: "precios-inflados",
        title: "Precios Inflados",
        icon: ArrowTrendingUpIcon,
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    Los <strong>Precios Inflados</strong> permiten generar un precio &quot;inflado&quot; (precio tachado) que se muestra
                    al cliente junto al precio real, simulando un descuento. Se asignan por producto y canal. Por ejemplo,
                    si un producto tiene PVP $10.000 y una regla de inflado MULTIPLICADOR x1.30, el precio tachado será $13.000
                    y el cliente verá &quot;$13.000&quot; tachado con &quot;$10.000&quot; como precio final.
                </p>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Tipos disponibles:</p>
                    <div className="overflow-x-auto">
                        <table className="text-xs border-collapse w-full">
                            <thead>
                                <tr className="bg-gray-50 text-gray-600 dark:bg-slate-800 dark:text-slate-300">
                                    <th className="px-3 py-2 border border-gray-200 dark:border-slate-700 text-left">Tipo</th>
                                    <th className="px-3 py-2 border border-gray-200 dark:border-slate-700 text-left">Descripción</th>
                                    <th className="px-3 py-2 border border-gray-200 dark:border-slate-700 text-left">Ejemplo</th>
                                </tr>
                            </thead>
                            <tbody>
                                {[
                                    ["MULTIPLICADOR", "Multiplica el precio por el valor", "1.10 → +10%"],
                                    ["DESCUENTO_PORC", "PVP / (1 - valor/100)", "valor=30 → PVP / 0.70"],
                                    ["DIVISOR", "Divide el precio por el valor", "0.9 → +11%"],
                                    ["PRECIO_FIJO", "Establece un precio fijo en $", "$ 999.99"],
                                ].map(([tipo, desc, ej]) => (
                                    <tr key={tipo} className="odd:bg-white even:bg-gray-50 dark:odd:bg-slate-900 dark:even:bg-slate-800/70">
                                        <td className="px-3 py-1.5 border border-gray-200 dark:border-slate-700 font-mono font-semibold text-blue-800 dark:text-blue-300">{tipo}</td>
                                        <td className="px-3 py-1.5 border border-gray-200 dark:border-slate-700">{desc}</td>
                                        <td className="px-3 py-1.5 border border-gray-200 dark:border-slate-700 text-gray-500 dark:text-slate-400">{ej}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        ),
    },
    {
        id: "reglas-canal",
        title: "Reglas de Canal",
        icon: FunnelIcon,
        summary: "Definen qué productos forman parte de cada canal (eligibilidad).",
        keywords: ["canal", "incluir", "excluir", "filtro", "producto", "tag"],
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    Las <strong>Reglas de Canal</strong> deciden <em>qué productos pertenecen al canal</em>. Si un producto
                    no aplica al canal, no se le calculan precios para ese canal. Es distinto de las{" "}
                    <strong>Reglas de Excepción</strong>, que controlan qué conceptos de cálculo se aplican a productos que
                    ya están en el canal.
                </p>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Tipos de regla:</p>
                    <div className="flex gap-2">
                        <Badge text="INCLUIR" color="green" />
                        <Badge text="EXCLUIR" color="red" />
                    </div>
                    <p className="text-gray-600">
                        <strong>Sin regla</strong>: todos los productos aplican al canal.
                        <br />
                        <strong>INCLUIR</strong>: aplican <em>solamente</em> los productos que cumplen al menos una regla INCLUIR.
                        <br />
                        <strong>EXCLUIR</strong>: aplican todos los productos <em>excepto</em> los que cumplen una regla EXCLUIR.
                    </p>
                </div>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Filtros disponibles (se combinan con AND):</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>Canal</strong>: Obligatorio.</li>
                        <li><strong>Tipo, Marca, Rubro (Clasif. Gral), Gastro (Clasif. Gastro)</strong>: Filtros opcionales.</li>
                        <li><strong>Producto específico</strong>: Buscador por SKU o nombre para acotar a un único producto.</li>
                        <li><strong>Tag</strong>: <Badge text="MAQUINA" color="blue" />, <Badge text="REPUESTO" color="blue" />, <Badge text="MENAJE" color="green" />.</li>
                        <li><strong>Tiene Envío</strong>: Aplica sólo a productos con (o sin) envío configurado.</li>
                    </ul>
                </div>
                <Tip>
                    La tabla se muestra por defecto <strong>ordenada por canal</strong> de manera ascendente. Incluye un panel
                    desplegable <strong>&quot;Cómo funciona INCLUIR / EXCLUIR&quot;</strong> con ejemplos visuales.
                </Tip>
                <Tip>
                    Las condiciones dentro de una regla se combinan con <strong>AND</strong>: si ponés Tag=MAQUINA y Marca=X,
                    sólo aplica a productos que son MAQUINA <em>y además</em> de marca X. Para combinaciones alternativas
                    (Marca X <em>o</em> Marca Y) creá reglas separadas.
                </Tip>
                <Warning>
                    Las reglas de canal afectan directamente a qué productos aparecen en el canal. Si después de crear una
                    regla un producto deja de tener precio en un canal, revisá las reglas de ese canal.
                </Warning>
            </div>
        ),
    },
    {
        id: "cuotas-canal",
        title: "Cuotas por Canal",
        icon: CreditCardIcon,
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    Las <strong>Cuotas por Canal</strong> definen los planes de financiación disponibles para cada canal de venta.
                    Cada registro vincula un canal con un concepto de cálculo, un número de cuotas y un porcentaje de recargo.
                </p>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Campos:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>Canal</strong>: El canal de venta al que aplica.</li>
                        <li><strong>Concepto</strong>: El concepto de cálculo asociado.</li>
                        <li><strong>Cuotas</strong>: Cantidad de cuotas (ej: 3, 6, 12).</li>
                        <li><strong>Porcentaje</strong>: Recargo o descuento que se aplica al precio.</li>
                    </ul>
                </div>
                <Tip>
                    Las cuotas configuradas aquí se usan en la exportación de listas de precios con el parámetro &quot;cuotas&quot;.
                </Tip>
                <Tip>
                    La tabla se muestra por defecto <strong>ordenada por canal</strong> de manera ascendente, para que las cuotas
                    de un mismo canal queden agrupadas en pantalla.
                </Tip>
            </div>
        ),
    },
    {
        id: "reglas-excepcion",
        title: "Reglas de Excepción",
        icon: ExclamationTriangleIcon,
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    Las <strong>Reglas de Excepción</strong> permiten excluir o incluir conceptos de cálculo específicos
                    para combinaciones de canal, marca, clasificación, tipo o producto.
                </p>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Tipos de regla:</p>
                    <div className="flex gap-2">
                        <Badge text="EXCLUIR" color="red" />
                        <Badge text="INCLUIR" color="green" />
                    </div>
                    <p className="text-gray-600">
                        <strong>EXCLUIR</strong>: El concepto NO se aplica para esa combinación.
                        <br />
                        <strong>INCLUIR</strong>: El concepto SÍ se aplica solo para esa combinación (override).
                    </p>
                </div>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Filtros disponibles:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>Canal + Concepto</strong>: Obligatorios en cada regla.</li>
                        <li><strong>Marca, Rubro (Clasif. Gral), Gastro (Clasif. Gastro), Tipo</strong>: Filtros opcionales para acotar la regla.</li>
                        <li><strong>Tag</strong>: Filtra por etiqueta del producto (<Badge text="MAQUINA" color="blue" />, <Badge text="REPUESTO" color="blue" />, <Badge text="MENAJE" color="green" />).</li>
                        <li><strong>Tiene Envío</strong>: Aplica sólo a productos con (o sin) envío configurado.</li>
                    </ul>
                </div>
                <Tip>
                    La tabla muestra por defecto las reglas <strong>ordenadas por canal</strong> de manera ascendente. La columna
                    <strong> Concepto Afectado</strong> muestra un <strong>tooltip al pasar el mouse</strong> con la descripción
                    completa del concepto (cuando está cargada).
                </Tip>
                <Tip>
                    La pantalla incluye un panel desplegable <strong>&quot;Cómo funciona INCLUIR / EXCLUIR&quot;</strong> con
                    ejemplos visuales. Resumen rápido: <strong>sin regla</strong> el concepto se aplica a todos los productos del canal;
                    <strong> INCLUIR</strong> lo restringe únicamente a los productos que cumplen la condición; <strong>EXCLUIR</strong>{" "}
                    lo aplica a todos menos a los que cumplen la condición.
                </Tip>
                <Warning>
                    Las reglas de excepción afectan directamente al cálculo de precios. Verificá siempre el resultado en el Monitor de Precios después de crear o modificar una regla.
                </Warning>
            </div>
        ),
    },
    {
        id: "reglas-descuento",
        title: "Reglas de Descuento",
        icon: TagIcon,
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    Las <strong>Reglas de Descuento</strong> definen descuentos automáticos que se aplican según el canal de venta
                    y un monto mínimo de compra. Cuando el PVP supera el monto mínimo, el descuento se aplica y se recalculan
                    los márgenes.
                </p>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Campos principales:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>Canal</strong>: Canal de venta al que aplica la regla.</li>
                        <li><strong>Catálogo</strong>: Catálogo al que aplica (opcional).</li>
                        <li><strong>Clasif. General</strong>: Clasificación general para acotar (opcional).</li>
                        <li><strong>Clasif. Gastro</strong>: Clasificación gastronómica para acotar (opcional).</li>
                        <li><strong>Monto mínimo</strong>: PVP mínimo para que el descuento se active.</li>
                        <li><strong>Descuento (%)</strong>: Porcentaje de descuento a aplicar sobre el PVP.</li>
                        <li><strong>Prioridad</strong>: Orden de aplicación cuando coinciden varias reglas.</li>
                        <li><strong>Activo</strong>: Habilita o deshabilita la regla.</li>
                        <li><strong>Descripción</strong>: Nota interna para documentar la regla.</li>
                    </ul>
                </div>
                <Tip>
                    Los descuentos se ven reflejados en el Monitor de Precios en las columnas &quot;c/Desc&quot; (PVP, Ganancia, Costos Venta,
                    Ingreso Neto, márgenes y markup con descuento aplicado).
                </Tip>
                <Tip>
                    La tabla se muestra por defecto <strong>ordenada por canal</strong> de manera ascendente, para agrupar las
                    reglas de un mismo canal y comparar prioridades fácilmente.
                </Tip>
            </div>
        ),
    },
    {
        id: "recalculos-pendientes",
        title: "Recálculos Pendientes (Banner)",
        icon: BellAlertIcon,
        summary: "Cómo aplicar los cambios pendientes desde el banner global del header.",
        keywords: ["pendiente", "banner", "aplicar", "recalcular", "scope"],
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    Cuando se modifica algo que afecta el cálculo de precios (costo, margen, MLA, regla de canal,
                    cuotas, concepto, etc.), el sistema <strong>no recalcula automáticamente</strong>. En su lugar
                    marca el cambio como <strong>pendiente</strong> y aparece un <strong>banner ámbar</strong> en
                    el header con el detalle. El recálculo recién corre cuando apretás <strong>Aplicar</strong>.
                </p>
                <Tip>
                    Esto es a propósito: si hacés 10 ediciones seguidas, antes se disparaban 10 recálculos
                    encadenados (lentos, ruidosos, a veces redundantes). Ahora hacés las 10 ediciones y aplicás
                    una sola vez al final → un único recálculo eficiente con scope acotado.
                </Tip>

                <div className="flex flex-col gap-2">
                    <p className="font-semibold">El banner muestra:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>Cardinalidad real del scope</strong>: por ejemplo &quot;3 productos pendientes&quot;,
                            &quot;2 canales pendientes&quot;, o &quot;Recálculo masivo pendiente&quot; según lo que
                            haya cambiado.</li>
                        <li><strong>Detalle de motivos</strong>: al hacer clic, se despliega la lista de motivos
                            con cuántas ediciones de cada tipo hay.</li>
                        <li><strong>Botón Aplicar</strong>: dispara el recálculo en background sin bloquear la UI.</li>
                    </ul>
                </div>

                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Cómo se decide el scope (qué se recalcula):</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>Edit de costo / IVA / margen / atributo de UN producto</strong> → solo ese producto
                            en todos los canales (~250 ms).</li>
                        <li><strong>Edit de cuotas / regla / regla excepción / canal base de UN canal</strong> → todos
                            los productos del canal + sus subcanales (los subcanales heredan el PVP del padre).</li>
                        <li><strong>Edit de regla de descuento de UN canal</strong> → todos los productos del canal + subcanales.</li>
                        <li><strong>Edit del precioEnvio o comisión de UN MLA</strong> → solo los productos asociados a ese MLA.</li>
                        <li><strong>Edit de financiación de UN proveedor</strong> → solo los productos de ese proveedor.</li>
                        <li><strong>Edit de esMaquina en UNA clasif gastro</strong> → solo los productos de esa clasif.</li>
                        <li><strong>Edit del porcentaje o aplicaSobre de UN concepto de cálculo</strong> → todos los canales que usan ese concepto + subcanales.</li>
                    </ul>
                </div>

                <Tip>
                    Mientras el recálculo corre en background, el header muestra <strong>&quot;Aplicando recálculo
                    pendiente&quot;</strong> como proceso activo. El Monitor de Precios y otras vistas se refrescan
                    automáticamente apenas termina.
                </Tip>

                <Warning>
                    Si apretás Aplicar mientras hay otro proceso del grupo BD corriendo (por ejemplo un
                    Recálculo Masivo o una importación DUX), el sistema responde <strong>409 (conflicto)</strong>
                    y muestra el toast &quot;Ya hay un recálculo en proceso&quot;. Los pendientes <strong>no se
                    pierden</strong>: quedan acumulados, esperá a que el otro proceso termine y volvé a apretar
                    Aplicar.
                </Warning>

                <Warning>
                    Si <strong>reiniciás el backend</strong> con pendientes acumulados, los flags se pierden
                    (el estado vive en memoria). Si pasa eso, los precios pueden quedar desactualizados — corré
                    &quot;Recalcular Todos&quot; del Monitor para sincronizar.
                </Warning>
            </div>
        ),
    },
    {
        id: "calculadora-precios",
        title: "Calculadora de Precios",
        icon: CalculatorIcon,
        summary: "Simulador de PVP para productos hipotéticos sin tocar el catálogo.",
        keywords: ["calculadora", "simulador", "hipotetico", "what if", "que pasaria si"],
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    La <strong>Calculadora de Precios</strong> permite simular el PVP final de un producto
                    hipotético contra cualquier canal y plan de cuotas, <strong>sin necesidad de crearlo en
                    el catálogo</strong>. Usa exactamente el mismo motor que el Monitor de Precios.
                </p>

                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Casos de uso:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li>Estimar el PVP <strong>antes</strong> de cargar un producto nuevo.</li>
                        <li>Probar &quot;¿qué pasaría si subo el costo un 10%?&quot; sin tocar el catálogo real.</li>
                        <li>Comparar el mismo producto hipotético en distintos canales / cuotas.</li>
                        <li>Validar que las reglas y conceptos de un canal se aplican como esperás.</li>
                        <li>Cargar atributos de un producto existente y modificar uno solo (botón &quot;Cargar producto&quot;).</li>
                    </ul>
                </div>

                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Selectores arriba (canal y cuotas):</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>Canal</strong>: badge de canal con colores. Define el contexto del cálculo.</li>
                        <li><strong>Cuotas</strong>: muestra <em>solo los planes configurados</em> en el canal seleccionado.
                            Si el canal no tiene planes, no se muestra ninguno (en lugar de un &quot;Sin plan&quot; falso).</li>
                    </ul>
                </div>

                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Indicadores del resultado:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>PVP</strong>: precio final que paga el cliente (incluye IVA, impuestos, comisiones, recargo por cuotas).</li>
                        <li><strong>PVP Inflado</strong>: precio &quot;tachado&quot; cuando hay regla de inflado configurada.</li>
                        <li><strong>Costos Venta</strong>: comisiones del canal + descuentos + envío + recargo por cuotas.</li>
                        <li><strong>Ingreso Neto Vendedor</strong>: PVP − IVA − impuestos − costos venta.</li>
                        <li><strong>Ganancia</strong>: ingreso neto − costo producto.</li>
                        <li><strong>Margen s/PVP</strong> = ganancia / PVP.</li>
                        <li><strong>Margen s/Ingreso Neto</strong> = ganancia / ingreso neto. El más &quot;real&quot; sin ruido de IVA.</li>
                        <li><strong>Markup</strong> = ganancia / costo producto. Cuánto se recarga sobre el costo.</li>
                    </ul>
                </div>

                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Leyenda de colores en márgenes / markup (igual que el Monitor):</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li className="text-red-600">&lt;0% — pérdida (vendés debajo del costo).</li>
                        <li className="text-orange-500">0–15% — bajo.</li>
                        <li className="text-yellow-600">15–25% — moderado.</li>
                        <li className="text-green-600">25–40% — bueno.</li>
                        <li className="text-emerald-600">&gt;40% — excelente.</li>
                    </ul>
                </div>

                <Tip>
                    La Calculadora <strong>no persiste nada</strong> en la BD ni dispara recálculos. Es 100%
                    en memoria sobre un producto temporal armado a partir de los inputs.
                </Tip>

                <Warning>
                    Algunas reglas que apuntan a un producto específico por SKU no van a matchear en simulación
                    (porque el producto no existe). Eso es esperado — la calculadora simula reglas <em>generales</em>
                    del canal, no reglas hiper-específicas.
                </Warning>
            </div>
        ),
    },
    {
        id: "monitor-precios",
        title: "Monitor de Precios",
        icon: ComputerDesktopIcon,
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    El <strong>Monitor de Precios</strong> es una vista comparativa que muestra todos los precios calculados
                    por producto, canal y plan de cuotas en una sola tabla. Es la herramienta principal para verificar
                    costos, márgenes y precios finales.
                </p>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Filtros en la barra superior:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>Buscador</strong>: Filtra por SKU, MLA o nombre del producto.</li>
                        <li><strong>Canal</strong>: Filtra por canal de venta (o &quot;Todos&quot;).</li>
                        <li><strong>Cuotas</strong>: Filtra por plan de cuotas. Las opciones disponibles dependen del canal seleccionado.</li>
                        <li><strong>Vista</strong>: Selector que cambia el conjunto de columnas visibles —
                            <em>Rentabilidad</em> (PVP + ganancia + costos),
                            <em>Edición</em> (foco en costos / IVA / márgenes editables),
                            <em>Descuentos</em> (columnas c/Desc),
                            <em>Completo</em> (todas las columnas).</li>
                    </ul>
                </div>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Grupos de columnas (de izquierda a derecha):</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>Identificación</strong>: SKU, MLA, Producto.</li>
                        <li><strong>Canal/Cuotas</strong>: Canal y plan de cuotas.</li>
                        <li><strong>Costo base</strong>: Costo e IVA.</li>
                        <li><strong>Configuración de márgenes</strong>: Mrg Minorista, Mrg Mayorista, Fijo Min, Fijo May (editables haciendo clic).</li>
                        <li><strong>Precios calculados</strong>: PVP, PVP Inflado, Regla de Inflado.</li>
                        <li><strong>Resultados financieros</strong>: Ganancia, Costos de Venta, Ingreso Neto.</li>
                        <li><strong>Márgenes calculados</strong>: Margen s/PVP, Margen s/Ingreso Neto, Markup %.</li>
                        <li><strong>Descuentos</strong>: Columnas &quot;c/Desc&quot; con valores recalculados si aplica descuento.</li>
                        <li><strong>Acciones</strong>: Ver fórmula de cálculo y recalcular precios individuales.</li>
                    </ul>
                </div>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Leyenda de colores (footer):</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li className="text-red-600">&lt;0% — Margen/markup negativo.</li>
                        <li className="text-orange-500">0-15% — Margen/markup bajo.</li>
                        <li className="text-yellow-600">15-25% — Margen/markup moderado.</li>
                        <li className="text-green-600">25-40% — Margen/markup bueno.</li>
                        <li className="text-emerald-600">&gt;40% — Margen/markup excelente.</li>
                    </ul>
                </div>
                <Tip>
                    Las ediciones inline (costo, IVA, márgenes, regla de inflado) <strong>marcan el cambio como
                    pendiente</strong> y aparecen en el banner ámbar del header. El recálculo corre cuando apretás
                    Aplicar — ver sección &quot;Recálculos Pendientes&quot;. La fila editada se refresca al toque
                    para que veas el costo nuevo aunque los precios calculados todavía sean los viejos.
                </Tip>
                <Tip>
                    Cuando se aplica el recálculo (sea desde el banner, &quot;Recalcular Todos&quot; o
                    &quot;Recalcular&quot; individual), la tabla del Monitor se <strong>actualiza
                    automáticamente</strong> apenas termina el procesamiento en background. No hace falta
                    refrescar la página manualmente.
                </Tip>
                <Tip>
                    El botón <strong>&quot;Recalcular Todos&quot;</strong> (rojo, en el encabezado) recalcula los precios de todos los productos
                    en todos los canales. Tarda ~80 segundos y bloquea otros procesos del grupo BD. Usalo solo
                    cuando hayas hecho cambios verdaderamente masivos o si reiniciaste el backend con pendientes.
                </Tip>
                <Tip>
                    Hacé clic en <strong>&quot;Fórmula&quot;</strong> en cualquier fila para ver el detalle paso a paso de cómo se calcula
                    el PVP final, incluyendo cada concepto de cálculo aplicado.
                </Tip>
                <Tip>
                    Cada encabezado de columna tiene un <strong>tooltip al pasar el mouse</strong> con la fórmula y la
                    explicación del campo (Costo Producto, Ganancia, Ingreso Neto Vendedor, Margen s/PVP, Margen s/Ingreso Neto,
                    Markup y todas sus variantes &quot;c/Desc&quot;). Útil cuando hay dudas sobre qué representa cada número.
                </Tip>
            </div>
        ),
    },
    {
        id: "estadisticas",
        title: "Estadísticas",
        icon: PresentationChartBarIcon,
        summary: "Dashboard con métricas, márgenes, markup y gráficos del catálogo.",
        keywords: ["dashboard", "graficos", "margenes", "markup"],
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    El módulo de <strong>Estadísticas</strong> ofrece un dashboard visual con indicadores clave
                    y gráficos para analizar el estado general del catálogo y los márgenes de ganancia.
                </p>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Indicadores (cards superiores):</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>Total productos</strong>: Cantidad total registrada en el sistema.</li>
                        <li><strong>Activos</strong>: Productos disponibles para la venta.</li>
                        <li><strong>Con precio calculado</strong>: Productos con al menos un precio generado en algún canal.</li>
                        <li><strong>Con MLA</strong>: Productos vinculados a una publicación de MercadoLibre.</li>
                        <li><strong>Combos</strong>: Productos marcados como combo.</li>
                        <li><strong>Prioritarios</strong>: Productos con tag PRIO para reposición prioritaria.</li>
                        <li><strong>Sin stock</strong>: Productos con stock en cero.</li>
                        <li><strong>Sin costo</strong>: Productos sin costo cargado (no se puede calcular margen).</li>
                        <li><strong>Sin proveedor</strong>: Productos sin proveedor asignado.</li>
                        <li><strong>Sin imagen</strong>: Productos sin imagen cargada.</li>
                        <li><strong>Sin margen</strong>: Productos activos sin precio calculado en ningún canal.</li>
                        <li><strong>Margen negativo</strong>: Productos que se venden por debajo del costo en al menos un canal.</li>
                    </ul>
                </div>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Gráficos disponibles:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>Margen s/PVP por canal</strong>: Barras con el porcentaje de ganancia sobre el precio de venta para cada canal.</li>
                        <li><strong>Margen s/Ingreso Neto por canal</strong>: Barras con el porcentaje de ganancia sobre el ingreso neto para cada canal.</li>
                        <li><strong>Markup por canal</strong>: Barras con el porcentaje de ganancia sobre el costo para cada canal.</li>
                        <li><strong>Distribución de márgenes</strong>: Histograma que muestra cuántos productos caen en cada rango de margen (&lt;0%, 0-10%, 10-20%, etc.).</li>
                        <li><strong>Productos por proveedor (Top 10)</strong>: Gráfico de torta con la distribución de productos entre proveedores.</li>
                        <li><strong>Productos por catálogo</strong>: Barras con la cantidad de productos asociados a cada catálogo.</li>
                    </ul>
                </div>
                <Tip>
                    Usá el selector de <strong>Cuotas</strong> en la parte superior para cambiar entre contado y distintos planes
                    de cuotas. Los gráficos de margen, markup y distribución se actualizan automáticamente según la cuota seleccionada.
                </Tip>
                <Warning>
                    Si hay productos con margen negativo, se muestra una tabla detallada al final con SKU, canal, cuotas,
                    margen y ganancia (pérdida) por unidad. Estos productos requieren atención urgente.
                </Warning>
            </div>
        ),
    },
    {
        id: "reposicion",
        title: "Reposición de Stock",
        icon: ArrowPathIcon,
        summary: "Cálculo de sugerencias de compra según ventas históricas y cobertura.",
        keywords: ["stock", "sugerido", "pedido", "proveedor"],
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    El módulo de <strong>Reposición</strong> calcula automáticamente las cantidades sugeridas a pedir a cada proveedor,
                    basándose en el historial de ventas obtenido desde DUX ERP.
                </p>
                <div className="flex flex-col gap-3">
                    <Step n={1} title="Configurar parámetros">
                        Se dividen en dos bloques: <strong>Períodos y pesos</strong> (meses de cobertura, días por período y peso de cada período)
                        y <strong>Conexión DUX</strong> (ID empresa y sucursales de donde se obtienen stock y ventas). Guardá la configuración antes de calcular.
                    </Step>
                    <Step n={2} title="Calcular Reposición">
                        Presioná <strong>Calcular reposición</strong>. El proceso es asíncrono y puede tardar varios minutos.
                        Podés ver el progreso en tiempo real y cancelarlo si es necesario.
                    </Step>
                    <Step n={3} title="Revisar y ajustar">
                        Una vez completado, revisá la tabla de sugerencias. Podés modificar manualmente la columna <strong>Pedido</strong>{" "}
                        y guardar los ajustes con <strong>Guardar ajustes</strong>.
                    </Step>
                    <Step n={4} title="Generar Órdenes de Compra">
                        Con los pedidos definidos, generá las órdenes de compra automáticamente.
                        Se crearán agrupadas por proveedor para todos los productos con pedido mayor a 0.
                    </Step>
                </div>
                <div className="flex flex-col gap-1">
                    <p className="font-semibold">Columnas de la tabla de resultados:</p>
                    <ul className="text-xs text-gray-600 dark:text-slate-400 grid grid-cols-2 gap-x-6 gap-y-0.5 list-disc list-inside">
                        <li><strong>SKU / Cód.Ext</strong>: Código del producto y código externo.</li>
                        <li><strong>Proveedor</strong>: Proveedor asignado al producto.</li>
                        <li><strong>UxB / MOQ</strong>: Unidades por bulto y mínimo de pedido.</li>
                        <li><strong>Stock</strong>: Stock actual en sistema.</li>
                        <li><strong>Pend.Cli / Pend.Prov</strong>: Pendientes de clientes y proveedores.</li>
                        <li><strong>Saldo</strong>: Stock disponible neto.</li>
                        <li><strong>V.M1/M2/M3</strong>: Ventas en los últimos 3 períodos.</li>
                        <li><strong>Prom/mes / Prom/día</strong>: Promedio ponderado mensual y diario.</li>
                        <li><strong>P.Reorden</strong>: Punto en que hay que pedir.</li>
                        <li><strong>Sugerido</strong>: Cantidad calculada por el sistema.</li>
                        <li><strong>Pedido</strong>: Cantidad ajustable manualmente.</li>
                        <li><strong>Últ.Compra / Últ.Cant.</strong>: Fecha y cantidad de la última compra.</li>
                        <li><strong>Tag</strong>: PRIO = prioritario, LIQ = liquidación.</li>
                        <li><strong>Urgente</strong>: Indica si el producto necesita reposición urgente.</li>
                    </ul>
                </div>
            </div>
        ),
    },
    {
        id: "ordenes-compra",
        title: "Órdenes de Compra",
        icon: ShoppingCartIcon,
        summary: "Estados, recepción y gestión de órdenes generadas desde reposición o manualmente.",
        keywords: ["oc", "borrador", "enviada", "recibida", "completa"],
        content: (
            <div className="flex flex-col gap-4">
                <p>Las órdenes de compra se crean desde la Reposición o manualmente desde este módulo.</p>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Estados posibles:</p>
                    <div className="flex flex-wrap gap-2">
                        <Badge text="BORRADOR" color="gray" />
                        <Badge text="ENVIADA" color="blue" />
                        <Badge text="RECIBIDA_PARCIAL" color="yellow" />
                        <Badge text="COMPLETA" color="green" />
                    </div>
                    <p className="text-gray-600">
                        Al recibir una OC, podés registrar las cantidades recibidas por línea. Si no se recibe todo, queda en RECIBIDA_PARCIAL.
                    </p>
                </div>
                <Tip>
                    Podés exportar cualquier OC a Excel desde <strong>Herramientas Excel → Reposición → Descargar OC</strong> ingresando el ID de la orden.
                </Tip>
            </div>
        ),
    },
    {
        id: "excel",
        title: "Herramientas Excel",
        icon: TableCellsIcon,
        summary: "Importaciones y exportaciones masivas de costos, listas de precios, catálogos y reposición.",
        keywords: ["importar", "exportar", "archivo", "xlsx"],
        content: (
            <div className="flex flex-col gap-5">
                <div>
                    <div className="flex items-center gap-2 mb-2">
                        <ArrowUpTrayIcon className="w-5 h-5 text-blue-600" />
                        <p className="font-semibold text-blue-800">Importaciones</p>
                    </div>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>Importar Costos</strong>: Actualiza costo, IVA y proveedor desde Excel. Dispara recálculo automático de precios.</li>
                        <li><strong>Importar Migración</strong>: Solo para migración inicial. Puede sobrescribir datos masivamente.</li>
                    </ul>
                    <Warning>
                        El campo en el formulario multipart debe llamarse <code className="bg-gray-100 px-1 rounded">archivo</code>.
                    </Warning>
                </div>
                <div>
                    <div className="flex items-center gap-2 mb-2">
                        <ArrowDownTrayIcon className="w-5 h-5 text-green-600" />
                        <p className="font-semibold text-green-800">Exportaciones</p>
                    </div>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>Lista de Precios</strong>: Formatos completo, MercadoLibre, KT Hogar, KT Gastro. El formato <strong>Completo</strong> permite opcionalmente seleccionar un canal y cuotas para filtrar la exportación. Los demás formatos permiten elegir cuotas.</li>
                        <li><strong>Catálogo</strong>: Precios filtrados por catálogo y canal. Requiere ID catálogo + ID canal.</li>
                        <li><strong>Reposición → Sugerencias</strong>: Descarga el resultado del último cálculo de reposición.</li>
                        <li><strong>Reposición → OC</strong>: Descarga una orden de compra específica por ID.</li>
                    </ul>
                </div>
            </div>
        ),
    },
    {
        id: "operaciones-ml",
        title: "Operaciones ML",
        icon: BoltIcon,
        summary: "Procesos masivos asincrónicos de envío y comisiones en MercadoLibre.",
        keywords: ["mercadolibre", "comisiones", "envio", "asincrono"],
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    Este módulo combina la <strong>configuración de envíos ML</strong> y los <strong>procesos masivos</strong> de MercadoLibre en una sola pantalla.
                </p>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Configuración de envíos:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>Umbral envío gratis</strong>: Precio mínimo desde el cual ML considera el envío gratis obligatorio.</li>
                        <li><strong>Tiers de costo de envío</strong>: Tres rangos (Bajo, Medio, Alto) con tope de PVP y costo fijo que paga el vendedor.</li>
                    </ul>
                </div>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Procesos masivos:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>Actualizar Costos de Envío</strong> <Badge text="celeste" color="blue" />: Recalcula los costos de envío de todos los MLAs.</li>
                        <li><strong>Actualizar Comisiones</strong> <Badge text="ámbar" color="yellow" />: Recalcula las comisiones por categoría de ML.</li>
                    </ul>
                </div>
                <p>
                    Ambos procesos son <strong>asíncronos</strong>: se inician y se puede ver el progreso en tiempo real.
                    Cada sección tiene un color distintivo: <strong>celeste</strong> para costos de envío
                    y <strong>ámbar</strong> para comisiones.
                </p>
                <Warning>
                    Estos procesos no pueden ejecutarse en paralelo. Esperá a que uno termine antes de iniciar el otro.
                </Warning>
            </div>
        ),
    },
    {
        id: "catalogos-pdf",
        title: "Catálogos PDF",
        icon: BookOpenIcon,
        summary: "Generación manual y automática de catálogos en formato PDF.",
        keywords: ["catalogo", "pdf", "exportar", "automatico", "presupuesto", "lista"],
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    El módulo de <strong>Catálogos PDF</strong> permite generar documentos PDF de productos
                    con precios, imágenes y configuración visual personalizable. Hay dos modos de generación:
                </p>
                <SectionCard title="Generación Manual" icon={BookOpenIcon}>
                    <p>
                        Seleccionás un <strong>catálogo</strong>, <strong>canal</strong> y <strong>cuotas</strong>,
                        junto con filtros opcionales (clasificación, tipo, marca, máquina). Podés configurar:
                    </p>
                    <ul className="list-disc pl-5 space-y-1 text-sm text-gray-600 dark:text-slate-400 mt-2">
                        <li>Título y subtítulo del documento</li>
                        <li>Tipo de documento (CATALOGO o PRESUPUESTO) y tipo de hoja (A4, A3, A5, Carta)</li>
                        <li>Inclusión de imágenes y carátula</li>
                        <li>Productos por página y estética (Linea GE o KT)</li>
                        <li>Visibilidad, tamaño de fuente y color por columna (código, nombre, precio, UxB)</li>
                        <li><strong>Ordenamiento múltiple</strong>: lista priorizada de campos (Clasif. General, Clasif. Gastro, Tipo, Marca) que se aplican en cascada</li>
                    </ul>
                </SectionCard>
                <SectionCard title="Configuración Global" icon={Cog6ToothIcon}>
                    <p>
                        La <strong>carpeta global de imágenes</strong> se configura mediante una variable de entorno del servidor
                        y se muestra como campo de solo lectura en la interfaz. No se puede modificar desde la UI.
                    </p>
                </SectionCard>
                <SectionCard title="Configuraciones Automáticas" icon={WrenchScrewdriverIcon}>
                    <p>
                        Permite crear configuraciones guardadas que se pueden ejecutar individualmente o en lote.
                        Cada configuración se identifica por <strong>ID</strong> y <strong>nombre</strong>.
                        La <strong>ubicación de salida</strong> es obligatoria e indica dónde se guarda el PDF generado en el servidor.
                        Los endpoints automáticos no requieren autenticación, lo que permite integración con
                        herramientas como <Badge text="n8n" color="blue" /> u otros automatizadores.
                    </p>
                    <Tip>
                        En Docker, las rutas de salida usan <code className="bg-gray-100 px-1 rounded dark:bg-slate-700">/app/catalogos-salida/</code> que
                        mapea a la carpeta de Google Drive del host.
                    </Tip>
                </SectionCard>
            </div>
        ),
    },
    {
        id: "dux",
        title: "DUX ERP",
        icon: ServerStackIcon,
        summary: "Obtención, importación y exportación del catálogo con el ERP DUX.",
        keywords: ["erp", "importacion", "exportacion", "dux", "staging"],
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    La integración con <strong>DUX ERP</strong> permite obtener, importar y exportar el catálogo de productos.
                </p>
                <div className="flex flex-col gap-3">
                    <Step n={1} title="Obtener Productos de DUX">
                        Descarga el catálogo del ERP a un área de staging local. No modifica nada en el sistema.
                    </Step>
                    <Step n={2} title="Importar Productos al Sistema">
                        Procesa los datos del Paso 1 y actualiza los productos existentes (costo, IVA, proveedor, descripcion). Los SKUs no encontrados se reportan en el resultado.
                        Disponible solo después de completar el Paso 1.
                    </Step>
                </div>
                <Warning>
                    La opción <strong>Exportar Productos a DUX</strong> (panel rojo &quot;Escritura en DUX&quot;)
                    escribe datos <em>directamente</em> en el ERP — puede llevar minutos y altera datos de
                    producción del ERP. Por seguridad el botón aparece <strong>deshabilitado</strong> en la UI
                    actual; pedí habilitación al admin si realmente necesitás disparar la sincronización.
                </Warning>
                <p>También podés consultar productos individuales de DUX por código, ver listas de precios y empresas/sucursales.</p>
                <Tip>
                    El módulo DUX se accede desde <strong>Integraciones &gt; DUX ERP</strong>. Tiene dos sub-secciones:{" "}
                    <strong>Importar / Exportar</strong> (descrita aquí) y <strong>Deudas Clientes</strong> (ver sección dedicada).
                </Tip>
            </div>
        ),
    },
    {
        id: "dux-deudas",
        title: "Deudas Clientes (DUX)",
        icon: BanknotesIcon,
        summary: "Consulta de saldos pendientes de clientes con datos en vivo desde DUX ERP.",
        keywords: ["dux", "deudas", "clientes", "saldo", "factura", "nota credito", "cobranza"],
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    El módulo de <strong>Deudas Clientes</strong> consulta facturas, notas de crédito y notas de débito
                    pendientes de cobro directamente en <strong>DUX ERP</strong>. Permite ver el detalle por cliente,
                    los cobros aplicados a cada comprobante y exportar todo a Excel agrupado.
                </p>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Filtros disponibles:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>Fecha desde / hasta</strong>: Rango de comprobantes a consultar.</li>
                        <li><strong>Empresa y Sucursal(es)</strong>: Selector que carga las opciones desde DUX.</li>
                        <li><strong>Cliente</strong>: Filtro por nombre (opcional).</li>
                        <li><strong>Con cobro</strong>: Incluye o excluye comprobantes que ya tienen cobros parciales.</li>
                        <li><strong>Anuladas</strong>: Incluye o excluye comprobantes anulados.</li>
                    </ul>
                </div>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Vista de resultados:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>Resumen superior</strong>: Cantidad de facturas, notas de crédito y notas de débito devueltas.</li>
                        <li><strong>Comprobantes agrupados por cliente</strong>: cada tarjeta muestra CUIT, total adeudado, cobrado y saldo.</li>
                        <li><strong>Detalle de cada comprobante</strong>: tipo, letra, número, fecha, importes (gravado, IVA, exento, descuento), total, cobrado y saldo, vendedor, link a la factura.</li>
                        <li><strong>Líneas del comprobante</strong>: código de ítem, descripción, cantidad, precio unitario, % descuento y % IVA.</li>
                        <li><strong>Cobros aplicados</strong>: punto de venta, comprobante, personal, caja y movimientos (tipo de valor, referencia, monto).</li>
                        <li><strong>Facturas referenciadas</strong>: para notas de crédito o débito, lista las facturas que referencian.</li>
                    </ul>
                </div>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Procesamiento asíncrono:</p>
                    <p className="text-gray-600">
                        La consulta puede tardar varios minutos según el rango de fechas. El sistema muestra el progreso en
                        tiempo real (procesados / total, exitosos, errores) con la posibilidad de <strong>cancelar</strong>{" "}
                        en cualquier momento.
                    </p>
                </div>
                <Tip>
                    El botón <strong>Exportar a Excel</strong> genera un archivo agrupado por cliente con totales por grupo,
                    colores diferenciados (rojo para débito, verde para crédito, amarillo para notas de crédito parciales),
                    y resaltado de saldos vencidos a más de 30 días.
                </Tip>
                <Warning>
                    Las notas de crédito que referencian facturas <em>fuera</em> del rango consultado se marcan como{" "}
                    <strong>NC parcial</strong> y se exportan con un comentario en la celda. Tenelas en cuenta cuando armes
                    la cobranza para evitar saldos negativos engañosos.
                </Warning>
            </div>
        ),
    },
    {
        id: "usuarios-accesos",
        title: "Usuarios y Accesos",
        icon: UsersIcon,
        summary: "Administración de usuarios, roles, contraseñas y permisos disponibles.",
        keywords: ["usuarios", "roles", "permisos", "clave", "password"],
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    El módulo de <strong>Usuarios y Accesos</strong> permite administrar quién puede ingresar al sistema
                    y qué nivel de acceso tiene cada persona.
                </p>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Qué podés hacer en esta pantalla:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>Buscar usuarios</strong> por nombre o username.</li>
                        <li><strong>Crear usuarios</strong> nuevos con username, contraseña, nombre completo y rol.</li>
                        <li><strong>Editar usuarios</strong> existentes para cambiar nombre, rol o estado activo.</li>
                        <li><strong>Cambiar contraseña</strong> desde el botón <strong>Clave</strong>.</li>
                        <li><strong>Eliminar usuarios</strong> seleccionando una o varias filas.</li>
                    </ul>
                </div>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Columnas principales:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>Usuario</strong>: identificador de login.</li>
                        <li><strong>Nombre completo</strong>: nombre visible de la persona.</li>
                        <li><strong>Rol</strong>: se muestra como badge para identificar rápido el nivel de acceso.</li>
                        <li><strong>Estado</strong>: indica si el usuario está activo o inactivo.</li>
                        <li><strong>Permisos</strong>: cantidad total de permisos heredados por el rol.</li>
                    </ul>
                </div>
                <Tip>
                    Debajo de la tabla se muestra el bloque <strong>Roles y permisos disponibles</strong>, donde podés ver
                    qué permisos hereda cada rol (con badge de color según el tipo de rol). Hacé clic en <strong>Editar permisos</strong> en la tarjeta de cada rol
                    para modificar los permisos asignados directamente desde la interfaz.
                </Tip>
            </div>
        ),
    },
    {
        id: "auditoria",
        title: "Auditoría",
        icon: ClipboardDocumentIcon,
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    La pantalla de <strong>Auditoría</strong> muestra el historial global de cambios auditados del sistema.
                    Sirve para ver <strong>qué cambió</strong>, <strong>cuándo</strong>, <strong>quién lo hizo</strong>
                    y <strong>desde qué origen</strong> se realizó la modificación.
                </p>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Filtros disponibles:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>Búsqueda libre</strong>: Busca por código, campo, usuario o valores antes/después.</li>
                        <li><strong>Entidad</strong>: Permite acotar a productos, proveedores, clientes y demás módulos auditados.</li>
                        <li><strong>Usuario</strong>: Filtra por nombre completo o username.</li>
                        <li><strong>Acción</strong>: Alta, edición o baja.</li>
                        <li><strong>Campo</strong>: Se filtra con texto libre, útil porque cada entidad tiene muchos campos distintos.</li>
                        <li><strong>Origen</strong>: Formulario, edición inline, monitor de precios, API o sistema.</li>
                    </ul>
                </div>
                <Tip>
                    Cada fila muestra la fecha, la entidad, el código afectado, el usuario, la acción, el campo modificado,
                    el valor anterior, el valor nuevo y, cuando aplica, un botón para abrir directamente el registro relacionado.
                    Los cálculos de envío y comisión de MercadoLibre también quedan registrados.
                </Tip>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Operaciones auditadas en el dominio Canal-Concepto:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>CANAL_CONCEPTO</strong>: asignar, quitar y clonar la relación
                            canal ⇆ concepto (incluye edición desde &quot;Fórmula del Canal&quot;).
                            En clonaciones, el snapshot incluye <span className="font-mono text-xs">clonado_desde_canal</span>.</li>
                        <li><strong>CANAL_CONCEPTO_REGLA</strong>: alta, edición y baja de reglas de
                            excepción por canal-concepto. Las reglas copiadas también se auditan.</li>
                        <li><strong>CANAL_CONCEPTO_CUOTA</strong>: alta, edición y baja de planes de cuotas.</li>
                        <li><strong>CONCEPTO_CALCULO</strong>: edición de nombre, porcentaje, aplicaSobre y descripción.
                            El origen <em>INLINE</em> se usa para ediciones desde la tabla y desde la Fórmula del Canal.</li>
                    </ul>
                </div>
                <Warning>
                    La auditoría depende de que exista la tabla <strong>auditoria_cambios</strong> en la base de datos.
                    Si el historial aparece vacío pese a haber cambios recientes, revisá que la migración generalizada y la limpieza de tablas legacy se hayan aplicado correctamente.
                </Warning>
            </div>
        ),
    },
    {
        id: "configuracion-envios-ml",
        title: "Configuración Envíos ML",
        icon: Cog6ToothIcon,
        summary: "Parámetros globales de integración de MercadoLibre.",
        keywords: ["parametros", "mercadolibre", "token", "envio gratis", "tiers"],
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    La configuración de envíos ML se gestiona desde la pantalla de <strong>Operaciones ML</strong>, donde
                    se definen el umbral de envío gratis y los tiers de costo de envío por rango de PVP.
                </p>
                <Tip>
                    Estos valores impactan directamente en el cálculo iterativo del PVP de ML. Si el PVP está por debajo del umbral, se usa el costo fijo del tier correspondiente. Si está por encima, se consulta la API de ML.
                </Tip>
                <Warning>
                    Modificar estos parámetros afecta todos los cálculos de precios de MercadoLibre. Hacerlo con precaución.
                </Warning>
            </div>
        ),
    },
    {
        id: "automatizacion-precios",
        title: "Automatización de Precios KT",
        icon: BoltIcon,
        summary: "Sincronización de precios entre DUX, MercadoLibre y TiendaNube con 9 pasos configurables.",
        keywords: ["automatizacion", "sincronizar", "dux", "ml", "nube", "precios", "promociones", "envio"],
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    La <strong>Automatización de Precios KT</strong> es el proceso central que sincroniza precios
                    entre el sistema, DUX ERP, MercadoLibre y TiendaNube. Se ejecuta manualmente desde la UI
                    o automáticamente via n8n.
                </p>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Secciones de la página:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>Configuracion</strong>: Parámetros de canales, cuotas, listas DUX y porcentajes de promoción. Cada valor es editable haciendo clic directamente. Cada canal tiene un checkbox &quot;Sin IVA&quot; para enviar precios sin IVA a DUX.</li>
                        <li><strong>Topes de Promocion por MLA</strong>: Lista de MLAs con porcentaje máximo de descuento individual. Sobreescribe los porcentajes globales de Deal/Seller Campaign/Smart para ese MLA específico. Se buscan MLAs existentes con autocompletado.</li>
                        <li><strong>Pasos de Sincronizacion</strong>: 9 checkboxes que controlan qué pasos se ejecutan.</li>
                    </ul>
                </div>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Los 9 pasos (en orden de ejecución):</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>1. Importar costos de DUX</strong>: Descarga productos de DUX ERP y actualiza costos, IVA, proveedor y descripción. La importación es incremental (solo productos modificados desde la última ejecución).</li>
                        <li><strong>2. Calcular precios de envío</strong>: Calcula el costo de envío solo para MLAs que aún no tienen precio de envío asignado.</li>
                        <li><strong>3. Quitar promociones en ML</strong>: Remueve items de todas las promociones activas en MercadoLibre antes de actualizar precios.</li>
                        <li><strong>4. Subir a DUX (Mercado Libre)</strong>: Sube los precios calculados a la lista de precios de ML en DUX.</li>
                        <li><strong>5. Subir a DUX (KT Gastro)</strong>: Sube precios de KT Gastro a DUX. Si &quot;Sin IVA&quot; está activado, quita el IVA del producto antes de subir.</li>
                        <li><strong>6. Subir a DUX (KT Hogar)</strong>: Sube precios de KT Hogar a DUX.</li>
                        <li><strong>7. Subir a Tienda Nube (KT Hogar)</strong>: Actualiza precios en TiendaNube solo para la tienda KT Hogar. Envía PVP inflado como precio tachado y PVP como precio real.</li>
                        <li><strong>8. Modificar precios en ML</strong>: Modifica los precios de las publicaciones en MercadoLibre (con variaciones si las tiene).</li>
                        <li><strong>9. Incluir en promociones ML</strong>: Agrega items a promociones DEAL, Seller Campaign y Smart, respetando topes individuales por MLA.</li>
                    </ul>
                </div>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Ejecución automática (n8n):</p>
                    <p className="text-gray-600">
                        El endpoint <code className="bg-gray-100 px-1 rounded dark:bg-slate-700">POST /api/automatizacion-precios/ejecutar</code> ejecuta
                        los 9 pasos de forma sincrónica y retorna un JSON con los contadores de cada paso. No requiere body ni autenticación.
                    </p>
                </div>
                <Tip>
                    Todos los precios enviados a DUX se redondean sin decimales. Los precios se obtienen de la tabla de precios calculados por canal y cuotas.
                </Tip>
                <Warning>
                    Mientras se ejecuta la automatización, se bloquean los procesos de cálculo de envío, cálculo de comisión, importación DUX, obtención DUX y recálculo masivo de precios.
                </Warning>
            </div>
        ),
    },
    {
        id: "config-automatizacion",
        title: "Config. Automatización",
        icon: WrenchScrewdriverIcon,
        summary: "Parámetros clave-valor del sistema para procesos automáticos.",
        keywords: ["clave", "valor", "automatizacion", "jobs"],
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    La <strong>Config. Automatización</strong> es una tabla de parámetros clave-valor del sistema.
                    Cada registro tiene una clave única, un valor y una descripción.
                </p>
                <p>
                    Se usa para configurar tareas automáticas programadas y parámetros globales
                    (ej: frecuencia de actualización de precios, flags de habilitación de procesos).
                </p>
                <div className="flex flex-col gap-2">
                    <p className="font-semibold">Campos:</p>
                    <ul className="list-disc list-inside space-y-1 text-gray-600">
                        <li><strong>Clave</strong>: Identificador único del parámetro.</li>
                        <li><strong>Valor</strong>: Valor actual de la configuración.</li>
                        <li><strong>Descripción</strong>: Explicación de para qué sirve el parámetro.</li>
                    </ul>
                </div>
                <Warning>
                    Solo modificá estos valores si sabés exactamente qué hacen. Cambios incorrectos pueden afectar procesos automáticos del sistema.
                </Warning>
            </div>
        ),
    },
    {
        id: "login",
        title: "Inicio de Sesión",
        icon: UsersIcon,
        summary: "Pantalla de login con logo de SuperMaster.",
        keywords: ["login", "sesion", "acceso", "contraseña"],
        content: (
            <div className="flex flex-col gap-4">
                <p>
                    La pantalla de <strong>inicio de sesión</strong> muestra el logo de <strong>SuperMaster</strong> y
                    un formulario con usuario y contraseña. Al ingresar correctamente, el sistema redirige a la home.
                </p>
                <Tip>
                    Si olvidaste tu contraseña, pedile a un administrador que la restablezca desde el módulo de <strong>Usuarios y Accesos</strong>.
                </Tip>
            </div>
        ),
    },
];

const manualOrder = [
    "login",
    "navegacion",
    "estadisticas",
    "productos",
    "crear-producto",
    "proveedores",
    "clientes",
    "catalogos",
    "mlas",
    "referencias",
    "canales",
    "monitor-precios",
    "reglas-canal",
    "cuotas-canal",
    "reglas-excepcion",
    "reglas-descuento",
    "precios-inflados",
    "ordenes-compra",
    "reposicion",
    "operaciones-ml",
    "automatizacion-precios",
    "excel",
    "catalogos-pdf",
    "dux",
    "dux-deudas",
    "auditoria",
    "usuarios-accesos",
    "configuracion-envios-ml",
    "config-automatizacion",
] as const;

const manualOrderIndex = new Map<string, number>(manualOrder.map((id, index) => [id, index]));

// ── Contenido exportable (sin <main>, para embeber en otras páginas) ──────────

export function ManualContent() {
    const [search, setSearch] = useState("");
    const normalizedSearch = search.trim().toLowerCase();
    const visibleSections = useMemo(() => {
        const filtered = !normalizedSearch ? sections : sections.filter((section) => {
            const haystack = [
                section.title,
                section.id,
                section.summary || "",
                ...(section.keywords || []),
            ].join(" ").toLowerCase();
            return haystack.includes(normalizedSearch);
        });
        return [...filtered].sort((a, b) => {
            const aIndex = manualOrderIndex.get(a.id) ?? Number.MAX_SAFE_INTEGER;
            const bIndex = manualOrderIndex.get(b.id) ?? Number.MAX_SAFE_INTEGER;
            return aIndex - bIndex;
        });
    }, [normalizedSearch]);

    return (
        <div className="flex w-full min-w-0 flex-col gap-6">
            <section className="w-full rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
                <div className="flex w-full min-w-0 flex-col gap-4">
                    <div>
                        <p className="mb-1 text-xs font-bold uppercase tracking-widest text-gray-400 dark:text-slate-500">Manual</p>
                        <p className="text-sm text-gray-600 dark:text-slate-400">Buscá módulos, procesos y temas frecuentes.</p>
                    </div>
                    <label className="relative block max-w-xl">
                        <MagnifyingGlassIcon className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400 dark:text-slate-500" />
                        <input
                            type="text"
                            value={search}
                            onChange={(e) => setSearch(e.target.value)}
                            placeholder="Buscar en el manual..."
                            className="w-full rounded-lg border border-gray-200 bg-gray-50 py-2 pl-9 pr-3 text-sm text-gray-700 outline-none transition focus:border-blue-300 focus:bg-white focus:ring-2 focus:ring-blue-100 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100 dark:focus:border-blue-600 dark:focus:bg-slate-800 dark:focus:ring-blue-500/20"
                        />
                    </label>
                    <div className="rounded-lg border border-gray-100 bg-gray-50 px-3 py-2 text-xs text-gray-500 dark:border-slate-700 dark:bg-slate-800/70 dark:text-slate-400">
                        {visibleSections.length} sección{visibleSections.length === 1 ? "" : "es"} visible{visibleSections.length === 1 ? "" : "s"}
                    </div>
                    <nav className="flex min-w-0 flex-wrap gap-2">
                        {visibleSections.map((s) => (
                            <a
                                key={s.id}
                                href={`#${s.id}`}
                                className="inline-flex items-center gap-2 rounded-lg border border-blue-100 bg-blue-50 px-3 py-2 text-sm text-blue-700 transition hover:border-blue-200 hover:bg-blue-100 hover:text-blue-900 dark:border-slate-700 dark:bg-slate-800 dark:text-blue-300 dark:hover:border-slate-600 dark:hover:bg-slate-700 dark:hover:text-blue-200"
                            >
                                <s.icon className="h-4 w-4 shrink-0" />
                                <span className="font-medium">{s.title}</span>
                            </a>
                        ))}
                    </nav>
                </div>
            </section>

            <div className="flex w-full min-w-0 flex-col gap-6">
                {visibleSections.length === 0 ? (
                    <div className="rounded-xl border border-dashed border-gray-300 bg-white px-6 py-10 text-center text-sm text-gray-500 shadow-sm dark:border-slate-700 dark:bg-slate-900 dark:text-slate-400">
                        No encontré secciones para <strong>{search}</strong>. Probá con el nombre del módulo o una palabra clave.
                    </div>
                ) : (
                    visibleSections.map((s) => (
                        <div key={s.id} id={s.id} className="scroll-mt-4">
                            <SectionCard title={s.title} icon={s.icon}>
                                {s.summary ? <p className="text-sm font-medium text-gray-500 dark:text-slate-400">{s.summary}</p> : null}
                                {s.content}
                            </SectionCard>
                        </div>
                    ))
                )}
            </div>
        </div>
    );
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function ManualPage() {
    return (
        <main className="w-full min-w-0 p-4 bg-gray-50 dark:bg-slate-950 flex flex-col gap-6">
            <div>
                <h1 className="text-3xl font-bold text-gray-800 dark:text-slate-100 flex items-center gap-2">
                    <BookOpenIcon className="w-8 h-8 text-gray-600 dark:text-slate-400" />
                    Manual de Usuario
                </h1>
                <p className="text-gray-500 dark:text-slate-400 text-sm mt-1">
                    Guía de uso de la aplicación Master Application.
                </p>
            </div>
            <ManualContent />
        </main>
    );
}
