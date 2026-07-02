"use client";
import React, { useState, useEffect, useCallback, useMemo, useRef } from "react";
import { toast } from "sonner";
import { notificar } from "../utils/notificar";
import { esSesionExpirada } from "../utils/fetchAPI";
import { BuildingStorefrontIcon, CheckIcon, CheckCircleIcon, CloudArrowDownIcon, CubeIcon, FireIcon, HomeIcon, XMarkIcon, IdentificationIcon, CurrencyDollarIcon, ArchiveBoxIcon, ReceiptPercentIcon, Squares2X2Icon, UserGroupIcon, ShoppingBagIcon, BanknotesIcon, InformationCircleIcon, SparklesIcon, TagIcon, PauseCircleIcon, EyeIcon, EyeSlashIcon, MinusCircleIcon } from "@heroicons/react/24/outline";
import Tooltip from "../components/Tooltip/Tooltip";
import { API_BASE_URL } from "../config/runtime";
import Button from "../components/Button/Button";
import Modal from "../components/Modal/Modal";
import AsyncSelect from "../components/AsyncSelect/AsyncSelect";
import {
    getSiguienteSkuAPI, existeSkuAPI, getMlaPorCodigoAPI, createMlaAPI, getMlaPorIdAPI, patchMlaAPI, type MlaDetalleDTO,
    searchMarcas, searchClasifGral, searchClasifGastro, searchTipos, searchProveedores, searchOrigenes, searchMateriales, searchMlas,
    searchCatalogos, searchAptos, searchSegmentos, searchCanales, addProductoCatalogoAPI, addProductoAptoAPI, addProductoSegmentoAPI,
    removeProductoCatalogoAPI, removeProductoAptoAPI, removeProductoSegmentoAPI, updateProductoAPI, getNombreById,
    exportarProductosADuxAPI, calcularEnvioMlaAPI, exportarProductosANubeAPI, exportarProductosAMlAPI, recalcularProductoAPI,
    generarSeoAPI, type DestinoNube, type SeoNube,
    searchSectoresDeposito, predecirCategoriasMlAPI, type PrediccionCategoriaMl,
    getImagenDetalleAPI, type ImagenDetalle,
    getMlCategoriaFichaAPI, getMlCategoriaAtributosAPI, type MlAtributoDef, type ProductoMlAtributo,
    type MlFicha, type MlComponente,
    getMlCategoriaMaxTitleAPI,
    putEstadoPublicacionAPI,
    getEstadoMlAPI, getEstadoHogarAPI, getEstadoGastroAPI, getEstadoDuxAPI, getFamiliaAPI, quitarDeFamiliaAPI, type FamiliaMl,
    type EstadoCanal, type EstadoPublicacionUpdate,
    type MlCanal, type NubeCanal, type DuxCanal,
    generarCaratulaAPI, guardarCaratulaAPI,
    getDescripcionSugeridaAPI,
    getCrudasAPI, crudaMiniaturaURL, type CrudasDisponibles,
    mlEditarURL,
    nubeEditarURL, NUBE_ADMIN_SUBDOMINIO,
} from "./productosService";
import { updateProductoMargenAPI } from "./productoMargenService";
import { getCuotasPorCanalAPI } from "../canal-concepto-cuotas/canalConceptoCuotaService";
import { getImagenConfigAPI, getSeoConfigAPI } from "../config-ia/seoService";
import ImagenesCarousel from "./ImagenesCarousel";
import {
    getProductoAptosAPI, getProductoCatalogosAPI, getProductoSegmentosAPI,
    getAllAptosAPI, getAllCatalogosAPI, getAllSegmentosAPI, asignarPrecioInfladoAPI,
} from "./productoSubRecursosService";
import MultiAsyncSelect, { type MultiOption } from "../components/MultiAsyncSelect/MultiAsyncSelect";
import { PreciosInfladosSection, type PrecioInfladoDraft } from "./PreciosInfladosSection";
import { HistorialSection } from "./HistorialSection";
import { ProductoCreateDTO, ProductoDTO, ProductoPatchDTO } from "./types";
import VariantesSection from "./variantes/VariantesSection";
import { VarianteBorrador, validarVariantes } from "./variantes/types";
import { formatFechaAR } from "../utils/formatDate";
import HtmlEditor from "./HtmlEditor";


type CanalExport = "Dux" | "Tienda Nube" | "Mercado Libre";
type ResultadoCanal = { canal: CanalExport; estado: "ok" | "error"; detalle: string; conAvisos?: boolean };

// Plural correcto: "1 creado" / "3 creados".
const plural = (n: number, sing: string, plur: string) => `${n} ${n === 1 ? sing : plur}`;
// Quita el prefijo redundante "{sku} / " de los mensajes del backend (al subir un solo producto el SKU sobra).
const sinPrefijoSku = (msgs: string[], sku: string) => { const pref = `${sku} / `; return msgs.map(m => (m.startsWith(pref) ? m.slice(pref.length) : m)); };

// Convierte el resultado de un export de canal en ok/error + detalle legible.
function clasificarExport(canal: CanalExport, r: { creados?: number; actualizados?: string[]; yaExistian?: string[]; errores: string[]; advertencias?: string[] }, sku: string): ResultadoCanal {
    if (r.errores.length) return { canal, estado: "error", detalle: sinPrefijoSku(r.errores, sku).join("; ") };
    const partes: string[] = [];
    if (r.creados) partes.push(plural(r.creados, "creado", "creados"));
    if (r.actualizados?.length) partes.push(plural(r.actualizados.length, "actualizado", "actualizados"));
    if (r.yaExistian?.length) partes.push(plural(r.yaExistian.length, "ya existía", "ya existían"));
    const conAvisos = !!r.advertencias?.length;
    if (conAvisos) partes.push(`⚠ ${sinPrefijoSku(r.advertencias!, sku).join("; ")}`);
    return { canal, estado: "ok", detalle: partes.join(" · ") || "sin cambios", conAvisos };
}

// Emite un toast por canal: warning si trae avisos (ej. "creado sin imagen"), success si salió limpio.
const notificarCanales = (resultados: ResultadoCanal[]) =>
    resultados.forEach(r => (r.conAvisos ? notificar.warning : notificar.success)(`${r.canal}: ${r.detalle}`));

// Spinner del botón de submit mientras se crea/edita el producto.
function SpinnerIcon() {
    return (
        <svg className="h-4 w-4 animate-spin" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
    );
}

// El selector manual de imagen se eliminó: las imágenes se resuelven por SKU y se ven con ImagenesCarousel.

// Etiqueta legible para una cuota de canal: usa la descripción configurada, o la deriva del número.
const etiquetaCuota = (cuotas: number, descripcion?: string) =>
    descripcion?.trim() || (cuotas < 0 ? "Transferencia" : cuotas === 0 ? "Contado" : `${cuotas} cuotas`);

type CuotaOpcion = { cuotas: number; descripcion: string };
// Opciones del select de cuotas: las reales del canal, asegurando que la cuota seleccionada
// aparezca aunque las opciones aún no hayan cargado (refleja la selección, sin inventar planes).
const opcionesConSeleccion = (opts: CuotaOpcion[], seleccion: number): CuotaOpcion[] =>
    opts.some(c => c.cuotas === seleccion) ? opts : [{ cuotas: seleccion, descripcion: etiquetaCuota(seleccion) }, ...opts];

// Etiqueta corta de cada sección de la ficha ML (evita repetir "Características de la…").
const SECCION_LABEL_ML: Record<string, string> = {
    VARIANTE: "Variante",
    PRINCIPALES: "Principales",
    SECUNDARIAS: "Secundarias",
};

// Valida un GTIN/EAN: longitud 8/12/13/14 + dígito verificador (módulo 10 de GS1). Igual que el
// backend: ML rechaza identificadores con formato inválido y, peor, eso bloquea todos los atributos.
const gtinValido = (codigo: string): boolean => {
    const s = codigo.trim();
    if (![8, 12, 13, 14].includes(s.length) || !/^\d+$/.test(s)) return false;
    let sum = 0;
    for (let i = 0; i < s.length - 1; i++) {
        const d = s.charCodeAt(s.length - 2 - i) - 48;
        sum += i % 2 === 0 ? d * 3 : d;
    }
    return (10 - (sum % 10)) % 10 === s.charCodeAt(s.length - 1) - 48;
};

const EXT_ML = new Set(["jpg", "jpeg", "png"]);
const EXT_NUBE = new Set(["gif", "jpg", "jpeg", "png", "webp"]);
const MAX_BYTES_IMG = 10 * 1024 * 1024; // 10 MB
const BULLET_COTIZAR = "<ul><li>ENVIO A COTIZAR</li></ul>";

// Resalta en negrita el último segmento (la categoría hoja) de un path "A > B > C".
function pathConHojaResaltada(path: string) {
    const partes = path.split(/\s*>\s*/);
    const hoja = partes.pop() ?? path;
    const prefijo = partes.length ? partes.join(" > ") + " > " : "";
    return <>{prefijo}<span className="font-semibold">{hoja}</span></>;
}

type ProductoFormModalProps = {
    producto: ProductoDTO | null;            // null = crear; ProductoDTO = editar
    canExportarDux: boolean;
    createProducto: (data: ProductoCreateDTO, afterCreate?: (id: number) => Promise<void>) => Promise<ProductoDTO>;
    onClose: () => void;
    onSuccess: () => void | Promise<void>;
    /** Abre el modal para editar OTRO producto (usado por el link "Editar" de una variante hermana). */
    onEditarOtro?: (productoId: number) => void;
};

export default function ProductoFormModal({ producto, canExportarDux, createProducto, onClose, onSuccess, onEditarOtro }: ProductoFormModalProps) {
    const [isSaving, setIsSaving] = useState(false);

    // --- Campos del Formulario ---
    const [sku, setSku] = useState("");
    // Último SKU autocompletado: nos deja saber si el usuario lo editó a mano
    // (en cuyo caso no lo pisamos al cambiar "Es Combo").
    const [lastSuggestedSku, setLastSuggestedSku] = useState("");
    // Aviso en vivo: true si el SKU tipeado ya pertenece a otro producto.
    const [skuYaExiste, setSkuYaExiste] = useState(false);
    const [codExt, setCodExt] = useState("");
    const [tituloDux, setTituloDux] = useState("");
    const [tituloMl, setTituloMl] = useState("");
    const [mlCategoryId, setMlCategoryId] = useState<string | null>(null);
    const [mlCategoryNombre, setMlCategoryNombre] = useState<string | null>(null);
    const [prediccionesMl, setPrediccionesMl] = useState<PrediccionCategoriaMl[]>([]);
    const [cargandoPrediccionesMl, setCargandoPrediccionesMl] = useState(false);
    const [tituloNube, setTituloNube] = useState("");
    // Título override por canal Nube. En EDICIÓN se lee de la API de cada store; en ALTA se espeja desde el título base.
    const [tituloHogar, setTituloHogar] = useState("");
    const [tituloGastro, setTituloGastro] = useState("");
    const [descripcionMl, setDescripcionMl] = useState("");
    const [descripcionHogar, setDescripcionHogar] = useState("");
    const [descripcionGastro, setDescripcionGastro] = useState("");
    const [sugiriendoDesc, setSugiriendoDesc] = useState(false);
    const [esCombo, setEsCombo] = useState(false);
    const [esComboOriginal, setEsComboOriginal] = useState(false);
    const [subirADux, setSubirADux] = useState(true);
    const [subirKtHogar, setSubirKtHogar] = useState(true);
    const [subirKtGastro, setSubirKtGastro] = useState(true);
    const [subirMl, setSubirMl] = useState(true);
    const [imagenesDetectadas, setImagenesDetectadas] = useState<ImagenDetalle[]>([]);
    const [cuotaHogar, setCuotaHogar] = useState<number>(-1);
    const [cuotaGastro, setCuotaGastro] = useState<number>(6);
    const [cuotasMlOpts, setCuotasMlOpts] = useState<CuotaOpcion[]>([]);
    const [cuotasHogarOpts, setCuotasHogarOpts] = useState<CuotaOpcion[]>([]);
    const [cuotasGastroOpts, setCuotasGastroOpts] = useState<CuotaOpcion[]>([]);
    const [cuotaMl, setCuotaMl] = useState<number>(0);
    // SEO de Tienda Nube por canal. En ALTA arranca vacío; en EDICIÓN se pre-carga desde el canal si existe.
    // No se persiste en la BD del sistema: si el usuario genera/edita estos campos, se usan al exportar; si quedan vacíos, se autogeneran.
    const [seoHogar, setSeoHogar] = useState<{ title: string; description: string; tags: string }>({ title: "", description: "", tags: "" });
    const [seoGastro, setSeoGastro] = useState<{ title: string; description: string; tags: string }>({ title: "", description: "", tags: "" });
    // Paquete de envío por canal Nube (peso/dimensiones). Cada store trae los suyos al editar y recibe los suyos al exportar.
    const [hogarPeso, setHogarPeso] = useState("0.050");
    const [hogarProfundidad, setHogarProfundidad] = useState("8.00");
    const [hogarAncho, setHogarAncho] = useState("5.00");
    const [hogarAlto, setHogarAlto] = useState("5.00");
    const [gastroPeso, setGastroPeso] = useState("0.050");
    const [gastroProfundidad, setGastroProfundidad] = useState("8.00");
    const [gastroAncho, setGastroAncho] = useState("5.00");
    const [gastroAlto, setGastroAlto] = useState("5.00");
    // Canales cuyo SEO se está generando ahora. Es un Set para permitir generar HOGAR y GASTRO
    // en paralelo: cada botón se deshabilita solo por su propio canal, no por el del otro.
    const [generandoSeo, setGenerandoSeo] = useState<Set<"GASTRO" | "HOGAR">>(() => new Set());
    // Resultado de las subidas a canales (para el panel de estado + reintento).
    const [resultadosCanal, setResultadosCanal] = useState<ResultadoCanal[]>([]);
    const [skuSubida, setSkuSubida] = useState("");
    const [reintentando, setReintentando] = useState(false);
    const [uxb, setUxb] = useState(1);
    const [activo, setActivo] = useState(true);
    const [carouselSku, setCarouselSku] = useState<string | null>(null);
    const [capacidad, setCapacidad] = useState("");
    const [largo, setLargo] = useState("");
    const [ancho, setAncho] = useState("");
    const [alto, setAlto] = useState("");
    const [diamboca, setDiamboca] = useState("");
    const [diambase, setDiambase] = useState("");
    const [espesor, setEspesor] = useState("");
    const [costo, setCosto] = useState<number | "">("");
    const [iva, setIva] = useState(21.0);
    const [marcaId, setMarcaId] = useState<number | null>(null);
    const [origenId, setOrigenId] = useState<number | null>(null);
    const [clasifGralId, setClasifGralId] = useState<number | null>(null);
    const [clasifGastroId, setClasifGastroId] = useState<number | null>(null);
    const [tipoId, setTipoId] = useState<number | null>(null);
    const [proveedorId, setProveedorId] = useState<number | null>(null);
    const [materialId, setMaterialId] = useState<number | null>(null);
    const [sectorDepositoId, setSectorDepositoId] = useState<number | null>(null);
    // Nombres a mostrar en los AsyncSelect de relación simple (necesario para
    // precargar el valor en modo edición; el AsyncSelect no resuelve nombre por id).
    const [marcaDisplay, setMarcaDisplay] = useState("");
    const [origenDisplay, setOrigenDisplay] = useState("");
    const [clasifGralDisplay, setClasifGralDisplay] = useState("");
    const [clasifGastroDisplay, setClasifGastroDisplay] = useState("");
    const [tipoDisplay, setTipoDisplay] = useState("");
    const [proveedorDisplay, setProveedorDisplay] = useState("");
    const [materialDisplay, setMaterialDisplay] = useState("");
    const [sectorDepositoDisplay, setSectorDepositoDisplay] = useState("");
    // Sección MLA unificada: un único código editable + datos del MLA (MLAU/envío/comisión/tope).
    // mlaId != null ⇒ el código matchea un MLA existente en la BD; null con código ⇒ se creará al guardar.
    const [mlaId, setMlaId] = useState<number | null>(null);
    // Snapshot del MLA cargado (para mostrar fechas de cálculo y detectar ediciones a persistir).
    const [mlaDetalle, setMlaDetalle] = useState<MlaDetalleDTO | null>(null);
    const [mlaCodigo, setMlaCodigo] = useState("");
    const [mlaMlau, setMlaMlau] = useState("");
    const [mlaPrecioEnvio, setMlaPrecioEnvio] = useState<number | "">("");
    const [mlaTope, setMlaTope] = useState<number | "">("");
    const [mlaComision, setMlaComision] = useState<number | "">("");
    const [obteniendoMla, setObteniendoMla] = useState(false);
    // Márgenes (se asocian tras crear el producto)
    const [margenMinorista, setMargenMinorista] = useState<number | "">("");
    const [margenMayorista, setMargenMayorista] = useState<number | "">("");
    const [mlPaqAlto, setMlPaqAlto] = useState<number | "">("");
    const [mlPaqAncho, setMlPaqAncho] = useState<number | "">("");
    const [mlPaqLargo, setMlPaqLargo] = useState<number | "">("");
    const [mlPaqPeso, setMlPaqPeso] = useState<number | "">("");
    const [ean, setEan] = useState("");
    // Ficha técnica de la categoría ML (secciones → componentes → atributos).
    const [mlFicha, setMlFicha] = useState<MlFicha | null>(null);
    // Lista plana de definiciones de atributos (derivada de la ficha) para validar required.
    const mlAtributosDef = useMemo<MlAtributoDef[]>(
        () => mlFicha?.secciones.flatMap(s => s.componentes.flatMap(c => c.atributos)) ?? [],
        [mlFicha]);
    // Ids de atributos presentes en la ficha (para la sincronización con dimensiones físicas).
    const fichaAttrIds = useMemo(() => new Set(mlAtributosDef.map(d => d.id)), [mlAtributosDef]);
    // Def de cada atributo de la ficha por id (para leer las unidades que ML declara por categoría).
    const fichaAttrById = useMemo(() => {
        const m = new Map<string, MlAtributoDef>();
        for (const d of mlAtributosDef) m.set(d.id, d);
        return m;
    }, [mlAtributosDef]);
    // EQUIPAMIENTO: algún nodo de la clasificación de Nube (gastro si existe, sino general) es "EQUIPAMIENTO".
    const esEquipamiento = useMemo(() => {
        const ruta = clasifGastroDisplay || clasifGralDisplay || "";
        return ruta.split(">").some(seg => seg.trim().toUpperCase() === "EQUIPAMIENTO");
    }, [clasifGastroDisplay, clasifGralDisplay]);
    const [mlAtributosVal, setMlAtributosVal] = useState<Record<string, ProductoMlAtributo>>({});
    // Límite de caracteres del Título ML según la categoría seleccionada (null = sin categoría).
    const [maxTitleLengthMl, setMaxTitleLengthMl] = useState<number | null>(null);
    // Relaciones N-a-N (se asocian tras crear el producto)
    const [catalogosSel, setCatalogosSel] = useState<MultiOption[]>([]);
    const [aptosSel, setAptosSel] = useState<MultiOption[]>([]);
    const [segmentosSel, setSegmentosSel] = useState<MultiOption[]>([]);
    // Precios inflados a asignar tras crear el producto (solo modo alta).
    const [preciosInfladosSel, setPreciosInfladosSel] = useState<PrecioInfladoDraft[]>([]);
    // null = modo crear; con id = modo editar (mismo modal/form).
    const [editandoProductoId, setEditandoProductoId] = useState<number | null>(null);

    // ===== Variantes (Fase 2a: solo al crear) =====
    const [tieneVariantes, setTieneVariantes] = useState(false);
    const [ejeAtributoId, setEjeAtributoId] = useState("");
    const [ejeValorBase, setEjeValorBase] = useState("");
    const [ejeValorBaseId, setEjeValorBaseId] = useState<string | null>(null);
    const [variantesBorrador, setVariantesBorrador] = useState<VarianteBorrador[]>([]);
    // 2b-2: agregar una variante a una familia existente (modo edición).
    const [agregandoVariante, setAgregandoVariante] = useState(false);
    const [nvSku, setNvSku] = useState("");
    const [nvEjeValorId, setNvEjeValorId] = useState<string | null>(null);
    const [nvEjeValorNombre, setNvEjeValorNombre] = useState("");
    const [nvStock, setNvStock] = useState<number | "">(0);
    const [nvEan, setNvEan] = useState("");
    // Atributos de la categoría ML (fuente confiable de allowVariations para el eje).
    const [ejeAtributosCat, setEjeAtributosCat] = useState<MlAtributoDef[]>([]);
    const ejeOpciones = useMemo(
        () => ejeAtributosCat.filter(d => d.allowVariations).map(d => ({
            id: d.id, name: d.name, values: (d.values ?? []).map(x => ({ id: x.id as string | null, name: x.name })),
        })),
        [ejeAtributosCat]);
    // Trae los atributos de categoría para el eje: al crear con variantes, o al agregar una a una familia.
    useEffect(() => {
        const activo = (!editandoProductoId && tieneVariantes) || (!!editandoProductoId && agregandoVariante);
        if (!activo || !mlCategoryId) { setEjeAtributosCat([]); return; }
        let cancelado = false;
        getMlCategoriaAtributosAPI(mlCategoryId)
            .then(defs => { if (!cancelado) setEjeAtributosCat(defs); })
            .catch(() => { if (!cancelado) setEjeAtributosCat([]); });
        return () => { cancelado = true; };
    }, [tieneVariantes, agregandoVariante, mlCategoryId, editandoProductoId]);
    // Resultados de la creación por variante (para el panel del footer).
    const [resultadosVariantes, setResultadosVariantes] = useState<{ sku: string; resultados: ResultadoCanal[] }[]>([]);
    // Familia de variantes del producto (solo edición; 2b-1: lista read-only desde la BD por family_id).
    const [familia, setFamilia] = useState<FamiliaMl | null>(null);
    const [quitandoId, setQuitandoId] = useState<number | null>(null); // 2b-3: variante en confirmación de quitar
    useEffect(() => {
        if (!editandoProductoId) { setFamilia(null); return; }
        let cancel = false;
        getFamiliaAPI(editandoProductoId).then(f => { if (!cancel) setFamilia(f); }).catch(() => { if (!cancel) setFamilia(null); });
        return () => { cancel = true; };
    }, [editandoProductoId]);
    // Cache de "¿el SKU tiene imagen válida para ML?" por cada SKU de variante consultado.
    const imagenesPorSkuCache = useRef<Map<string, boolean>>(new Map());

    // Tab activo del panel en modo edición: form de datos o historial de cambios.
    const [panelTab, setPanelTab] = useState<"datos" | "historial">("datos");
    // Snapshot de N-a-N al abrir en edición, para calcular el diff al guardar.
    const [catalogosOriginal, setCatalogosOriginal] = useState<MultiOption[]>([]);
    const [aptosOriginal, setAptosOriginal] = useState<MultiOption[]>([]);
    const [segmentosOriginal, setSegmentosOriginal] = useState<MultiOption[]>([]);
    const [moq, setMoq] = useState<number | "">("");
    const [stock, setStock] = useState<number | "">(0);
    const [tagReposicion, setTagReposicion] = useState<"" | "PRIO" | "LIQ">("");
    const [tag, setTag] = useState<"" | "MAQUINA" | "REPUESTO" | "MENAJE" | "INSUMO">("");
    const [formErrors, setFormErrors] = useState<Record<string, string>>({});
    const [cargandoMl, setCargandoMl] = useState(false);
    const [cargandoHogar, setCargandoHogar] = useState(false);
    const [cargandoGastro, setCargandoGastro] = useState(false);
    const [cargandoDux, setCargandoDux] = useState(false);
    const [estadoMl, setEstadoMl] = useState<MlCanal | null>(null);
    const [estadoHogar, setEstadoHogar] = useState<NubeCanal | null>(null);
    const [estadoGastro, setEstadoGastro] = useState<NubeCanal | null>(null);
    const [estadoDux, setEstadoDux] = useState<DuxCanal | null>(null);
    // Snapshots originales para detectar cambios de estado al guardar (PUT estado).
    const [estadoMlOriginal, setEstadoMlOriginal] = useState<MlCanal | null>(null);
    const [estadoHogarOriginal, setEstadoHogarOriginal] = useState<NubeCanal | null>(null);
    const [estadoGastroOriginal, setEstadoGastroOriginal] = useState<NubeCanal | null>(null);
    // Código MLA real resuelto por SKU contra ML (desde los .then de getEstadoMlAPI/getEstadoHogarAPI/getEstadoGastroAPI/getEstadoDuxAPI). Para verificar el MLA guardado.
    const [mlaResuelto, setMlaResuelto] = useState<string | null>(null);
    // Verificación (solo informa, no cambia nada): compara el MLA guardado con la publicación vigente en ML.
    // `alerta` = el MLA guardado está en problema (sin publicación vigente / no coincide) → además se marca el input en rojo.
    const mlaVerif = useMemo<{ tone: "emerald" | "amber" | "slate"; text: string; alerta: boolean } | null>(() => {
        if (cargandoMl || !estadoMl) return null;        // todavía sin datos de ML
        const stored = mlaCodigo.trim();
        const resuelto = (mlaResuelto ?? "").trim();
        if (estadoMl.estado.error)
            return stored ? { tone: "slate", text: "No se pudo verificar contra Mercado Libre.", alerta: false } : null;
        if (!stored)
            return resuelto ? { tone: "amber", text: `⚠ Hay una publicación vigente en ML (${resuelto}) sin vincular en la base.`, alerta: false } : null;
        if (!resuelto)
            return { tone: "amber", text: "⚠ No hay publicación vigente en ML para este SKU; el MLA guardado podría estar obsoleto.", alerta: true };
        if (resuelto === stored)
            return { tone: "emerald", text: "✓ Verificado en ML: coincide con la publicación vigente.", alerta: false };
        return { tone: "amber", text: `⚠ El MLA guardado no coincide con la publicación vigente en ML (${resuelto}).`, alerta: true };
    }, [cargandoMl, estadoMl, mlaResuelto, mlaCodigo]);

    // EQUIPAMIENTO + KT GASTRO: la Descripción · KT GASTRO siempre incluye el bullet "ENVIO A COTIZAR"
    // (espejo de lo que el backend agrega al publicar, idempotente). Se quita si deja de aplicar.
    // descripcionGastro va en deps a propósito: reasegura el bullet tras editar/componer (es obligatorio para EQUIPAMIENTO+GASTRO).
    useEffect(() => {
        if (cargandoGastro) return; // esperar a que cargue la descripción del canal
        const debe = esEquipamiento && subirKtGastro;
        setDescripcionGastro(prev => {
            const tiene = prev.includes("ENVIO A COTIZAR");
            if (debe && !tiene) return prev + BULLET_COTIZAR;
            if (!debe && tiene) return prev.replaceAll(BULLET_COTIZAR, "");
            return prev;
        });
    }, [esEquipamiento, subirKtGastro, cargandoGastro, descripcionGastro]);
    const [caratulaPreview, setCaratulaPreview] = useState<string | null>(null);
    const [caratulaFormato, setCaratulaFormato] = useState<string>("jpeg");
    const [caratulaCruda, setCaratulaCruda] = useState<string | null>(null);
    const [caratulaCrudaFormato, setCaratulaCrudaFormato] = useState<string>("jpeg");
    const [generandoCaratula, setGenerandoCaratula] = useState(false);
    const [guardandoCaratula, setGuardandoCaratula] = useState(false);
    const [caratulaCacheBust, setCaratulaCacheBust] = useState(0);
    const [selectorCaratulaAbierto, setSelectorCaratulaAbierto] = useState(false);
    const [crudasDisp, setCrudasDisp] = useState<CrudasDisponibles | null>(null);
    const [crudaElegida, setCrudaElegida] = useState<string | null>(null);
    const [faseCaratula, setFaseCaratula] = useState("");
    const [duracionCaratula, setDuracionCaratula] = useState<number | null>(null);
    const [transcurridoCaratula, setTranscurridoCaratula] = useState(0); // ms transcurridos en vivo mientras genera
    const [modelImagen, setModelImagen] = useState<string>("");
    const [modelSeo, setModelSeo] = useState<string>("");

    // Indica si el producto ya está publicado en Mercado Libre (se usa en validación y UI).
    const mlPublicado = !!(estadoMl?.estado?.publicado || mlaResuelto);

    // Carga las cuotas reales de cada canal (KT HOGAR / KT GASTRO / ML) para poblar los
    // selectores. Si un canal no se encuentra o no tiene cuotas, su select queda solo con la
    // cuota seleccionada (no se inventan planes).
    useEffect(() => {
        let cancelado = false;
        const cargarCuotasCanal = async (nombre: string): Promise<CuotaOpcion[]> => {
            try {
                const canales = await searchCanales(nombre, 50);
                const canal = canales.find(c => (c.nombreCorto ?? c.label).toUpperCase() === nombre.toUpperCase());
                if (!canal) return [];
                const cuotas = await getCuotasPorCanalAPI(canal.id);
                return cuotas
                    .slice()
                    .sort((a, b) => a.cuotas - b.cuotas)
                    .map(c => ({ cuotas: c.cuotas, descripcion: etiquetaCuota(c.cuotas, c.descripcion) }));
            } catch {
                return [];
            }
        };
        void (async () => {
            const [hogar, gastro, ml] = await Promise.all([
                cargarCuotasCanal("KT HOGAR"),
                cargarCuotasCanal("KT GASTRO"),
                cargarCuotasCanal("ML"),
            ]);
            if (cancelado) return;
            setCuotasHogarOpts(hogar);
            setCuotasGastroOpts(gastro);
            setCuotasMlOpts(ml);
            // Respeta el default (Transferencia / 6 cuotas) si existe en el canal; si no, la primera.
            if (hogar.length && !hogar.some(c => c.cuotas === -1)) setCuotaHogar(hogar[0].cuotas);
            if (gastro.length && !gastro.some(c => c.cuotas === 6)) setCuotaGastro(gastro[0].cuotas);
            // Default ML: CONTADO (0). El canal ML trae la cuota 0; si no, cae a la primera opción.
            if (ml.length && !ml.some(c => c.cuotas === 0)) setCuotaMl(ml[0].cuotas);
        })();
        return () => { cancelado = true; };
    }, []);

    const validateForm = (): boolean => {
        const errors: Record<string, string> = {};
        if (!sku.trim()) errors.sku = "El SKU es obligatorio";
        else if (sku.trim().length > 45) errors.sku = "Máximo 45 caracteres";
        else if (!editandoProductoId && skuYaExiste) errors.sku = "Ya existe un producto con este SKU";
        if (!tituloDux.trim()) errors.tituloDux = "El Título Dux es obligatorio";
        else if (tituloDux.trim().length > 100) errors.tituloDux = "Máximo 100 caracteres";
        if (tituloMl.trim().length > 100) errors.tituloMl = "Máximo 100 caracteres";
        else if (subirMl && !tituloMl.trim()) errors.tituloMl = "Requerido para subir a Mercado Libre";
        if (!mlPublicado && tituloMl.trim() && !mlCategoryId) errors.mlCategory = "Si hay Título ML, predecí y elegí una categoría de Mercado Libre";
        if (tituloNube.trim().length > 100) errors.tituloNube = "Máximo 100 caracteres";
        else if ((subirKtHogar || subirKtGastro) && !tituloNube.trim()) errors.tituloNube = "Requerido para subir a Tienda Nube";
        if (costo === "" || Number(costo) <= 0) errors.costo = "El costo debe ser mayor a 0";
        if (uxb < 1) errors.uxb = "UxB debe ser al menos 1";
        if (!clasifGralId && !clasifGastroId) errors.clasificacion = "Seleccioná al menos una clasificación (general o gastronómica)";
        if (!tipoId) errors.tipoId = "El tipo es obligatorio";
        if (!sectorDepositoId) errors.sectorDeposito = "El sector de depósito es obligatorio";
        if (!esCombo) {
            if (!marcaId) errors.marcaId = "La marca es obligatoria";
            if (!origenId) errors.origenId = "El origen es obligatorio";
            // PRUEBA: proveedor no obligatorio temporalmente (descartar fallo en Dux)
            // if (!proveedorId) errors.proveedorId = "El proveedor es obligatorio";
            if (!materialId) errors.materialId = "El material es obligatorio";
            if (!tag) errors.tag = "El tag es obligatorio";
            const tieneMargen = (margenMinorista !== "" && Number(margenMinorista) > 0) || (margenMayorista !== "" && Number(margenMayorista) > 0);
            if (!tieneMargen) errors.margen = "Cargá al menos un margen (minorista o mayorista) mayor a 0";
        }
        if (largo.length > 45) errors.largo = "Máximo 45 caracteres";
        if (ancho.length > 45) errors.ancho = "Máximo 45 caracteres";
        if (alto.length > 45) errors.alto = "Máximo 45 caracteres";
        if (subirMl) {
            // Mercado Libre exige al menos una imagen válida (JPG/PNG, ≤10 MB). Tienda Nube sí admite sin imagen.
            const hayImagenMlValida = imagenesDetectadas.some(img => EXT_ML.has(img.extension) && img.bytes <= MAX_BYTES_IMG);
            if (!hayImagenMlValida) errors.imagenesMl = "Mercado Libre requiere al menos una imagen (JPG o PNG, hasta 10 MB). Seleccioná una imagen para el SKU.";
            if (mlPaqAlto === "" || Number(mlPaqAlto) <= 0) errors.mlPaqAlto = "Requerido para subir a ML";
            if (mlPaqAncho === "" || Number(mlPaqAncho) <= 0) errors.mlPaqAncho = "Requerido para subir a ML";
            if (mlPaqLargo === "" || Number(mlPaqLargo) <= 0) errors.mlPaqLargo = "Requerido para subir a ML";
            if (mlPaqPeso === "" || Number(mlPaqPeso) <= 0) errors.mlPaqPeso = "Requerido para subir a ML";
            // Atributos obligatorios de la categoría ML elegida (tag required/new_required): deben
            // completarse para subir a ML (el backend igual los exige; esto avisa antes de subir).
            const atributosMlFaltantes = mlAtributosDef
                .filter(d => d.required && !mlAtributosVal[d.id]?.valueName?.trim())
                .map(d => d.name);
            if (atributosMlFaltantes.length > 0) {
                errors.mlAtributos = `Completá los atributos obligatorios de Mercado Libre: ${atributosMlFaltantes.join(", ")}`;
            }
        }
        setFormErrors(errors);
        return Object.keys(errors).length === 0;
    };

    // Tras crear el producto: guarda los márgenes (si hay) y asocia catálogos,
    // aptos y clientes. Los errores acá no invalidan el producto ya creado.
    const asociarMargenYRelaciones = async (productoId: number) => {
        // Solo enviamos los márgenes cargados (omitimos los vacíos en vez de
        // mandarlos como null). Así se puede crear con uno solo: el backend
        // defaultea a 0 el margen ausente. Mandar null explícito daba error.
        const margenDto: { margenMinorista?: number; margenMayorista?: number } = {};
        if (margenMinorista !== "") margenDto.margenMinorista = margenMinorista;
        if (margenMayorista !== "") margenDto.margenMayorista = margenMayorista;
        if (Object.keys(margenDto).length > 0) {
            try {
                await updateProductoMargenAPI(productoId, margenDto);
            } catch {
                notificar.error("El producto se creó, pero falló al guardar los márgenes");
            }
        }
        try {
            await Promise.all([
                ...catalogosSel.map((c) => addProductoCatalogoAPI(productoId, Number(c.id))),
                ...aptosSel.map((a) => addProductoAptoAPI(productoId, Number(a.id))),
                ...segmentosSel.map((c) => addProductoSegmentoAPI(productoId, Number(c.id))),
            ]);
        } catch {
            notificar.error("El producto se creó, pero falló al asociar algún catálogo/apto/segmento");
        }
        if (preciosInfladosSel.length > 0) {
            try {
                await Promise.all(preciosInfladosSel.map((d) => asignarPrecioInfladoAPI(
                    productoId, d.canalId, d.precioInfladoId,
                    { fechaDesde: d.fechaDesde, fechaHasta: d.fechaHasta, observaciones: d.observaciones },
                )));
            } catch {
                notificar.error("El producto se creó, pero falló al asignar algún precio inflado");
            }
        }
    };

    // Canales marcados según los checkboxes (Nube agrupa HOGAR/GASTRO).
    const canalesMarcados = (): CanalExport[] => {
        const c: CanalExport[] = [];
        if (subirADux && canExportarDux) c.push("Dux");
        if ((subirKtHogar || subirKtGastro) && canExportarDux) c.push("Tienda Nube");
        if (subirMl && canExportarDux) c.push("Mercado Libre");
        return c;
    };

    // Ejecuta en paralelo los exports de los canales pedidos. Cada wrapper captura su error
    // y devuelve un ResultadoCanal — nunca rechaza, así un canal no corta a los demás.
    const ejecutarExportsCanales = async (
        skuExport: string, canales: CanalExport[],
        ov?: { cuotaMl: number; cuotaHogar: number; cuotaGastro: number; mlAtributos: ProductoMlAtributo[] },
    ): Promise<ResultadoCanal[]> => {
        // Valores efectivos: los del override (por variante) o los del producto base.
        const cuotaMlEf = ov?.cuotaMl ?? cuotaMl;
        const cuotaHogarEf = ov?.cuotaHogar ?? cuotaHogar;
        const cuotaGastroEf = ov?.cuotaGastro ?? cuotaGastro;
        const mlAtributosEf = ov?.mlAtributos ?? Object.values(mlAtributosVal);
        const tareas: Promise<ResultadoCanal>[] = [];
        if (canales.includes("Dux")) {
            tareas.push((async (): Promise<ResultadoCanal> => {
                // Dux confirma el alta de forma asíncrona (puede tardar). Mostramos un toast de
                // carga mientras se espera la respuesta del proceso y lo cerramos al terminar.
                const toastId = toast.loading("Subiendo a Dux… esperando confirmación del proceso (puede tardar)");
                try {
                    // En alta no hay panel (estadoDux = null) → "S"; en edición, el valor del panel.
                    const duxHabilitado: "S" | "N" = estadoDux?.estado.estado === "deshabilitado" ? "N" : "S";
                    const r = await exportarProductosADuxAPI([skuExport], duxHabilitado);
                    return clasificarExport("Dux", r, skuExport);
                } catch (e) {
                    return { canal: "Dux", estado: "error", detalle: e instanceof Error ? e.message : "error al subir" };
                } finally {
                    toast.dismiss(toastId);
                }
            })());
        }
        if (canales.includes("Tienda Nube")) {
            // Resuelve el SEO de un canal: usa el bloque editado/generado si tiene algo; si está
            // vacío, lo autogenera con IA. Si la autogeneración falla, sigue sin SEO (no aborta el export).
            const resolverSeo = async (
                seoBloque: { title: string; description: string; tags: string },
                canalSeo: "GASTRO" | "HOGAR",
            ): Promise<SeoNube | undefined> => {
                const existente = seoBloqueAPayload(seoBloque);
                if (existente) return existente;
                try {
                    return await generarSeoAPI(canalSeo, construirContextoSeo());
                } catch {
                    return undefined; // El export igual ocurre (el backend puede subir sin SEO).
                }
            };
            tareas.push((async (): Promise<ResultadoCanal> => {
                // Genera el SEO de ambas tiendas en paralelo (cada una puede llamar a OpenAI); solo
                // las marcadas lo resuelven, las demás quedan en undefined sin consultar. Recién con
                // ambos SEO listos se arma el payload y se sube, manteniendo el orden HOGAR, GASTRO.
                const [seoH, seoG] = await Promise.all([
                    subirKtHogar ? resolverSeo(seoHogar, "HOGAR") : Promise.resolve(undefined),
                    subirKtGastro ? resolverSeo(seoGastro, "GASTRO") : Promise.resolve(undefined),
                ]);
                const tiendas: DestinoNube[] = [];
                if (subirKtHogar) tiendas.push({ tienda: "KT HOGAR", cuotas: cuotaHogarEf, seo: seoH, descripcion: descripcionHogar.trim() || null, titulo: tituloHogar.trim() || null, peso: hogarPeso, profundidad: hogarProfundidad, ancho: hogarAncho, alto: hogarAlto });
                if (subirKtGastro) tiendas.push({ tienda: "KT GASTRO", cuotas: cuotaGastroEf, seo: seoG, descripcion: descripcionGastro.trim() || null, titulo: tituloGastro.trim() || null, peso: gastroPeso, profundidad: gastroProfundidad, ancho: gastroAncho, alto: gastroAlto });
                if (!tiendas.length) return { canal: "Tienda Nube", estado: "ok", detalle: "sin cambios" };
                try {
                    const r = await exportarProductosANubeAPI([skuExport], tiendas);
                    return clasificarExport("Tienda Nube", r, skuExport);
                } catch (e) {
                    return { canal: "Tienda Nube", estado: "error", detalle: e instanceof Error ? e.message : "error al subir" };
                }
            })());
        }
        if (canales.includes("Mercado Libre")) {
            tareas.push((async (): Promise<ResultadoCanal> => {
                try {
                    const r = await exportarProductosAMlAPI(
                        [skuExport], cuotaMlEf,
                        mlCategoryId, mlAtributosEf, descripcionMl.trim() || null, tituloMl.trim() || null);
                    return clasificarExport("Mercado Libre", r, skuExport);
                } catch (e) {
                    return { canal: "Mercado Libre", estado: "error", detalle: e instanceof Error ? e.message : "error al subir" };
                }
            })());
        }
        return Promise.all(tareas);
    };

    // ===== Variantes: helpers de guardado =====
    // ¿El SKU tiene al menos una imagen válida para ML? El base usa imagenesDetectadas; el resto, el cache.
    const imagenesDetectadasPorSku = (s: string) => s.trim() === sku.trim()
        ? imagenesDetectadas.some(i => EXT_ML.has(i.extension) && i.bytes <= MAX_BYTES_IMG)
        : (imagenesPorSkuCache.current.get(s.trim()) ?? false);
    const cargarImagenesVariantes = async (skus: string[]) => {
        await Promise.all(skus.map(async s => {
            const key = s.trim();
            if (!key || imagenesPorSkuCache.current.has(key)) return;
            try { const imgs = await getImagenDetalleAPI(key); imagenesPorSkuCache.current.set(key, imgs.some(i => EXT_ML.has(i.extension) && i.bytes <= MAX_BYTES_IMG)); }
            catch { imagenesPorSkuCache.current.set(key, false); }
        }));
    };
    // Payload de alta de una variante = clon del base con override de sku/stock/ean/mlaId.
    const construirPayloadVariante = (over: { sku: string; stock: number | ""; ean: string | null; mlaId: number | null }): ProductoCreateDTO => ({
        sku: over.sku, codExt, tituloDux: tituloDux.trim(), tituloNube: tituloNube.trim() || null, esCombo, uxb, activo,
        capacidad, largo: normalizarFisico("largo") || null, ancho: normalizarFisico("ancho") || null, alto: normalizarFisico("alto") || null,
        diamboca: normalizarFisico("diamboca") || null, diambase: normalizarFisico("diambase") || null, espesor: normalizarFisico("espesor") || null,
        costo: costo === "" ? 0 : costo, iva,
        stock: over.stock !== "" ? over.stock : null, moq: moq !== "" ? moq : null,
        tagReposicion: tagReposicion || null, tag: tag || null,
        marcaId, origenId, clasifGralId: clasifGralId!, clasifGastroId, tipoId: tipoId!, proveedorId, materialId, sectorDepositoId, mlaId: over.mlaId,
        mlPaqAlto: mlPaqAlto === "" ? null : Number(mlPaqAlto), mlPaqAncho: mlPaqAncho === "" ? null : Number(mlPaqAncho),
        mlPaqLargo: mlPaqLargo === "" ? null : Number(mlPaqLargo), mlPaqPeso: mlPaqPeso === "" ? null : Number(mlPaqPeso),
        ean: over.ean,
    });

    // Alta con variantes: crea N productos (base + hermanas) y publica cada uno con el MISMO
    // family_name (tituloMl compartido) + su atributo de eje → ML los agrupa en una familia.
    const handleCrearConVariantes = async () => {
        await cargarImagenesVariantes(variantesBorrador.map(v => v.sku));
        const errVar = validarVariantes({ sku, ejeValorNombre: ejeValorBase }, variantesBorrador, ejeAtributoId, subirMl, imagenesDetectadasPorSku);
        if (errVar) { notificar.error(errVar); return; }
        setIsSaving(true);
        try {
            // MLA del base (si el usuario tipeó uno); las hermanas crean su propio MLA al publicar.
            let mlaIdBase: number | null = null;
            try { const r = await resolverMlaParaGuardar(); mlaIdBase = r.mlaId; }
            catch (e) { if (!esSesionExpirada(e)) notificar.error(e instanceof Error ? e.message : "Error al guardar el MLA"); return; }
            const canales = canalesMarcados();
            const ejeAttr = (valorId: string | null, valorNombre: string): ProductoMlAtributo => ({ attributeId: ejeAtributoId, valueId: valorId, valueName: valorNombre.trim(), noAplica: false });
            const mlBase = Object.values(mlAtributosVal).filter(a => a.attributeId !== ejeAtributoId);
            const items = [
                { sku: sku.trim(), stock, ean: ean.trim() || null, mlaId: mlaIdBase, cuotaMl, cuotaHogar, cuotaGastro, mlAtributos: [...mlBase, ejeAttr(ejeValorBaseId, ejeValorBase)] },
                ...variantesBorrador.map(v => ({ sku: v.sku.trim(), stock: v.stock, ean: v.ean.trim() || null, mlaId: null as number | null, cuotaMl: v.cuotaMl, cuotaHogar: v.cuotaHogar, cuotaGastro: v.cuotaGastro, mlAtributos: [...mlBase, ejeAttr(v.ejeValorId, v.ejeValorNombre)] })),
            ];
            const acumulado: { sku: string; resultados: ResultadoCanal[] }[] = [];
            for (const it of items) {
                try {
                    const creado = await createProducto(construirPayloadVariante({ sku: it.sku, stock: it.stock, ean: it.ean, mlaId: it.mlaId }), asociarMargenYRelaciones);
                    if (creado?.id && (subirKtHogar || subirKtGastro) && canExportarDux) { try { await recalcularProductoAPI(creado.id); } catch { /* el export avisa */ } }
                    const rc = await ejecutarExportsCanales(it.sku, canales, { cuotaMl: it.cuotaMl, cuotaHogar: it.cuotaHogar, cuotaGastro: it.cuotaGastro, mlAtributos: it.mlAtributos });
                    acumulado.push({ sku: it.sku, resultados: rc });
                } catch (e) {
                    acumulado.push({ sku: it.sku, resultados: [{ canal: "Mercado Libre", estado: "error", detalle: e instanceof Error ? e.message : "no se pudo crear la variante" }] });
                }
            }
            setResultadosVariantes(acumulado);
            const huboError = acumulado.some(a => a.resultados.some(r => r.estado === "error"));
            if (!huboError) { notificar.success(`${items.length} variantes creadas`); onClose(); }
            else notificar.error("Algunas variantes fallaron; revisá el detalle abajo.");
        } finally { setIsSaving(false); }
    };

    // 2b-2: agrega una variante a la familia del producto que se está editando. Crea un producto nuevo
    // (clon del actual) y lo publica con el MISMO tituloMl (= family_name) + su atributo de eje → ML lo
    // une a la familia. Best-effort; refresca el panel de familia al terminar.
    const handleAgregarVariante = async () => {
        if (!ejeAtributoId) { notificar.error("Elegí el eje de variación."); return; }
        if (!nvSku.trim()) { notificar.error("Cargá el SKU de la variante."); return; }
        if (!nvEjeValorNombre.trim()) { notificar.error("Cargá el valor del eje de la variante."); return; }
        if (familia?.variantes.some(v => (v.sku ?? "").trim().toLowerCase() === nvSku.trim().toLowerCase())) {
            notificar.error("Ese SKU ya está en la familia."); return;
        }
        if (subirMl) {
            await cargarImagenesVariantes([nvSku]);
            if (!imagenesDetectadasPorSku(nvSku)) { notificar.error("Mercado Libre exige al menos una imagen para el SKU."); return; }
        }
        setIsSaving(true);
        try {
            const creado = await createProducto(construirPayloadVariante({ sku: nvSku.trim(), stock: nvStock, ean: nvEan.trim() || null, mlaId: null }), asociarMargenYRelaciones);
            if (creado?.id && (subirKtHogar || subirKtGastro) && canExportarDux) { try { await recalcularProductoAPI(creado.id); } catch { /* el export avisa */ } }
            const mlBase = Object.values(mlAtributosVal).filter(a => a.attributeId !== ejeAtributoId);
            const rc = await ejecutarExportsCanales(nvSku.trim(), canalesMarcados(), {
                cuotaMl, cuotaHogar, cuotaGastro,
                mlAtributos: [...mlBase, { attributeId: ejeAtributoId, valueId: nvEjeValorId, valueName: nvEjeValorNombre.trim(), noAplica: false }],
            });
            const errores = rc.filter(r => r.estado === "error");
            if (errores.length) notificar.error(`Variante creada con errores: ${errores.map(r => `${r.canal} — ${r.detalle}`).join("; ")}`);
            else notificar.success(`Variante ${nvSku.trim()} agregada a la familia`);
            if (editandoProductoId) { try { setFamilia(await getFamiliaAPI(editandoProductoId)); } catch { /* se refresca al reabrir */ } }
            setNvSku(""); setNvEjeValorId(null); setNvEjeValorNombre(""); setNvStock(0); setNvEan(""); setAgregandoVariante(false);
        } finally { setIsSaving(false); }
    };

    // 2b-3: quita una variante de la familia (pausa en ML + desasocia). Refresca el panel.
    const handleQuitarDeFamilia = async (productoId: number) => {
        setIsSaving(true);
        try {
            await quitarDeFamiliaAPI(productoId);
            notificar.success("Variante pausada y quitada de la familia");
            if (editandoProductoId) { try { setFamilia(await getFamiliaAPI(editandoProductoId)); } catch { /* se refresca al reabrir */ } }
        } catch (e) {
            if (!esSesionExpirada(e)) notificar.error(e instanceof Error ? e.message : "No se pudo quitar la variante");
        } finally { setIsSaving(false); setQuitandoId(null); }
    };

    const handleCreate = async () => {
        if (!validateForm()) return;
        if (tieneVariantes) { await handleCrearConVariantes(); return; }
        try {
            setIsSaving(true);
            const costoNum = costo === "" ? 0 : costo;
            // Resuelve el MLA a asociar: matchea/crea/persiste según el código cargado.
            let mlaIdFinal: number | null;
            let mlaFueCreado = false;
            try {
                const r = await resolverMlaParaGuardar();
                mlaIdFinal = r.mlaId;
                mlaFueCreado = r.fueCreado;
            } catch (e) {
                if (!esSesionExpirada(e)) notificar.error(e instanceof Error ? e.message : "Error al guardar el MLA");
                setIsSaving(false);
                return;
            }
            const payload: ProductoCreateDTO = {
                sku: sku.trim(), codExt, tituloDux: tituloDux.trim(), tituloNube: tituloNube.trim() || null, esCombo, uxb, activo,
                capacidad, largo: normalizarFisico("largo") || null, ancho: normalizarFisico("ancho") || null, alto: normalizarFisico("alto") || null,
                diamboca: normalizarFisico("diamboca") || null, diambase: normalizarFisico("diambase") || null, espesor: normalizarFisico("espesor") || null,
                costo: costoNum, iva,
                stock: stock !== "" ? stock : null,
                moq: moq !== "" ? moq : null,
                tagReposicion: tagReposicion || null,
                tag: tag || null,
                marcaId, origenId, clasifGralId: clasifGralId!, clasifGastroId, tipoId: tipoId!, proveedorId, materialId, sectorDepositoId, mlaId: mlaIdFinal,
                mlPaqAlto: mlPaqAlto === "" ? null : Number(mlPaqAlto),
                mlPaqAncho: mlPaqAncho === "" ? null : Number(mlPaqAncho),
                mlPaqLargo: mlPaqLargo === "" ? null : Number(mlPaqLargo),
                mlPaqPeso: mlPaqPeso === "" ? null : Number(mlPaqPeso),
                ean: ean.trim() || null,
            };
            const creado = await createProducto(payload, asociarMargenYRelaciones);
            // Si se creó un MLA nuevo SIN envío cargado a mano, calcular su precio de envío (el
            // cálculo necesita el producto). Se ESPERA (no fire-and-forget) para que el recálculo
            // que dispara no pise el PVP justo cuando se exporta a Nube. Para MLAs existentes o con
            // envío manual NO se recalcula (respeta el valor guardado/editado).
            if (mlaIdFinal && creado?.id && mlaFueCreado && mlaPrecioEnvio === "") {
                try { await calcularEnvioMlaAPI(creado.id); } catch { /* se recalcula luego desde ML */ }
            }
            // Recálculo SÍNCRONO del precio antes de exportar a Nube (que lee el PVP de
            // producto_canal_precios): cierra el race entre los recálculos async
            // (alta / margen / precio inflado / envío MLA) y el export.
            if (creado?.id && (subirKtHogar || subirKtGastro) && canExportarDux) {
                try { await recalcularProductoAPI(creado.id); } catch { /* el export avisará si falta el precio */ }
            }
            // Exportar a los canales marcados EN PARALELO. El producto ya está creado.
            const sk = sku.trim();
            setSkuSubida(sk);
            const resultados = await ejecutarExportsCanales(sk, canalesMarcados());
            setResultadosCanal(resultados);
            if (resultados.every(r => r.estado === "ok")) {
                notificar.success(`Producto ${sk} creado`);
                notificarCanales(resultados);
                onClose();
            } else {
                // Mantener el modal abierto con el panel de estado por canal.
                const fallidos = resultados.filter(r => r.estado === "error").map(r => `• ${r.canal} — ${r.detalle}`).join("\n");
                notificar.error(`El producto se creó, pero falló la subida:\n${fallidos}`);
            }
        } catch { /* hook already toasts */ } finally { setIsSaving(false); }
    };

    // Pide al backend el menor SKU libre del rango y lo carga en el form.
    // Si el rango está lleno (sku null) deja el campo vacío y avisa.
    const cargarSkuSugerido = useCallback(async (combo: boolean) => {
        try {
            const sugerido = await getSiguienteSkuAPI(combo);
            if (sugerido) {
                setSku(sugerido);
                setLastSuggestedSku(sugerido);
                setFormErrors((p) => ({ ...p, sku: "" }));
            } else {
                setLastSuggestedSku("");
                toast.warning(`No hay SKU libre en el rango ${combo ? "5000000–5999999" : "1000000–1999999"}. Cargalo manualmente.`);
            }
        } catch {
            // Silencioso: el usuario siempre puede tipear el SKU a mano.
        }
    }, []);

    // Precarga al montar: en edición copia los campos del producto; en alta sugiere el SKU.
    useEffect(() => {
        // Evita que una carga async vieja (p. ej. el doble efecto de StrictMode, o un re-open) pise
        // cambios que el usuario ya hizo (p. ej. el estado de un canal volvía al anterior "a veces").
        let cancelled = false;
        if (producto) {
            setEditandoProductoId(producto.id);
            setPanelTab("datos");
            setSku(producto.sku ?? "");
            setCodExt(producto.codExt ?? "");
            setTituloDux(producto.tituloDux ?? "");
            setTituloMl("");
            setPrediccionesMl([]);
            setTituloNube(producto.tituloNube ?? "");
            setEsCombo(!!producto.esCombo);
            setEsComboOriginal(!!producto.esCombo);
            setUxb(producto.uxb ?? 1);
            setActivo(!!producto.activo);
            setSubirADux(true);
            setSubirKtHogar(true); setSubirKtGastro(true); setSubirMl(true);
            setCapacidad(producto.capacidad ?? "");
            setLargo(producto.largo ?? ""); setAncho(producto.ancho ?? ""); setAlto(producto.alto ?? "");
            setDiamboca(producto.diamboca ?? ""); setDiambase(producto.diambase ?? ""); setEspesor(producto.espesor ?? "");
            setCosto(producto.costo ?? ""); setIva(producto.iva ?? 21);
            setStock(producto.stock ?? ""); setMoq(producto.moq ?? "");
            setTagReposicion((producto.tagReposicion as "" | "PRIO" | "LIQ") ?? "");
            setTag((producto.tag as "" | "MAQUINA" | "REPUESTO" | "MENAJE" | "INSUMO") ?? "");
            setMarcaId(producto.marcaId ?? null); setOrigenId(producto.origenId ?? null);
            setClasifGralId(producto.clasifGralId ?? null); setClasifGastroId(producto.clasifGastroId ?? null);
            setTipoId(producto.tipoId ?? null); setProveedorId(producto.proveedorId ?? null);
            setMaterialId(producto.materialId ?? null); setSectorDepositoId(producto.sectorDepositoId ?? null);
            setMlaId(producto.mlaId ?? null);
            // El código del MLA arranca con el del producto; el detalle (MLAU/envío/comisión)
            // lo completa el efecto que escucha mlaId.
            setMlaCodigo(producto.mlaId ? (producto.mlaNombre ?? "") : "");
            // Displays: marca/clasif/tipo traen *NombreCompleto.
            setMarcaDisplay(producto.marcaNombreCompleto ?? "");
            setClasifGralDisplay(producto.clasifGralNombreCompleto ?? "");
            setClasifGastroDisplay(producto.clasifGastroNombreCompleto ?? "");
            setTipoDisplay(producto.tipoNombreCompleto ?? "");
            // Origen/material/proveedor/sectorDeposito no traen nombre en el DTO: se
            // resuelven por id más abajo (el AsyncSelect no resuelve nombre por id).
            setOrigenDisplay(""); setMaterialDisplay(""); setProveedorDisplay(""); setSectorDepositoDisplay("");
            setMargenMinorista(producto.margenMinorista ?? ""); setMargenMayorista(producto.margenMayorista ?? "");
            setMlPaqAlto(producto.mlPaqAlto ?? "");
            setMlPaqAncho(producto.mlPaqAncho ?? "");
            setMlPaqLargo(producto.mlPaqLargo ?? "");
            setMlPaqPeso(producto.mlPaqPeso ?? "");
            setEan(producto.ean ?? "");
            // Atributos ML: se pre-cargan desde el canal en el .then de getEstadoMlAPI.
            setMlAtributosVal({});
            setFormErrors({});
            setCatalogosSel([]); setAptosSel([]); setSegmentosSel([]);
            setCatalogosOriginal([]); setAptosOriginal([]); setSegmentosOriginal([]);
            // El SEO de Nube no se persiste en la BD del sistema: en edición se pre-carga desde el canal (ver .then de getEstadoHogarAPI/getEstadoGastroAPI); en alta arranca vacío.
            setSeoHogar({ title: "", description: "", tags: "" });
            setSeoGastro({ title: "", description: "", tags: "" });

            // Nombres de origen/material/proveedor/sector (no vienen en el DTO de la tabla).
            if (producto.origenId) getNombreById("origenes", producto.origenId).then(r => setOrigenDisplay(r.nombre)).catch(() => {});
            if (producto.materialId) getNombreById("materiales", producto.materialId).then(r => setMaterialDisplay(r.nombre)).catch(() => {});
            if (producto.proveedorId) getNombreById("proveedores", producto.proveedorId).then(r => setProveedorDisplay(r.nombre)).catch(() => {});
            if (producto.sectorDepositoId) getNombreById("sectores-deposito", producto.sectorDepositoId, "codigo").then(r => setSectorDepositoDisplay(r.nombre)).catch(() => {});

            // Relaciones N-a-N: los GET dan ids; cruzamos con getAll* para los nombres.
            void (async () => {
                try {
                    const [aptosAsig, allAptos, catAsig, allCat, segAsig, allSeg] = await Promise.all([
                        getProductoAptosAPI(producto.id), getAllAptosAPI(),
                        getProductoCatalogosAPI(producto.id), getAllCatalogosAPI(),
                        getProductoSegmentosAPI(producto.id), getAllSegmentosAPI(),
                    ]);
                    const aptos: MultiOption[] = aptosAsig.map(a => ({ id: a.aptoId, label: allAptos.find(x => x.id === a.aptoId)?.nombre ?? String(a.aptoId) }));
                    const catalogos: MultiOption[] = catAsig.map(c => ({ id: c.catalogoId, label: allCat.find(x => x.id === c.catalogoId)?.nombre ?? String(c.catalogoId) }));
                    const segmentos: MultiOption[] = segAsig.map(c => ({ id: c.segmentoId, label: allSeg.find(x => x.id === c.segmentoId)?.nombre ?? String(c.segmentoId) }));
                    setAptosSel(aptos); setAptosOriginal(aptos);
                    setCatalogosSel(catalogos); setCatalogosOriginal(catalogos);
                    setSegmentosSel(segmentos); setSegmentosOriginal(segmentos);
                } catch (e) {
                    // Si la sesión expiró (401), fetchAPI ya redirige al login: no ensuciar con un toast.
                    if (!esSesionExpirada(e)) notificar.error("No se pudieron cargar catálogos/aptos/segmentos del producto");
                }
            })();

            getImagenConfigAPI().then(c => { if (!cancelled) setModelImagen(c.model); }).catch(() => {});
            getSeoConfigAPI().then(c => { if (!cancelled) setModelSeo(c.model); }).catch(() => {});

            setCargandoMl(true); setCargandoHogar(true); setCargandoGastro(true); setCargandoDux(true);
            setMlaResuelto(null);
            getEstadoMlAPI(producto.id).then(e => { if (cancelled) return; setEstadoMl(e); setEstadoMlOriginal(e);
                if (e.categoryId) { setMlCategoryId(e.categoryId); setMlCategoryNombre(e.categoryNombre); }
                if (e.atributos.length) { const m: Record<string, ProductoMlAtributo> = {}; for (const a of e.atributos) m[a.attributeId] = a; setMlAtributosVal(m); }
                setDescripcionMl(e.descripcion ?? "");
                setTituloMl(e.titulo ?? "");
                setMlaResuelto(e.mlaResuelto ?? null);
                if (e.mlPaqAlto != null) setMlPaqAlto(e.mlPaqAlto);
                if (e.mlPaqAncho != null) setMlPaqAncho(e.mlPaqAncho);
                if (e.mlPaqLargo != null) setMlPaqLargo(e.mlPaqLargo);
                if (e.mlPaqPeso != null) setMlPaqPeso(e.mlPaqPeso);
            }).catch(err => { if (!cancelled && !esSesionExpirada(err)) notificar.error("No se pudo leer Mercado Libre"); })
              .finally(() => { if (!cancelled) setCargandoMl(false); });
            getEstadoHogarAPI(producto.id).then(e => { if (cancelled) return; setEstadoHogar(e); setEstadoHogarOriginal(e);
                setDescripcionHogar(e.descripcion ?? "");
                if (e.seo) setSeoHogar({ title: e.seo.title ?? "", description: e.seo.description ?? "", tags: e.seo.tags ?? "" });
                if (e.titulo != null) setTituloHogar(e.titulo);
                if (e.peso != null) setHogarPeso(e.peso); if (e.profundidad != null) setHogarProfundidad(e.profundidad);
                if (e.ancho != null) setHogarAncho(e.ancho); if (e.alto != null) setHogarAlto(e.alto);
            }).catch(err => { if (!cancelled && !esSesionExpirada(err)) notificar.error("No se pudo leer KT HOGAR"); })
              .finally(() => { if (!cancelled) setCargandoHogar(false); });
            getEstadoGastroAPI(producto.id).then(e => { if (cancelled) return; setEstadoGastro(e); setEstadoGastroOriginal(e);
                setDescripcionGastro(e.descripcion ?? "");
                if (e.seo) setSeoGastro({ title: e.seo.title ?? "", description: e.seo.description ?? "", tags: e.seo.tags ?? "" });
                // Título y paquete de GASTRO se leen de su propia API (independientes de HOGAR).
                if (e.titulo != null) setTituloGastro(e.titulo);
                if (e.peso != null) setGastroPeso(e.peso); if (e.profundidad != null) setGastroProfundidad(e.profundidad);
                if (e.ancho != null) setGastroAncho(e.ancho); if (e.alto != null) setGastroAlto(e.alto);
            }).catch(err => { if (!cancelled && !esSesionExpirada(err)) notificar.error("No se pudo leer KT GASTRO"); })
              .finally(() => { if (!cancelled) setCargandoGastro(false); });
            getEstadoDuxAPI(producto.id).then(e => { if (cancelled) return; setEstadoDux(e); })
              .catch(err => { if (!cancelled && !esSesionExpirada(err)) notificar.error("No se pudo leer Dux"); })
              .finally(() => { if (!cancelled) setCargandoDux(false); });
            if (producto.sku) {
                getCrudasAPI(producto.sku).then(c => { if (!cancelled) setCrudasDisp(c); }).catch(() => { /* silencioso: el tooltip cae al fallback */ });
            }
        } else {
            setEditandoProductoId(null);
            setPanelTab("datos");
            setSeoHogar({ title: "", description: "", tags: "" });
            setSeoGastro({ title: "", description: "", tags: "" });
            setEan("");
            setDescripcionMl(""); setDescripcionHogar(""); setDescripcionGastro("");
            setMlAtributosVal({});
            setEsComboOriginal(false);
            setEstadoMl(null); setEstadoHogar(null); setEstadoGastro(null); setEstadoDux(null);
            setEstadoMlOriginal(null); setEstadoHogarOriginal(null); setEstadoGastroOriginal(null);
            setMlaResuelto(null);
            setTituloHogar(""); setTituloGastro("");
            setHogarPeso("0.050"); setHogarProfundidad("8.00"); setHogarAncho("5.00"); setHogarAlto("5.00");
            setGastroPeso("0.050"); setGastroProfundidad("8.00"); setGastroAncho("5.00"); setGastroAlto("5.00");
            void cargarSkuSugerido(false);
        }
        return () => { cancelled = true; };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // Guarda la edición: PATCH del producto + márgenes + diff de relaciones N-a-N.
    const handleGuardarEdicion = async () => {
        if (!validateForm() || editandoProductoId == null) return;
        try {
            setIsSaving(true);
            const id = editandoProductoId;
            const costoNum = costo === "" ? 0 : costo;
            // Resuelve el MLA a asociar: matchea/crea/persiste según el código cargado (misma
            // lógica que el alta — antes la edición no creaba el MLA tipeado a mano).
            let mlaIdFinal: number | null;
            let mlaFueCreado = false;
            try {
                const r = await resolverMlaParaGuardar();
                mlaIdFinal = r.mlaId;
                mlaFueCreado = r.fueCreado;
            } catch (e) {
                if (!esSesionExpirada(e)) notificar.error(e instanceof Error ? e.message : "Error al guardar el MLA");
                setIsSaving(false);
                return;
            }
            const patch = {
                codExt, tituloDux: tituloDux.trim(), tituloNube: tituloNube.trim() || null, esCombo, uxb, activo,
                capacidad, largo: normalizarFisico("largo") || null, ancho: normalizarFisico("ancho") || null, alto: normalizarFisico("alto") || null,
                diamboca: normalizarFisico("diamboca") || null, diambase: normalizarFisico("diambase") || null, espesor: normalizarFisico("espesor") || null,
                costo: costoNum, iva, stock: stock !== "" ? stock : null, moq: moq !== "" ? moq : null,
                tagReposicion: tagReposicion || null, tag: tag || null,
                marcaId, origenId, clasifGralId, clasifGastroId, tipoId, proveedorId, materialId, sectorDepositoId, mlaId: mlaIdFinal,
                mlPaqAlto: mlPaqAlto === "" ? null : Number(mlPaqAlto),
                mlPaqAncho: mlPaqAncho === "" ? null : Number(mlPaqAncho),
                mlPaqLargo: mlPaqLargo === "" ? null : Number(mlPaqLargo),
                mlPaqPeso: mlPaqPeso === "" ? null : Number(mlPaqPeso),
                ean: ean.trim() || null,
            } as ProductoPatchDTO;
            await updateProductoAPI(id, patch, "FORM");
            // Si se creó un MLA nuevo SIN envío cargado a mano durante la edición, calcularlo
            // (necesita el producto ya existente). MLAs existentes o con envío manual: no se toca.
            if (mlaIdFinal && mlaFueCreado && mlaPrecioEnvio === "") {
                try { await calcularEnvioMlaAPI(id); } catch { /* se recalcula luego desde ML */ }
            }

            // Solo enviamos los márgenes cargados (omitimos los vacíos en vez de
            // mandarlos como null). Para combos sin márgenes, omitir la llamada
            // evita el 400 que devuelve el backend al recibir null explícito.
            const margenDto: { margenMinorista?: number; margenMayorista?: number } = {};
            if (margenMinorista !== "") margenDto.margenMinorista = margenMinorista;
            if (margenMayorista !== "") margenDto.margenMayorista = margenMayorista;
            if (Object.keys(margenDto).length > 0) {
                try {
                    await updateProductoMargenAPI(id, margenDto);
                } catch {
                    notificar.error("El producto se actualizó, pero falló al guardar los márgenes");
                }
            }

            const diff = (orig: MultiOption[], curr: MultiOption[]) => {
                const oid = new Set(orig.map(o => Number(o.id)));
                const cid = new Set(curr.map(c => Number(c.id)));
                return {
                    add: curr.filter(c => !oid.has(Number(c.id))).map(c => Number(c.id)),
                    remove: orig.filter(o => !cid.has(Number(o.id))).map(o => Number(o.id)),
                };
            };
            const dCat = diff(catalogosOriginal, catalogosSel);
            const dApt = diff(aptosOriginal, aptosSel);
            const dSeg = diff(segmentosOriginal, segmentosSel);
            await Promise.all([
                ...dCat.add.map(x => addProductoCatalogoAPI(id, x)),
                ...dCat.remove.map(x => removeProductoCatalogoAPI(id, x)),
                ...dApt.add.map(x => addProductoAptoAPI(id, x)),
                ...dApt.remove.map(x => removeProductoAptoAPI(id, x)),
                ...dSeg.add.map(x => addProductoSegmentoAPI(id, x)),
                ...dSeg.remove.map(x => removeProductoSegmentoAPI(id, x)),
            ]);

            // Recálculo SÍNCRONO antes de exportar a Nube: si se cambió el costo, el PVP queda
            // obsoleto (recálculo async) y el export daría "precio desactualizado".
            if ((subirKtHogar || subirKtGastro) && canExportarDux) {
                try { await recalcularProductoAPI(id); } catch { /* el export avisará si falta el precio */ }
            }
            // Exportar a los canales marcados EN PARALELO (los cambios ya están guardados).
            const sk = sku.trim();
            setSkuSubida(sk);
            const resultados = await ejecutarExportsCanales(sk, canalesMarcados());
            setResultadosCanal(resultados);

            // Estado de publicación: aplicar solo lo que cambió respecto de lo leído al abrir.
            // Se deriva el estado "actual" desde los 4 estados independientes.
            {
                const upd: EstadoPublicacionUpdate = {};
                if (estadoMl?.estado.publicado && estadoMl.estado.estado !== estadoMlOriginal?.estado.estado) upd.ml = estadoMl.estado.estado;
                if (estadoHogar?.estado.publicado && estadoHogar.estado.estado !== estadoHogarOriginal?.estado.estado) upd.hogar = estadoHogar.estado.estado === "visible";
                if (estadoGastro?.estado.publicado && estadoGastro.estado.estado !== estadoGastroOriginal?.estado.estado) upd.gastro = estadoGastro.estado.estado === "visible";
                if (upd.ml !== undefined || upd.hogar !== undefined || upd.gastro !== undefined) {
                    try {
                        const res = await putEstadoPublicacionAPI(editandoProductoId, upd);
                        const lineas = ([["Mercado Libre", res.ml], ["KT HOGAR", res.hogar], ["KT GASTRO", res.gastro]] as const)
                            .filter(([, r]) => r)
                            .map(([label, r]) => `${label}: ${r!.detalle}`);
                        const huboError = [res.ml, res.hogar, res.gastro].some(r => r && !r.ok);
                        if (lineas.length) {
                            if (huboError) notificar.error(`Estado de publicación:\n${lineas.join("\n")}`);
                            else notificar.success(`Estado de publicación — ${lineas.join(" · ")}`);
                        }
                    } catch (e) { if (!esSesionExpirada(e)) notificar.error("No se pudo aplicar el cambio de estado de publicación"); }
                }
            }

            await onSuccess();
            if (resultados.every(r => r.estado === "ok")) {
                notificar.success(`Producto ${sku} actualizado`);
                notificarCanales(resultados);
                onClose();
            } else {
                const fallidos = resultados.filter(r => r.estado === "error").map(r => `• ${r.canal} — ${r.detalle}`).join("\n");
                notificar.error(`Los cambios se guardaron, pero falló la subida:\n${fallidos}`);
            }
        } catch (e) {
            if (!esSesionExpirada(e)) notificar.error(e instanceof Error ? e.message : "Error al guardar los cambios");
        } finally { setIsSaving(false); }
    };

    // Reintenta solo los canales que quedaron en error, sobre el producto ya creado (sin re-crear).
    const reintentarFallidos = async () => {
        const fallidos = resultadosCanal.filter(r => r.estado === "error").map(r => r.canal);
        if (!fallidos.length || !skuSubida) return;
        setReintentando(true);
        try {
            const nuevos = await ejecutarExportsCanales(skuSubida, fallidos);
            setResultadosCanal(prev => prev.map(p => nuevos.find(n => n.canal === p.canal) ?? p));
            if (nuevos.every(r => r.estado === "ok")) {
                notificarCanales(nuevos);
                onClose();
                await onSuccess();
            }
        } catch { /* los exports nunca rechazan; un fallo de refresh ya se maneja en el hook */ } finally {
            setReintentando(false);
        }
    };

    // Al cambiar "Es Combo" recalculamos el SKU sólo si el campo está vacío o
    // sigue siendo el que sugerimos (no pisamos un SKU escrito a mano).
    const handleToggleCombo = (next: boolean) => {
        setEsCombo(next);
        // Al togglear esCombo cambian los campos obligatorios: limpiamos los
        // errores de los que dejan de serlo para que no queden marcados.
        setFormErrors(p => {
            const n = { ...p };
            delete n.marcaId; delete n.origenId; delete n.proveedorId;
            delete n.materialId; delete n.tag; delete n.margen;
            return n;
        });
        if (sku === "" || sku === lastSuggestedSku) {
            void cargarSkuSugerido(next);
        }
    };

    // Aviso en vivo de SKU duplicado: con un pequeño debounce consultamos al
    // backend si el SKU tipeado ya pertenece a otro producto. Solo en modo alta;
    // abortamos la consulta anterior al re-tipear.
    useEffect(() => {
        // En edición el SKU es solo lectura (es el del propio producto): no validamos duplicado.
        if (editandoProductoId) { setSkuYaExiste(false); return; }
        const valor = sku.trim();
        if (!valor) { setSkuYaExiste(false); return; }
        const controller = new AbortController();
        const t = setTimeout(async () => {
            try {
                setSkuYaExiste(await existeSkuAPI(valor, controller.signal));
            } catch {
                // Silencioso: si falla la verificación, el backend igual rechaza al crear.
            }
        }, 400);
        return () => { clearTimeout(t); controller.abort(); };
    }, [sku, editandoProductoId]);

    // Carga el detalle de imágenes del SKU para mostrar el aviso preventivo de formato/tamaño.
    useEffect(() => {
        const skuTrim = sku.trim();
        if (!skuTrim) { setImagenesDetectadas([]); return; }
        const t = setTimeout(() => {
            getImagenDetalleAPI(skuTrim)
                .then(setImagenesDetectadas)
                .catch(() => setImagenesDetectadas([]));
        }, 400);
        return () => clearTimeout(t);
    }, [sku]);

    // Rellena los inputs del MLA (MLAU/envío/comisión/tope) y guarda el snapshot con sus
    // fechas de cálculo cada vez que se asocia un MLA existente (al editar, al matchear por
    // código o al autocompletar desde ML). Para un código nuevo (mlaId null) no toca los
    // inputs: quedan editables para crear el MLA al guardar.
    useEffect(() => {
        if (!mlaId) return;
        let cancel = false;
        getMlaPorIdAPI(mlaId)
            .then((d) => {
                if (cancel) return;
                setMlaDetalle(d);
                setMlaMlau(d.mlau ?? "");
                setMlaPrecioEnvio(d.precioEnvio ?? "");
                setMlaComision(d.comisionPorcentaje ?? "");
                setMlaTope(d.topePromocion ?? "");
            })
            .catch(() => { if (!cancel) setMlaDetalle(null); });
        return () => { cancel = true; };
    }, [mlaId]);

    // Lookup por código: al tipear/pegar el código del MLA, busca un match exacto en la BD.
    // Si existe ⇒ lo asocia (mlaId) y el efecto de arriba completa sus datos. Si no existe ⇒
    // queda como MLA nuevo (mlaId null) a crearse al guardar (los inputs muestran lo que haya).
    useEffect(() => {
        const code = mlaCodigo.trim();
        if (!code) return; // el caso vacío lo maneja el onChange del input
        const t = setTimeout(async () => {
            try {
                const opts = await searchMlas(code);
                const exact = opts.find((o) => o.label.toLowerCase() === code.toLowerCase());
                if (exact) {
                    setMlaId(Number(exact.id));
                } else {
                    // Código nuevo: desasocia y descarta el snapshot (así no quedan fechas
                    // "calculado el…" de un MLA anterior bajo un código que aún no existe).
                    setMlaId(null);
                    setMlaDetalle(null);
                }
            } catch { /* búsqueda best-effort: si falla, no cambia la asociación */ }
        }, 400);
        return () => clearTimeout(t);
    }, [mlaCodigo]);

    // Carga la ficha técnica de la categoría ML cada vez que cambia mlCategoryId.
    useEffect(() => {
        if (!mlCategoryId) { setMlFicha(null); return; }
        let cancel = false;
        getMlCategoriaFichaAPI(mlCategoryId)
            .then(ficha => { if (!cancel) setMlFicha(ficha); })
            .catch(() => { if (!cancel) setMlFicha(null); });
        return () => { cancel = true; };
    }, [mlCategoryId]);

    // Carga el máximo de caracteres permitido para el Título ML según la categoría seleccionada.
    useEffect(() => {
        if (!mlCategoryId) { setMaxTitleLengthMl(null); return; }
        let cancel = false;
        getMlCategoriaMaxTitleAPI(String(mlCategoryId))
            .then(n => { if (!cancel) setMaxTitleLengthMl(n); })
            .catch(() => { if (!cancel) setMaxTitleLengthMl(null); });
        return () => { cancel = true; };
    }, [mlCategoryId]);

    // ===== Sincronización Dimensiones Físicas ↔ atributos de dimensión de ML =====
    type FisicoKey = "alto" | "ancho" | "largo" | "diamboca" | "diambase" | "capacidad" | "espesor";
    // Mapeo atributo ML → campo físico + unidad. Se indexa por id de atributo porque algunas
    // categorías separan el diámetro en boca/base (MOUTH_DIAMETER / BASE_DIAMETER).
    const ML_DIM_MAP: Record<string, { fisico: FisicoKey; unidad: string }> = {
        HEIGHT: { fisico: "alto", unidad: "cm" },
        WIDTH: { fisico: "ancho", unidad: "cm" },
        LENGTH: { fisico: "largo", unidad: "cm" },
        DIAMETER: { fisico: "diamboca", unidad: "cm" },
        MOUTH_DIAMETER: { fisico: "diamboca", unidad: "cm" },
        BASE_DIAMETER: { fisico: "diambase", unidad: "cm" },
        CAPACITY: { fisico: "capacidad", unidad: "" }, // capacidad: string libre con unidad
        THICKNESS: { fisico: "espesor", unidad: "mm" },
    };
    // Fallback de unidades para atributos number_unit cuya categoría NO declara allowed_units
    // (ML las acepta igual). Ej.: VOLUME_CAPACITY ("Capacidad en volumen") viene sin unidades,
    // por eso el selector quedaba vacío.
    const FALLBACK_UNITS_ML: Record<string, string[]> = {
        VOLUME_CAPACITY: ["ml", "cc", "l"],
    };
    // Unidades ofrecidas en "Dimensiones Físicas" (la primera es el default).
    const FISICO_UNITS: Record<FisicoKey, string[]> = {
        largo: ["cm", "mm"], ancho: ["cm", "mm"], alto: ["cm", "mm"],
        diamboca: ["cm"], diambase: ["cm"],
        espesor: ["mm"], capacidad: ["ml", "lt"],
    };
    const fisicoSetters: Record<FisicoKey, (v: string) => void> = {
        alto: setAlto, ancho: setAncho, largo: setLargo,
        diamboca: setDiamboca, diambase: setDiambase, capacidad: setCapacidad, espesor: setEspesor,
    };
    const fisicoValues: Record<FisicoKey, string> = { alto, ancho, largo, diamboca, diambase, capacidad, espesor };
    // Extrae el primer número (acepta coma o punto) de un texto libre.
    const parseNumero = (s: string): string => {
        const m = (s ?? "").match(/-?\d+(?:[.,]\d+)?/);
        return m ? m[0].replace(",", ".") : "";
    };
    const formatNumberUnit = (num: string, unidad: string): string =>
        num ? (unidad ? `${num} ${unidad}` : num) : "";
    // Unidad efectiva de una dimensión: la del valor guardado; si no trae, la que declara ML (ficha de la
    // categoría); sino el default local. Misma lógica que el selector de renderFisico, para que lo que se VE se GUARDE.
    const unidadDe = (key: FisicoKey): string => {
        const enValor = fisicoValues[key].replace(/^\s*-?\d+(?:[.,]\d+)?\s*/, "").trim();
        if (enValor) return enValor;
        const mlDef = Object.entries(ML_DIM_MAP)
            .filter(([, m]) => m.fisico === key)
            .map(([attrId]) => fichaAttrById.get(attrId))
            .find(d => d?.valueType === "number_unit" && d.allowedUnits.length > 0);
        const unidades = mlDef ? mlDef.allowedUnits : FISICO_UNITS[key];
        return mlDef?.defaultUnit || unidades[0] || "";
    };
    // Valor de una dimensión numérica normalizado para persistir: "<num> <unidad>". Garantiza que la unidad
    // mostrada en el selector se guarde aunque el usuario no toque el campo (bug: largo/ancho/alto sin unidad).
    const normalizarFisico = (key: FisicoKey): string => {
        const n = parseNumero(fisicoValues[key]);
        return n ? formatNumberUnit(n, unidadDe(key)) : "";
    };

    // Actualiza solo el estado del atributo ML (sin tocar las dimensiones físicas).
    const setAtributoCore = (id: string, valueName: string, valueId: string | null = null) => {
        setMlAtributosVal(prev => {
            const next = { ...prev };
            const cur = next[id];
            if (!valueName && !cur?.noAplica) delete next[id];
            else next[id] = { attributeId: id, valueId, valueName, noAplica: cur?.noAplica ?? false };
            return next;
        });
        setFormErrors(p => p.mlAtributos ? { ...p, mlAtributos: "" } : p);
    };

    // Setter usado por los inputs de la ficha ML: además espeja a la dimensión física mapeada.
    const setAtributo = (id: string, valueName: string, valueId: string | null = null) => {
        setAtributoCore(id, valueName, valueId);
        const map = ML_DIM_MAP[id];
        if (map) fisicoSetters[map.fisico](valueName);
    };

    // Marca/desmarca "No aplica" para un atributo (conserva o limpia el valor según corresponda).
    const setNoAplica = (id: string, checked: boolean) => {
        setMlAtributosVal(prev => {
            const next = { ...prev };
            const cur = next[id];
            if (checked) next[id] = { attributeId: id, valueId: null, valueName: "", noAplica: true };
            else if (cur?.valueName) next[id] = { ...cur, noAplica: false };
            else delete next[id];
            return next;
        });
        setFormErrors(p => p.mlAtributos ? { ...p, mlAtributos: "" } : p);
    };

    // Setter usado por los inputs de "Dimensiones Físicas": espeja a los atributos ML presentes.
    const onFisicoChange = (fisico: FisicoKey, raw: string) => {
        fisicoSetters[fisico](raw);
        for (const [attrId, m] of Object.entries(ML_DIM_MAP)) {
            if (m.fisico !== fisico || !fichaAttrIds.has(attrId) || mlAtributosVal[attrId]?.noAplica) continue;
            // El valor físico ya incluye la unidad: se espeja tal cual al atributo ML mapeado.
            setAtributoCore(attrId, raw, mlAtributosVal[attrId]?.valueId ?? null);
        }
    };

    // Al cambiar de categoría, descarta los atributos guardados que la categoría actual NO declara
    // (quedaron "stale" de una categoría anterior): así no se mandan a ML atributos que no pide.
    // Solo poda con la ficha ya cargada; si fichaAttrIds estuviera vacío (cargando) borraría todo.
    useEffect(() => {
        if (!mlFicha) return;
        setMlAtributosVal(prev => {
            const next: typeof prev = {};
            let changed = false;
            for (const [id, attr] of Object.entries(prev)) {
                if (fichaAttrIds.has(id)) next[id] = attr;
                else changed = true;
            }
            return changed ? next : prev;
        });
    }, [mlFicha, fichaAttrIds]);

    // Al cargar la ficha, pre-llena los atributos de dimensión vacíos desde las dimensiones físicas.
    useEffect(() => {
        if (!mlFicha) return;
        if (editandoProductoId) return; // en edición los atributos vienen del canal, no de datos locales
        setMlAtributosVal(prev => {
            const next = { ...prev };
            let changed = false;
            for (const [attrId, m] of Object.entries(ML_DIM_MAP)) {
                if (!fichaAttrIds.has(attrId) || next[attrId]?.valueName || next[attrId]?.noAplica) continue;
                const fisico = fisicoValues[m.fisico];
                const unidadFisica = fisico.replace(/^\s*-?\d+(?:[.,]\d+)?\s*/, "").trim() || m.unidad;
                const valueName = m.fisico === "capacidad"
                    ? fisico
                    : (parseNumero(fisico) ? formatNumberUnit(parseNumero(fisico), unidadFisica) : "");
                if (valueName) { next[attrId] = { attributeId: attrId, valueId: null, valueName, noAplica: false }; changed = true; }
            }
            return changed ? next : prev;
        });
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [mlFicha, fichaAttrIds, editandoProductoId]);

    // Los atributos required no pueden quedar en "No aplica" (son obligatorios): si vinieran marcados
    // (carga vieja), se limpia el flag para que el input quede habilitado y completable.
    useEffect(() => {
        if (!mlFicha) return;
        setMlAtributosVal(prev => {
            const next = { ...prev };
            let changed = false;
            for (const d of mlAtributosDef) {
                if (d.required && next[d.id]?.noAplica) {
                    next[d.id] = { ...next[d.id], noAplica: false };
                    changed = true;
                }
            }
            return changed ? next : prev;
        });
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [mlFicha, fichaAttrIds]);

    // El atributo BRAND (Marca) de la ficha ML espeja SIEMPRE la Marca maestra (la Marca manda):
    // al elegir/cambiar/limpiar la Marca, el BRAND se completa o se vacía en consecuencia.
    useEffect(() => {
        if (!mlFicha || !fichaAttrIds.has("BRAND")) return;
        if (editandoProductoId) return; // en edición los atributos vienen del canal, no de datos locales
        const marcaLeaf = marcaDisplay.split(">").pop()?.trim() ?? "";
        // Si hay Marca elegida pero su nombre aún no cargó, esperamos: no borrar el BRAND guardado.
        if (marcaId && !marcaLeaf) return;
        setMlAtributosVal(prev => {
            const cur = prev["BRAND"];
            if (cur?.noAplica) return prev;                         // respeta "No aplica"
            if ((cur?.valueName ?? "") === marcaLeaf) return prev;   // ya está sincronizado (evita re-render)
            if (!marcaLeaf) {                                        // Marca vacía → BRAND vacío
                if (!cur) return prev;
                const next = { ...prev }; delete next["BRAND"]; return next;
            }
            return { ...prev, BRAND: { attributeId: "BRAND", valueId: null, valueName: marcaLeaf, noAplica: false } };
        });
    }, [mlFicha, fichaAttrIds, marcaDisplay, marcaId, editandoProductoId]);

    // El atributo MATERIAL de la ficha ML espeja SIEMPRE el Material maestro (mismo criterio que BRAND).
    useEffect(() => {
        if (!mlFicha || !fichaAttrIds.has("MATERIAL")) return;
        if (editandoProductoId) return; // en edición los atributos vienen del canal, no de datos locales
        const material = materialDisplay.trim();
        // El nombre del material puede llegar async (getNombreById): si hay Material elegido pero el
        // nombre aún no cargó, esperamos en vez de borrar el MATERIAL guardado.
        if (materialId && !material) return;
        setMlAtributosVal(prev => {
            const cur = prev["MATERIAL"];
            if (cur?.noAplica) return prev;                          // respeta "No aplica"
            if ((cur?.valueName ?? "") === material) return prev;     // ya está sincronizado (evita re-render)
            if (!material) {                                          // Material vacío → MATERIAL vacío
                if (!cur) return prev;
                const next = { ...prev }; delete next["MATERIAL"]; return next;
            }
            return { ...prev, MATERIAL: { attributeId: "MATERIAL", valueId: null, valueName: material, noAplica: false } };
        });
    }, [mlFicha, fichaAttrIds, materialDisplay, materialId, editandoProductoId]);

    const aplicarMlaEnForm = (mla: { id: number; mla: string; mlau: string | null; precioEnvio: number | null; comisionPorcentaje: number | null; topePromocion: number | null }) => {
        setMlaCodigo(mla.mla);
        setMlaId(mla.id);
        setMlaMlau(mla.mlau ?? "");
        setMlaPrecioEnvio(mla.precioEnvio ?? "");
        setMlaTope(mla.topePromocion ?? "");
        setMlaComision(mla.comisionPorcentaje ?? "");
    };

    // Normaliza un input numérico ("" ⇒ null) para comparar/enviar al backend.
    const normNum = (v: number | "") => (v === "" ? null : Number(v));

    // ¿Difieren los datos cargados en el form respecto del snapshot `base` del MLA en la BD?
    // Coacciona a número ambos lados (defensivo ante BigDecimal) y trata el tope como 0 a ambos
    // lados (mismo criterio que se envía), para no disparar PATCH espurios.
    const mlaFueEditado = (base: MlaDetalleDTO | null): boolean => {
        if (!base) return false;
        const num = (v: number | null | undefined) => (v == null ? null : Number(v));
        return (mlaMlau.trim() || null) !== (base.mlau ?? null)
            || normNum(mlaPrecioEnvio) !== num(base.precioEnvio)
            || normNum(mlaComision) !== num(base.comisionPorcentaje)
            || (mlaTope === "" ? 0 : Number(mlaTope)) !== (base.topePromocion ?? 0);
    };

    // Resuelve qué MLA asociar al guardar (vale para alta y edición):
    //  - código vacío ⇒ sin MLA (si el canal ML está marcado, lo crea/asocia el publish).
    //  - código que matchea un MLA existente ⇒ lo asocia; si editaste sus datos, los persiste (PATCH).
    //  - código nuevo ⇒ crea el MLA con los datos cargados.
    // Devuelve el id a asociar y si hubo creación (para decidir el cálculo de envío inicial).
    const resolverMlaParaGuardar = async (): Promise<{ mlaId: number | null; fueCreado: boolean }> => {
        const code = mlaCodigo.trim();
        if (!code) return { mlaId: null, fueCreado: false };
        const datos = {
            mlau: mlaMlau.trim() || null,
            precioEnvio: normNum(mlaPrecioEnvio),
            comisionPorcentaje: normNum(mlaComision),
            topePromocion: mlaTope === "" ? 0 : Number(mlaTope),
        };
        // Re-confirma el match exacto al guardar aunque el lookup con debounce no haya
        // corrido (p.ej. pegar el código y guardar enseguida). La columna `mla` NO es
        // única en la BD: sin esto, crear un código ya existente generaría un MLA duplicado.
        // Si se resuelve por re-lookup, se trae el baseline real para comparar ediciones (no
        // alcanza el snapshot `mlaDetalle`, que aún sería null) y así no nullear datos del MLA.
        let id = mlaId;
        let base = mlaDetalle;
        if (!id) {
            try {
                const opts = await searchMlas(code);
                const exact = opts.find((o) => o.label.toLowerCase() === code.toLowerCase());
                if (exact) {
                    id = Number(exact.id);
                    base = await getMlaPorIdAPI(id);
                }
            } catch { /* si la búsqueda/lectura falla, se intenta crear más abajo */ }
        }
        if (id) {
            if (mlaFueEditado(base)) await patchMlaAPI(id, datos);
            return { mlaId: id, fueCreado: false };
        }
        const creado = await createMlaAPI({ mla: code, ...datos });
        return { mlaId: creado.id, fueCreado: true };
    };

    // Trae la publicación de ML por el CÓDIGO MLA cargado en el form (activa o pausada):
    // crea/asegura el MLA, calcula envío + comisión y lo deja seleccionado. Avisa si es de catálogo.
    const handleObtenerMlaDeML = async () => {
        if (!mlaCodigo.trim()) {
            toast.error("Cargá primero el código MLA para traerlo de MercadoLibre");
            return;
        }
        setObteniendoMla(true);
        try {
            const r = await getMlaPorCodigoAPI(mlaCodigo.trim());
            aplicarMlaEnForm(r.mla);
            if (r.esCatalogo) {
                notificar.warning(`El MLA ${r.mla.mla} es una publicación de catálogo; el flujo de publicación puede diferir.`);
            }
            notificar.success(`MLA ${r.mla.mla} traído de MercadoLibre`);
        } catch (e) {
            if (!esSesionExpirada(e)) notificar.error(e instanceof Error ? e.message : "Error al obtener el MLA");
        } finally {
            setObteniendoMla(false);
        }
    };

    const handlePredecirCategoriasMl = async () => {
        if (!tituloMl.trim()) return;
        setCargandoPrediccionesMl(true);
        try {
            const preds = await predecirCategoriasMlAPI(tituloMl.trim());
            setPrediccionesMl(preds);
            if (preds.length === 0) notificar.info("Sin sugerencias de categoría para ese título");
        } catch (e) {
            if (!esSesionExpirada(e)) notificar.error(e instanceof Error ? e.message : "No se pudieron predecir categorías");
        } finally {
            setCargandoPrediccionesMl(false);
        }
    };

    // Arma el contexto que recibe la IA para generar el SEO de Nube, a partir de los campos del form.
    const construirContextoSeo = () => {
        const dimensiones = [
            capacidad && `Capacidad: ${capacidad}`,
            largo && `Largo: ${largo}`,
            ancho && `Ancho: ${ancho}`,
            alto && `Alto: ${alto}`,
            diamboca && `Diámetro de boca: ${diamboca}`,
            diambase && `Diámetro de base: ${diambase}`,
            espesor && `Espesor: ${espesor}`,
        ].filter((d): d is string => Boolean(d));
        return {
            tituloNube: tituloNube.trim(),
            marca: marcaDisplay || null,
            material: materialDisplay || null,
            aptos: aptosSel.map(a => a.label ?? String(a.id)).filter(Boolean),
            dimensiones,
        };
    };

    // Genera el SEO con IA para un canal de Nube y lo carga en su estado editable.
    const generarSeo = async (canal: "GASTRO" | "HOGAR") => {
        setGenerandoSeo(s => new Set(s).add(canal));
        try {
            const r = await generarSeoAPI(canal, construirContextoSeo());
            const next = { title: r.seoTitle, description: r.seoDescription, tags: r.seoTags };
            if (canal === "HOGAR") setSeoHogar(next); else setSeoGastro(next);
            notificar.success(`SEO de ${canal === "HOGAR" ? "KT Hogar" : "KT Gastro"} generado`);
        } catch (e) {
            if (!esSesionExpirada(e)) notificar.error(e instanceof Error ? e.message : "No se pudo generar el SEO con IA");
        } finally {
            setGenerandoSeo(s => { const n = new Set(s); n.delete(canal); return n; });
        }
    };

    // Fases de progreso (la llamada a OpenAI es opaca: la última fase permanece hasta que responde).
    const FASES_CARATULA: { ms: number; texto: string }[] = [
        { ms: 0, texto: "Preparando imagen…" },
        { ms: 800, texto: "Enviando a OpenAI…" },
        { ms: 2500, texto: "Generando carátula…" },
    ];

    const abrirSelectorCaratula = async () => {
        if (!sku.trim()) return;
        setSelectorCaratulaAbierto(true);
        setCrudasDisp(null);
        try {
            setCrudasDisp(await getCrudasAPI(sku.trim()));
        } catch (e) {
            if (!esSesionExpirada(e)) notificar.error(e instanceof Error ? e.message : "No se pudieron leer las imágenes crudas");
        }
    };

    const fmtDuracion = (ms: number) => {
        const s = ms / 1000;
        if (s < 60) return `${s.toFixed(1)} s`;
        const m = Math.floor(s / 60);
        return `${m}:${String(Math.round(s % 60)).padStart(2, "0")}`;
    };

    const generarCaratula = async (crudaNombre?: string) => {
        const cruda = crudaNombre ?? crudaElegida;
        if (!sku.trim() || !cruda) return;
        setCrudaElegida(cruda);
        setGenerandoCaratula(true);
        setDuracionCaratula(null);
        setTranscurridoCaratula(0);
        const inicio = Date.now();
        const timers = FASES_CARATULA.map(f => setTimeout(() => setFaseCaratula(f.texto), f.ms));
        const cron = setInterval(() => setTranscurridoCaratula(Date.now() - inicio), 200);
        try {
            const r = await generarCaratulaAPI(sku.trim(), cruda, tituloNube);
            setCaratulaPreview(r.imagenBase64);
            setCaratulaFormato(r.formato);
            setCaratulaCruda(r.crudaBase64);
            setCaratulaCrudaFormato(r.crudaFormato);
            setDuracionCaratula(Date.now() - inicio);
            setSelectorCaratulaAbierto(false);
        } catch (e) {
            if (!esSesionExpirada(e)) notificar.error(e instanceof Error ? e.message : "No se pudo generar la carátula");
        } finally {
            timers.forEach(clearTimeout);
            clearInterval(cron);
            setFaseCaratula("");
            setGenerandoCaratula(false);
        }
    };

    const guardarCaratula = async () => {
        if (!caratulaPreview || guardandoCaratula) return;
        setGuardandoCaratula(true);
        try {
            await guardarCaratulaAPI(sku.trim(), caratulaPreview);
            setCaratulaPreview(null);
            setCaratulaCruda(null);
            notificar.success("Carátula guardada");
            getImagenDetalleAPI(sku.trim()).then(setImagenesDetectadas).catch(() => {});
            setCaratulaCacheBust(c => c + 1);
            setSelectorCaratulaAbierto(false);
            setCrudasDisp(null);
            setCrudaElegida(null);
            setDuracionCaratula(null);
        } catch (e) {
            if (!esSesionExpirada(e)) notificar.error(e instanceof Error ? e.message : "No se pudo guardar la carátula");
        } finally {
            setGuardandoCaratula(false);
        }
    };

    const cancelarCaratula = () => {
        setCaratulaPreview(null);
        setCaratulaCruda(null);
        setDuracionCaratula(null);
        setCrudaElegida(null);
    };

    // Convierte un bloque de estado SEO a payload de export SOLO si tiene algún campo cargado; si no, undefined.
    const seoBloqueAPayload = (seo: { title: string; description: string; tags: string }): SeoNube | undefined =>
        (seo.title.trim() || seo.description.trim() || seo.tags.trim())
            ? { seoTitle: seo.title, seoDescription: seo.description, seoTags: seo.tags }
            : undefined;

    // Estructura base de cada sección; el color de fondo lo aporta un tinte propio (SECTION_TINT)
    // para diferenciar visualmente cada sección del fondo del modal.
    const sectionClassName = "rounded-2xl border p-4 shadow-sm";
    // El `!` (important) vence la regla global `.modal-form-shell fieldset { background:#fff }`
    // de globals.css, que si no deja todas las secciones blancas.
    const SECTION_TINT = {
        canales:        "!border-blue-200 !bg-blue-50/60 dark:!border-blue-900/40 dark:!bg-blue-950/20",
        identificacion: "!border-indigo-200 !bg-indigo-50/60 dark:!border-indigo-900/40 dark:!bg-indigo-950/20",
        economicos:     "!border-emerald-200 !bg-emerald-50/60 dark:!border-emerald-900/40 dark:!bg-emerald-950/20",
        reposicion:     "!border-amber-200 !bg-amber-50/60 dark:!border-amber-900/40 dark:!bg-amber-950/20",
        margenes:       "!border-teal-200 !bg-teal-50/60 dark:!border-teal-900/40 dark:!bg-teal-950/20",
        clasificacion:  "!border-violet-200 !bg-violet-50/60 dark:!border-violet-900/40 dark:!bg-violet-950/20",
        catalogos:      "!border-cyan-200 !bg-cyan-50/60 dark:!border-cyan-900/40 dark:!bg-cyan-950/20",
        ml:             "!border-yellow-200 !bg-yellow-50/70 dark:!border-yellow-900/40 dark:!bg-yellow-950/20",
        dimensiones:    "!border-sky-200 !bg-sky-50/60 dark:!border-sky-900/40 dark:!bg-sky-950/20",
        paqueteMl:      "!border-orange-200 !bg-orange-50/60 dark:!border-orange-900/40 dark:!bg-orange-950/20",
        inflados:       "!border-rose-200 !bg-rose-50/60 dark:!border-rose-900/40 dark:!bg-rose-950/20",
        seo:            "!border-fuchsia-200 !bg-fuchsia-50/70 dark:!border-fuchsia-900/40 dark:!bg-fuchsia-950/20",
    } as const;
    const sectionTitleClassName = "flex items-center gap-2 text-base font-semibold text-slate-900 dark:text-slate-100 [&_svg]:h-5 [&_svg]:w-5 [&_svg]:text-blue-500 dark:[&_svg]:text-blue-400";
    const sectionDescriptionClassName = "mt-1 text-xs text-slate-500 dark:text-slate-400";
    const fieldLabelClassName = "block text-sm font-semibold text-slate-700 dark:text-slate-200";
    const inputBaseClassName = "mt-1 w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-900 shadow-sm outline-none transition placeholder:text-slate-400 focus:border-blue-400 focus:ring-4 focus:ring-blue-100 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400 disabled:opacity-70 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100 dark:focus:ring-blue-500/20 dark:disabled:bg-slate-900/40 dark:disabled:text-slate-500";
    const inputErrorClassName = "border-red-300 bg-red-50 focus:border-red-400 focus:ring-red-100 dark:border-red-700 dark:bg-red-950/20 dark:focus:ring-red-500/20";
    const checkboxCardClassName = "flex items-center gap-3 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm font-semibold text-slate-700 dark:border-slate-700 dark:bg-slate-800/80 dark:text-slate-200";
    // Card de canal (sección Canales de venta): layout en columna y altura uniforme para que el
    // selector de cuotas no comprima el título ni desalinee las tarjetas entre sí.
    const canalCardClassName = "flex flex-col gap-2 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm font-semibold text-slate-700 dark:border-slate-700 dark:bg-slate-800/80 dark:text-slate-200";
    const CANAL_TINT = {
        dux:    "!border-indigo-200 !bg-indigo-50/60 dark:!border-indigo-900/40 dark:!bg-indigo-950/20",
        hogar:  "!border-blue-200 !bg-blue-50/60 dark:!border-blue-900/40 dark:!bg-blue-950/20",
        gastro: "!border-emerald-200 !bg-emerald-50/60 dark:!border-emerald-900/40 dark:!bg-emerald-950/20",
        ml:     "!border-yellow-200 !bg-yellow-50/70 dark:!border-yellow-900/40 dark:!bg-yellow-950/20",
    } as const;
    const selectBaseClassName = `${inputBaseClassName} appearance-none`;

    const indicadorCarga = (texto: string) => (
        <div className="flex items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-3 py-4 text-xs text-slate-400 dark:border-slate-700 dark:bg-slate-800/60"><SpinnerIcon /> {texto}</div>
    );

    // Renderiza el input de un atributo según el tipo de componente de ML.
    const renderMlAtributoInput = (d: MlAtributoDef, c: MlComponente) => {
        const v = mlAtributosVal[d.id];
        const disabled = v?.noAplica ?? false;
        const hasRgb = d.values.some(o => o.rgb);
        // "No aplica" → input claramente gris (con !important para pisar el bg-white de la clase base).
        const grisOff = disabled ? " !bg-slate-200 !border-slate-200 !text-slate-400 !shadow-none cursor-not-allowed dark:!bg-slate-900/50 dark:!border-slate-700 dark:!text-slate-500" : "";
        // Required vacío (tras intentar guardar): borde rojo en el input, además del label y el aviso.
        const errCls = (d.required && !!formErrors.mlAtributos && !v?.valueName?.trim()) ? ` ${inputErrorClassName}` : "";
        const inputCls = `${inputBaseClassName}${grisOff}${errCls}`;
        const selectCls = `${selectBaseClassName}${grisOff}${errCls}`;

        // COLOR_INPUT con paleta: swatch del color seleccionado + select de valores.
        if (c.tipo === "COLOR_INPUT" && hasRgb) {
            const sel = d.values.find(o => o.id === v?.valueId);
            return (
                <div className="mt-1 flex items-center gap-2">
                    <span className="h-6 w-6 shrink-0 rounded-full border border-slate-300 dark:border-slate-600"
                        style={{ background: sel?.rgb ? `#${sel.rgb}` : "transparent" }} />
                    <select className={selectCls} value={v?.valueId ?? ""} disabled={disabled}
                        onChange={e => { const opt = d.values.find(o => o.id === e.target.value); setAtributo(d.id, opt?.name ?? "", opt?.id ?? null); }}>
                        <option value="">—</option>
                        {d.values.map(o => <option key={o.id} value={o.id}>{o.name}</option>)}
                    </select>
                </div>
            );
        }
        // BOOLEAN_INPUT → toggle Sí/No (values trae las dos opciones).
        if ((c.tipo === "BOOLEAN_INPUT" || d.valueType === "boolean") && d.values.length > 0) {
            return (
                <div className="mt-1 inline-flex overflow-hidden rounded-xl border border-slate-200 dark:border-slate-700">
                    {d.values.map(o => (
                        <button key={o.id} type="button" disabled={disabled}
                            onClick={() => setAtributo(d.id, o.name, o.id)}
                            className={`px-4 py-2 text-sm font-medium transition disabled:opacity-50 ${v?.valueId === o.id ? "bg-blue-600 text-white" : "bg-white text-slate-600 hover:bg-slate-50 dark:bg-slate-800 dark:text-slate-300"}`}>
                            {o.name}
                        </button>
                    ))}
                </div>
            );
        }
        // list o COMBO sin valor libre → select.
        if (d.valueType === "list" || (c.tipo === "COMBO" && !c.allowCustomValue && d.values.length > 0)) {
            return (
                <select className={`${selectCls} mt-1`} value={v?.valueId ?? ""} disabled={disabled}
                    onChange={e => { const opt = d.values.find(o => o.id === e.target.value); setAtributo(d.id, opt?.name ?? "", opt?.id ?? null); }}>
                    <option value="">—</option>
                    {d.values.map(o => <option key={o.id} value={o.id}>{o.name}</option>)}
                </select>
            );
        }
        // number_unit → número + unidad.
        if (d.valueType === "number_unit") {
            const parts = (v?.valueName ?? "").split(" ");
            const num = parts[0] ?? "";
            const unit = parts.slice(1).join(" ");
            // Algunas dimensiones de ML no declaran allowed_units; usamos la unidad del mapeo físico como fallback.
            const unidades = d.allowedUnits.length ? d.allowedUnits
                : FALLBACK_UNITS_ML[d.id] ?? (ML_DIM_MAP[d.id] ? [ML_DIM_MAP[d.id].unidad] : []);
            const unidadActual = unit || d.defaultUnit || unidades[0] || "";
            const setNU = (n: string, u: string) => setAtributo(d.id, n ? `${n} ${u}` : "");
            return (
                <div className="mt-1 flex gap-2">
                    <input type="number" className={inputCls} value={num} disabled={disabled} placeholder={c.example ?? undefined}
                        onChange={e => setNU(e.target.value, unidadActual)} />
                    <select className={selectCls} value={unidadActual} disabled={disabled}
                        onChange={e => setNU(num, e.target.value)}>
                        {unidades.map(u => <option key={u} value={u}>{u}</option>)}
                    </select>
                </div>
            );
        }
        // string / number / COMBO con valor libre → texto, con datalist de sugeridos si hay values.
        return (
            <>
                <input type={d.valueType === "number" ? "number" : "text"} className={inputCls}
                    disabled={disabled}
                    maxLength={d.valueType === "number" ? undefined : (d.valueMaxLength ?? undefined)}
                    placeholder={c.example ?? undefined}
                    list={d.values.length ? `dl-${d.id}` : undefined} value={v?.valueName ?? ""}
                    onChange={e => setAtributo(d.id, e.target.value)} />
                {d.values.length > 0 && (
                    <datalist id={`dl-${d.id}`}>
                        {d.values.map(o => <option key={o.id} value={o.name} />)}
                    </datalist>
                )}
            </>
        );
    };

    // Renderiza una celda (label + input + ayuda + "No aplica") para un atributo dentro de un componente.
    const renderMlCelda = (d: MlAtributoDef, c: MlComponente, label: string, mostrarAyuda: boolean) => {
        const v = mlAtributosVal[d.id];
        const faltante = d.required && formErrors.mlAtributos && !v?.valueName?.trim();
        return (
            <div key={d.id}>
                <span className="flex items-center gap-1 text-sm font-semibold text-slate-700 dark:text-slate-200">
                    <span className={faltante ? "text-red-600 dark:text-red-400" : ""}>
                        {label}{subirMl && d.required && <span className="ml-0.5 font-bold text-red-600">*</span>}
                    </span>
                    {mostrarAyuda && c.tooltip && (
                        <Tooltip content={c.tooltip} className="flex items-center">
                            <InformationCircleIcon className="h-3.5 w-3.5 shrink-0 text-slate-400 transition-colors hover:text-slate-600 dark:hover:text-slate-200" />
                        </Tooltip>
                    )}
                </span>
                {renderMlAtributoInput(d, c)}
                {mostrarAyuda && c.hint && <span className="mt-0.5 block text-[11px] leading-tight text-slate-400 dark:text-slate-500">{c.hint}</span>}
                {!d.required && (
                    <label className="mt-1 flex w-fit items-center gap-1.5 text-xs text-slate-500 dark:text-slate-400">
                        <input type="checkbox" className="h-3.5 w-3.5 rounded border-slate-300"
                            checked={v?.noAplica ?? false} onChange={e => setNoAplica(d.id, e.target.checked)} />
                        No aplica
                    </label>
                )}
            </div>
        );
    };

    // Devuelve las celdas de un componente para la grilla de la sección.
    // - COLOR_INPUT: una sola celda (la del swatch).
    // - Multi-atributo (LINKED_BY_CONNECTOR_INPUT, p.ej. "Medidas"): un bloque agrupado con borde,
    //   título del componente y los sub-campos (Altura/Ancho/Largo) en una sub-grilla.
    // - Un solo atributo: una celda normal con su ayuda (hint/tooltip).
    const renderMlComponenteCeldas = (c: MlComponente) => {
        if (c.tipo === "COLOR_INPUT") {
            const d = c.atributos.find(a => a.values.some(v => v.rgb)) ?? c.atributos[0];
            return d ? [renderMlCelda(d, c, c.label, true)] : [];
        }
        if (c.atributos.length > 1) {
            return [(
                <div key={c.label} className="rounded-xl border border-slate-200 bg-slate-50/60 p-3 dark:border-slate-700 dark:bg-slate-800/30 md:col-span-2 xl:col-span-3">
                    <span className="flex items-center gap-1 text-sm font-semibold text-slate-700 dark:text-slate-200">
                        {c.label}
                        {c.tooltip && (
                            <Tooltip content={c.tooltip} className="flex items-center">
                                <InformationCircleIcon className="h-3.5 w-3.5 shrink-0 text-slate-400 transition-colors hover:text-slate-600 dark:hover:text-slate-200" />
                            </Tooltip>
                        )}
                    </span>
                    <div className="mt-2 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
                        {c.atributos.map(a => renderMlCelda(a, c, a.name, false))}
                    </div>
                </div>
            )];
        }
        return c.atributos.map(a => renderMlCelda(a, c, a.name, true));
    };

    // Render de un campo de "Dimensiones Físicas": número + selector de unidad. Persiste "<num> <unidad>".
    const renderFisico = (key: FisicoKey, label: string) => {
        const value = fisicoValues[key];
        const num = parseNumero(value);
        // Las unidades las manda ML: si la ficha de la categoría declara unidades para la dimensión
        // mapeada, se usan ESAS (sistema y ML quedan idénticos); si no hay categoría/atributo, fallback local.
        const mlDef = Object.entries(ML_DIM_MAP)
            .filter(([, m]) => m.fisico === key)
            .map(([attrId]) => fichaAttrById.get(attrId))
            .find(d => d?.valueType === "number_unit" && d.allowedUnits.length > 0);
        const unidades = mlDef ? mlDef.allowedUnits : FISICO_UNITS[key];
        const unidadActual = unidadDe(key);
        // Si el valor guardado trae una unidad que ML ya no ofrece, la incluimos igual para que el
        // select no quede en blanco (y el usuario vea/cambie la unidad real persistida).
        const unidadesMostradas = unidadActual && !unidades.includes(unidadActual) ? [...unidades, unidadActual] : unidades;
        const setFis = (n: string, u: string) => { onFisicoChange(key, n ? `${n} ${u}` : ""); if (formErrors[key]) setFormErrors(p => ({ ...p, [key]: "" })); };
        return (
            <label className="block">
                <span className={fieldLabelClassName}>{label}</span>
                <div className="mt-1 flex gap-2">
                    <input type="number" min={0} className={`${inputBaseClassName} ${formErrors[key] ? inputErrorClassName : ""}`}
                        value={num} onChange={e => setFis(e.target.value, unidadActual)} />
                    <select className={selectBaseClassName} value={unidadActual} onChange={e => setFis(num, e.target.value)}>
                        {unidadesMostradas.map(u => <option key={u} value={u}>{u}</option>)}
                    </select>
                </div>
                {formErrors[key] && <p className="mt-1 text-xs text-red-500">{formErrors[key]}</p>}
            </label>
        );
    };

    const estadoIcon = (estado: string | undefined) => {
        switch (estado) {
            case "active": return <CheckCircleIcon className="h-4 w-4 shrink-0 text-emerald-500" />;
            case "paused": return <PauseCircleIcon className="h-4 w-4 shrink-0 text-amber-500" />;
            case "visible": return <EyeIcon className="h-4 w-4 shrink-0 text-emerald-500" />;
            case "oculta": return <EyeSlashIcon className="h-4 w-4 shrink-0 text-slate-400" />;
            case "habilitado": return <CheckCircleIcon className="h-4 w-4 shrink-0 text-emerald-500" />;
            case "deshabilitado": return <MinusCircleIcon className="h-4 w-4 shrink-0 text-slate-400" />;
            default: return null;
        }
    };

    const ESTADO_BADGE: Record<string, { label: string; cls: string }> = {
        active:        { label: "Activa",        cls: "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300" },
        paused:        { label: "Pausada",       cls: "bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300" },
        visible:       { label: "Visible",       cls: "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300" },
        oculta:        { label: "Oculta",        cls: "bg-slate-200 text-slate-600 dark:bg-slate-700 dark:text-slate-300" },
        habilitado:    { label: "Habilitado",    cls: "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300" },
        deshabilitado: { label: "Deshabilitado", cls: "bg-slate-200 text-slate-600 dark:bg-slate-700 dark:text-slate-300" },
        // Estados de ML que NO se pueden setear desde el panel (solo lectura): se muestran como badge, sin toggle.
        under_review:  { label: "En revisión",   cls: "bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300" },
        closed:        { label: "Cerrada",       cls: "bg-slate-200 text-slate-600 dark:bg-slate-700 dark:text-slate-300" },
        inactive:      { label: "Inactiva",      cls: "bg-slate-200 text-slate-600 dark:bg-slate-700 dark:text-slate-300" },
    };

    // Estados de ML que el usuario SÍ puede alternar (la API de ML solo acepta active/paused).
    const ML_ESTADO_EDITABLE = (estado: string | null | undefined) => estado == null || estado === "active" || estado === "paused";

    const statChip = (label: string, value: string, valueCls = "text-slate-700 dark:text-slate-200") => (
        <span className="inline-flex flex-col rounded-lg bg-white/70 px-2 py-1 leading-tight shadow-sm ring-1 ring-slate-200/70 dark:bg-slate-900/40 dark:ring-slate-700/60">
            <span className="text-[10px] font-medium uppercase tracking-wide text-slate-400">{label}</span>
            <span className={`text-xs font-semibold ${valueCls}`}>{value}</span>
        </span>
    );

    // Switch on/off para el estado (binario) de cada canal. "on" = estado positivo (Habilitado/Visible/Activa).
    const estadoToggle = (on: boolean, onChange: (on: boolean) => void, disabled: boolean, onLabel: string, offLabel: string) => (
        <button type="button" role="switch" aria-checked={on} aria-label={on ? onLabel : offLabel} disabled={disabled} onClick={() => onChange(!on)}
            title={disabled ? undefined : `${on ? onLabel : offLabel} — clic para cambiar`}
            className={`relative inline-flex h-5 w-9 shrink-0 items-center rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-1 disabled:cursor-not-allowed disabled:opacity-50 ${on ? "bg-emerald-500" : "bg-slate-300 dark:bg-slate-600"}`}>
            <span className={`inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform ${on ? "translate-x-[1.125rem]" : "translate-x-0.5"}`} />
        </button>
    );

    const renderEstadoBody = (
        canal: EstadoCanal | undefined,
        control: React.ReactNode,
        estadoSel?: string,
        cargando?: boolean,
    ) => (
        <div className="border-t border-slate-200/70 pt-2 dark:border-slate-700/60">
            {cargando ? <span className="text-xs text-slate-400">Leyendo estado…</span>
              : !canal || canal.error ? <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700 dark:bg-amber-900/40 dark:text-amber-300">No se pudo leer el estado</span>
              : !canal.publicado ? <span className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-500 dark:bg-slate-700/60 dark:text-slate-300"><MinusCircleIcon className="h-3.5 w-3.5 shrink-0" /> No publicado</span>
              : (<>
                  <div className="flex items-center gap-2">
                      <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-semibold ${ESTADO_BADGE[estadoSel ?? ""]?.cls ?? "bg-slate-100 text-slate-500 dark:bg-slate-700/60 dark:text-slate-300"}`}>
                          {estadoIcon(estadoSel)}{ESTADO_BADGE[estadoSel ?? ""]?.label ?? estadoSel}
                      </span>
                      <div className="ml-auto shrink-0">{control}</div>
                  </div>
                  <div className="mt-2 flex flex-wrap gap-1.5">
                      {canal.precio != null && statChip("Precio", canal.precio.toLocaleString("es-AR", { style: "currency", currency: "ARS", minimumFractionDigits: 2, maximumFractionDigits: 2 }))}
                      {canal.promo != null && statChip("Promo", canal.promo.toLocaleString("es-AR", { style: "currency", currency: "ARS", minimumFractionDigits: 2, maximumFractionDigits: 2 }), "text-emerald-600 dark:text-emerald-400")}
                      {canal.stock != null && statChip("Stock", String(canal.stock))}
                      {canal.imagenes != null && statChip("Imágenes", String(canal.imagenes))}
                  </div>
                  {canal.imagenesUrls != null && canal.imagenesUrls.length > 0 && (
                      <div className="mt-2 flex flex-wrap gap-1.5">
                          {canal.imagenesUrls.map((url, i) => (
                              <a key={i} href={url} target="_blank" rel="noreferrer" title={`Imagen ${i + 1} — abrir en tamaño completo`}
                                 className="block h-12 w-12 overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm transition-transform hover:scale-105 dark:border-slate-600">
                                  <img src={url} alt={`Imagen ${i + 1}`} loading="lazy" className="h-full w-full object-contain"
                                       onError={(e) => { const a = e.currentTarget.parentElement as HTMLElement | null; if (a) a.style.display = "none"; }} />
                              </a>
                          ))}
                      </div>
                  )}
              </>)}
        </div>
    );

    const renderSeoNube = (
        canal: "HOGAR" | "GASTRO",
        titulo: string,
        seo: { title: string; description: string; tags: string },
        setSeo: React.Dispatch<React.SetStateAction<{ title: string; description: string; tags: string }>>,
    ) => (
        <div className="rounded-2xl border border-slate-200 bg-white/70 p-4 dark:border-slate-700 dark:bg-slate-800/60">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                <span className="text-sm font-semibold text-slate-700 dark:text-slate-200">SEO · {titulo}</span>
                <div className="flex flex-wrap items-center gap-2">
                    <Button variant="dark" onClick={() => generarSeo(canal)} disabled={generandoSeo.has(canal)}>
                        {generandoSeo.has(canal) ? <SpinnerIcon /> : <SparklesIcon className="h-4 w-4" />}
                        {generandoSeo.has(canal) ? "Generando..." : "Generar SEO con IA"}
                    </Button>
                    {modelSeo && (
                        <span className="ml-2 text-xs text-slate-500 dark:text-slate-400">
                            Modelo: {modelSeo}{" "}
                            <a href="/config-ia?tab=seo" target="_blank" rel="noreferrer" className="text-blue-600 hover:underline dark:text-blue-400">config</a>
                        </span>
                    )}
                </div>
            </div>
            {(canal === "HOGAR" ? cargandoHogar : cargandoGastro) ? indicadorCarga("Cargando datos del canal…") : (
                <div className="grid grid-cols-1 gap-3">
                    <label className="block">
                        <span className={fieldLabelClassName}>SEO Title</span>
                        <input type="text" maxLength={70} className={inputBaseClassName} value={seo.title} onChange={e => setSeo(p => ({ ...p, title: e.target.value }))} placeholder="Título SEO" />
                        <span className="mt-1 block text-right text-xs text-slate-400">{seo.title.length}/70</span>
                    </label>
                    <label className="block">
                        <span className={fieldLabelClassName}>SEO Description</span>
                        <textarea maxLength={320} rows={3} className={inputBaseClassName} value={seo.description} onChange={e => setSeo(p => ({ ...p, description: e.target.value }))} placeholder="Descripción SEO" />
                        <span className="mt-1 block text-right text-xs text-slate-400">{seo.description.length}/320</span>
                    </label>
                    <label className="block">
                        <span className={fieldLabelClassName}>Tags</span>
                        <input type="text" className={inputBaseClassName} value={seo.tags} onChange={e => setSeo(p => ({ ...p, tags: e.target.value }))} placeholder="tag1, tag2, ..." />
                    </label>
                </div>
            )}
        </div>
    );

    return (
        <>
            {/* MODAL CREAR / EDITAR PRODUCTO */}
            <Modal isOpen={true} onClose={() => { if (!isSaving) onClose(); }} title={editandoProductoId ? `Editar Producto${sku ? ` · ${sku}` : ""}` : "Nuevo Producto"} size="3xl" closeOnEscape={false} busy={isSaving}
                footer={<div className="flex w-full items-center justify-between gap-3">
                    <div className="min-w-0 flex-1">
                        {resultadosVariantes.some(v => v.resultados.some(r => r.estado === "error"))
                            ? <span className="block max-h-24 overflow-auto text-sm font-medium text-red-600 dark:text-red-400">
                                Variantes con error: {resultadosVariantes.filter(v => v.resultados.some(r => r.estado === "error")).map(v => `${v.sku} (${v.resultados.filter(r => r.estado === "error").map(r => `${r.canal} — ${r.detalle}`).join("; ")})`).join(" · ")}
                              </span>
                            : resultadosCanal.some(r => r.estado === "error")
                            ? <span className="block max-h-16 overflow-auto text-sm font-medium text-red-600 dark:text-red-400">
                                Se guardó, pero falló la subida: {resultadosCanal.filter(r => r.estado === "error").map(r => `${r.canal} — ${r.detalle}`).join(" · ")}
                              </span>
                            : <span className={`text-sm text-red-600 dark:text-red-400 ${Object.values(formErrors).some(Boolean) ? "" : "invisible"}`}>Revisá los campos marcados antes de guardar.</span>}
                    </div>
                    <div className="flex shrink-0 items-center gap-3">
                        <button type="button" onClick={onClose} disabled={isSaving}
                            className="inline-flex items-center gap-1.5 rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm font-medium text-slate-600 shadow-sm transition hover:bg-slate-50 hover:text-slate-800 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-200 dark:hover:bg-slate-600">
                            <XMarkIcon className="h-4 w-4" /> Cancelar
                        </button>
                        <button type="button" onClick={editandoProductoId ? handleGuardarEdicion : handleCreate}
                            disabled={isSaving || (!editandoProductoId && skuYaExiste)}
                            className="inline-flex items-center gap-2 rounded-xl bg-primary px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-primary-light focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-1 disabled:cursor-not-allowed disabled:opacity-50 dark:focus:ring-offset-slate-900">
                            {isSaving ? <SpinnerIcon /> : <CheckIcon className="h-4 w-4" />}
                            {isSaving ? (editandoProductoId ? "Guardando..." : "Creando Producto...") : (editandoProductoId ? "Guardar Cambios" : "Crear Producto")}
                        </button>
                    </div>
                </div>}>
                <div className="text-sm">
                    {/* Tabs solo en modo edición: Datos (form) e Historial */}
                    {editandoProductoId && (
                        <div className="mb-5 flex items-center border-b border-slate-200 dark:border-slate-700">
                            {([["datos", "Datos"], ["historial", "Historial"]] as const).map(([id, label]) => (
                                <button
                                    key={id}
                                    type="button"
                                    onClick={() => setPanelTab(id)}
                                    className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${panelTab === id ? "border-blue-600 text-blue-700 dark:text-blue-300" : "border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700"}`}
                                >
                                    {label}
                                </button>
                            ))}
                            {producto && (
                                <div className="ml-auto flex flex-wrap justify-end gap-x-4 gap-y-0.5 pb-2 pl-4 text-xs text-slate-400 dark:text-slate-500">
                                    <span>Creado: <span className="font-medium text-slate-500 dark:text-slate-400">{formatFechaAR(producto.fechaCreacion)}</span></span>
                                    <span>Últ. modificación: <span className="font-medium text-slate-500 dark:text-slate-400">{formatFechaAR(producto.fechaModificacion)}</span></span>
                                </div>
                            )}
                        </div>
                    )}

                    {editandoProductoId && panelTab === "historial" && (
                        <HistorialSection productoId={editandoProductoId} productoSku={sku} />
                    )}

                    <div className={`flex-col gap-5 ${editandoProductoId && panelTab === "historial" ? "hidden" : "flex"}`}>
                    <fieldset className={`${sectionClassName} ${SECTION_TINT.canales}`}>
                        <legend className={sectionTitleClassName}><BuildingStorefrontIcon className="h-5 w-5" /> Canales de venta</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Dónde publicar el producto y su estado en cada canal (se aplica al guardar).</p>
                        <div className="grid grid-cols-1 items-stretch gap-3 md:grid-cols-2 xl:grid-cols-4">
                            {/* DUX */}
                            {canExportarDux && (
                                <div className={`${canalCardClassName} ${CANAL_TINT.dux} h-full transition-opacity ${subirADux ? "" : "opacity-55"}`}>
                                    <div className="flex items-center gap-3">
                                        <CubeIcon className="h-5 w-5 shrink-0 text-indigo-500" />
                                        <input className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary" type="checkbox" checked={subirADux} onChange={e => setSubirADux(e.target.checked)} id="subirADux" />
                                        <label htmlFor="subirADux" className="flex-1 cursor-pointer select-none">Sincronizar con Dux</label>
                                        <Tooltip content={(
                                            <>
                                                Sube o actualiza en Dux (alta o actualización): título, costo, IVA, rubro/subrubro, marca, proveedor, código de barras, código externo, unidades por bulto (UxB), y habilita o deshabilita según el estado elegido.
                                                <span className="mt-1 block text-red-300">No se suben a Dux: la unidad de medida / sector de depósito (la API de Dux no expone su id), el stock, las imágenes ni el precio de venta (a Dux va el costo).</span>
                                            </>
                                        )} className="flex items-center">
                                            <InformationCircleIcon className="h-4 w-4 shrink-0 text-slate-400 transition-colors hover:text-slate-600 dark:hover:text-slate-200" />
                                        </Tooltip>
                                    </div>
                                    {editandoProductoId && renderEstadoBody(estadoDux?.estado,
                                        estadoToggle(estadoDux?.estado.estado !== "deshabilitado",
                                            on => setEstadoDux(p => p && ({ ...p, estado: { ...p.estado, estado: on ? "habilitado" : "deshabilitado" } })),
                                            !subirADux, "Habilitado", "Deshabilitado"),
                                        estadoDux?.estado.estado ?? undefined, cargandoDux)}
                                </div>
                            )}
                            {/* KT HOGAR */}
                            <div className={`${canalCardClassName} ${CANAL_TINT.hogar} h-full transition-opacity ${subirKtHogar ? "" : "opacity-55"}`}>
                                <div className="flex items-center gap-3">
                                    <HomeIcon className="h-5 w-5 shrink-0 text-sky-500" />
                                    <input className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary" type="checkbox" checked={subirKtHogar} onChange={e => setSubirKtHogar(e.target.checked)} id="subirKtHogar" disabled={!canExportarDux} />
                                    <label htmlFor="subirKtHogar" className="flex-1 cursor-pointer select-none">Sincronizar con KT HOGAR (Nube)</label>
                                    <Tooltip content="Sube o actualiza en Tienda Nube: título, descripción, precio (según el plan de cuotas), categorías e imágenes. El producto se sube oculto (no visible en la tienda); la visibilidad se controla con el estado de esta tarjeta al editar." className="flex items-center">
                                        <InformationCircleIcon className="h-4 w-4 shrink-0 text-slate-400 transition-colors hover:text-slate-600 dark:hover:text-slate-200" />
                                    </Tooltip>
                                </div>
                                {editandoProductoId && renderEstadoBody(estadoHogar?.estado,
                                    estadoToggle(estadoHogar?.estado.estado !== "oculta",
                                        on => setEstadoHogar(p => p && ({ ...p, estado: { ...p.estado, estado: on ? "visible" : "oculta" } })),
                                        !subirKtHogar, "Visible", "Oculta"),
                                    estadoHogar?.estado.estado ?? "visible", cargandoHogar)}
                                <div className="mt-auto flex items-center gap-2 border-t border-slate-200/70 pt-2 dark:border-slate-700/60">
                                    <span className="text-xs font-normal text-slate-500 dark:text-slate-400">Cuota del precio</span>
                                    <Tooltip content="Plan de cuotas del canal con el que se publica el precio en Tienda Nube (cada plan aplica su recargo/descuento de financiación)." className="flex-1">
                                        <select className={`${selectBaseClassName} w-full`} disabled={!subirKtHogar} value={cuotaHogar} onChange={e => setCuotaHogar(Number(e.target.value))}>
                                            {opcionesConSeleccion(cuotasHogarOpts, cuotaHogar).map(c => (
                                                <option key={c.cuotas} value={c.cuotas}>{c.descripcion}</option>
                                            ))}
                                        </select>
                                    </Tooltip>
                                </div>
                            </div>
                            {/* KT GASTRO */}
                            <div className={`${canalCardClassName} ${CANAL_TINT.gastro} h-full transition-opacity ${subirKtGastro ? "" : "opacity-55"}`}>
                                <div className="flex items-center gap-3">
                                    <FireIcon className="h-5 w-5 shrink-0 text-emerald-500" />
                                    <input className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary" type="checkbox" checked={subirKtGastro} onChange={e => setSubirKtGastro(e.target.checked)} id="subirKtGastro" disabled={!canExportarDux} />
                                    <label htmlFor="subirKtGastro" className="flex-1 cursor-pointer select-none">Sincronizar con KT GASTRO (Nube)</label>
                                    <Tooltip content="Sube o actualiza en Tienda Nube: título, descripción, precio (según el plan de cuotas), categorías e imágenes. El producto se sube oculto (no visible en la tienda); la visibilidad se controla con el estado de esta tarjeta al editar." className="flex items-center">
                                        <InformationCircleIcon className="h-4 w-4 shrink-0 text-slate-400 transition-colors hover:text-slate-600 dark:hover:text-slate-200" />
                                    </Tooltip>
                                </div>
                                {editandoProductoId && renderEstadoBody(estadoGastro?.estado,
                                    estadoToggle(estadoGastro?.estado.estado !== "oculta",
                                        on => setEstadoGastro(p => p && ({ ...p, estado: { ...p.estado, estado: on ? "visible" : "oculta" } })),
                                        !subirKtGastro, "Visible", "Oculta"),
                                    estadoGastro?.estado.estado ?? "visible", cargandoGastro)}
                                <div className="mt-auto flex items-center gap-2 border-t border-slate-200/70 pt-2 dark:border-slate-700/60">
                                    <span className="text-xs font-normal text-slate-500 dark:text-slate-400">Cuota del precio</span>
                                    <Tooltip content="Plan de cuotas del canal con el que se publica el precio en Tienda Nube (cada plan aplica su recargo/descuento de financiación)." className="flex-1">
                                        <select className={`${selectBaseClassName} w-full`} disabled={!subirKtGastro} value={cuotaGastro} onChange={e => setCuotaGastro(Number(e.target.value))}>
                                            {opcionesConSeleccion(cuotasGastroOpts, cuotaGastro).map(c => (
                                                <option key={c.cuotas} value={c.cuotas}>{c.descripcion}</option>
                                            ))}
                                        </select>
                                    </Tooltip>
                                </div>
                            </div>
                            {/* MERCADO LIBRE */}
                            <div className={`${canalCardClassName} ${CANAL_TINT.ml} h-full transition-opacity ${subirMl ? "" : "opacity-55"}`}>
                                <div className="flex items-center gap-3">
                                    <ShoppingBagIcon className="h-5 w-5 shrink-0 text-yellow-500" />
                                    <input className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary" type="checkbox" checked={subirMl} onChange={e => setSubirMl(e.target.checked)} id="subirMl" disabled={!canExportarDux} />
                                    <label htmlFor="subirMl" className="flex-1 cursor-pointer select-none">Sincronizar con Mercado Libre</label>
                                    <Tooltip content="Sube o actualiza en Mercado Libre: título (si no tiene ventas), descripción, precio, imágenes. El estado (activa/pausada) se controla con el estado de esta tarjeta al editar. La categoría (la elegida o la que predice ML) se aplica solo al crear; no se modifica en publicaciones existentes." className="flex items-center">
                                        <InformationCircleIcon className="h-4 w-4 shrink-0 text-slate-400 transition-colors hover:text-slate-600 dark:hover:text-slate-200" />
                                    </Tooltip>
                                </div>
                                {editandoProductoId && renderEstadoBody(estadoMl?.estado,
                                    ML_ESTADO_EDITABLE(estadoMl?.estado.estado)
                                        ? estadoToggle(estadoMl?.estado.estado !== "paused",
                                            on => setEstadoMl(p => p && ({ ...p, estado: { ...p.estado, estado: on ? "active" : "paused" } })),
                                            !subirMl, "Activa", "Pausada")
                                        : null,
                                    estadoMl?.estado.estado ?? "active", cargandoMl)}
                                <div className="mt-auto flex items-center gap-2 border-t border-slate-200/70 pt-2 dark:border-slate-700/60">
                                    <span className="text-xs font-normal text-slate-500 dark:text-slate-400">Cuota del precio</span>
                                    <Tooltip content="Plan de cuotas con el que se publica el precio en Mercado Libre (cada plan aplica su recargo de financiación)." className="flex-1">
                                        <select className={`${selectBaseClassName} w-full`} disabled={!subirMl} value={cuotaMl} onChange={e => setCuotaMl(Number(e.target.value))}>
                                            {opcionesConSeleccion(cuotasMlOpts, cuotaMl).map(c => (
                                                <option key={c.cuotas} value={c.cuotas}>{c.descripcion}</option>
                                            ))}
                                        </select>
                                    </Tooltip>
                                </div>
                            </div>
                        </div>
                        {(() => {
                            const avisos = imagenesDetectadas.flatMap((img) => {
                                const a: string[] = [];
                                if (img.bytes > MAX_BYTES_IMG) a.push(`${img.nombre} supera 10 MB — no se subirá`);
                                if (subirMl && !EXT_ML.has(img.extension)) a.push(`${img.nombre} — Mercado Libre no acepta .${img.extension}`);
                                if ((subirKtHogar || subirKtGastro) && !EXT_NUBE.has(img.extension)) a.push(`${img.nombre} — Tienda Nube no acepta .${img.extension}`);
                                return a;
                            });
                            if (!avisos.length) return null;
                            return (
                                <div className="mt-3 space-y-0.5 text-xs">
                                    {avisos.map((a, i) => (
                                        <div key={i} className="text-amber-600 dark:text-amber-400">&#9888; {a}</div>
                                    ))}
                                </div>
                            );
                        })()}
                    </fieldset>

                    <fieldset className={`${sectionClassName} ${SECTION_TINT.identificacion}`}>
                        <legend className={sectionTitleClassName}><IdentificationIcon /> Identificación</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Datos principales para reconocer el producto en la tabla y en web.</p>
                        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
                            {/* Estado y atributos del producto */}
                            <div className={checkboxCardClassName}>
                                <CheckCircleIcon className="h-5 w-5 shrink-0 text-emerald-500" />
                                <input className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary" type="checkbox" checked={activo} onChange={e => setActivo(e.target.checked)} id="activo" />
                                <label htmlFor="activo" className="cursor-pointer select-none">Activo</label>
                            </div>
                            <div className="flex flex-col items-start justify-center gap-1 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm font-semibold text-slate-700 dark:border-slate-700 dark:bg-slate-800/80 dark:text-slate-200">
                                <div className="flex items-center gap-3">
                                    <Squares2X2Icon className="h-5 w-5 shrink-0 text-violet-500" />
                                    <input className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary" type="checkbox" checked={esCombo} onChange={e => handleToggleCombo(e.target.checked)} id="esCombo" />
                                    <label htmlFor="esCombo" className="cursor-pointer select-none">Es Combo</label>
                                </div>
                                {editandoProductoId && esCombo !== esComboOriginal && (
                                    <p className="w-full text-xs font-medium text-amber-600 dark:text-amber-400">
                                        ⚠ Cambiar "Es combo" no modifica el SKU: se mantiene el actual y no se reasigna al rango de SKUs de simple/combo.
                                    </p>
                                )}
                            </div>
                            {/* Identificadores */}
                            <label className="block">
                                <span className="mb-1 block text-sm font-bold tracking-wide text-indigo-600 dark:text-indigo-400">SKU <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span></span>
                                <input type="text" disabled={!!editandoProductoId} className={`${inputBaseClassName} border-l-4 border-l-indigo-500 font-mono font-bold tracking-wide ${editandoProductoId ? "cursor-not-allowed border-slate-300 bg-slate-100 font-semibold text-slate-800 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-100" : ""} ${formErrors.sku ? inputErrorClassName : (skuYaExiste ? "border-amber-400 bg-amber-50 dark:bg-amber-900/20" : "")}`} value={sku} onChange={e => { setSku(e.target.value); if (formErrors.sku) setFormErrors(p => ({ ...p, sku: "" })); }} placeholder="Ej: CUT-001" autoFocus required />
                                {formErrors.sku
                                    ? <p className="mt-1 text-xs text-red-500">{formErrors.sku}</p>
                                    : skuYaExiste && <p className="mt-1 text-xs font-medium text-amber-600 dark:text-amber-400">⚠ Ya existe un producto con este SKU</p>}
                            </label>
                            <label className="block">
                                <span className={fieldLabelClassName}>UxB</span>
                                <input type="number" min={1} className={`${inputBaseClassName} ${formErrors.uxb ? inputErrorClassName : ""}`} value={uxb} onChange={e => { setUxb(Number(e.target.value)); if (formErrors.uxb) setFormErrors(p => ({ ...p, uxb: "" })); }} />
                                {formErrors.uxb && <p className="mt-1 text-xs text-red-500">{formErrors.uxb}</p>}
                            </label>
                            <label className="block">
                                <span className={fieldLabelClassName}>Cód. Ext.</span>
                                <input type="text" className={inputBaseClassName} value={codExt} onChange={e => setCodExt(e.target.value)} placeholder="Ej: 2000" />
                            </label>
                            <label className="block xl:col-span-4">
                                <span className={fieldLabelClassName}>Título Dux <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span></span>
                                <input type="text" className={`${inputBaseClassName} ${formErrors.tituloDux ? inputErrorClassName : ""}`} value={tituloDux} onChange={e => { setTituloDux(e.target.value); if (formErrors.tituloDux) setFormErrors(p => ({ ...p, tituloDux: "" })); }} placeholder="Título principal (Dux)" required />
                                {formErrors.tituloDux && <p className="mt-1 text-xs text-red-500">{formErrors.tituloDux}</p>}
                            </label>
                            <label className="block xl:col-span-4">
                                <span className={fieldLabelClassName}>Título Nube (base){(subirKtHogar || subirKtGastro) && <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span>}</span>
                                <input type="text" className={`${inputBaseClassName} ${formErrors.tituloNube ? inputErrorClassName : ""}`} value={tituloNube}
                                    onChange={e => {
                                        const v = e.target.value;
                                        // En alta, el título base se espeja a cada canal mientras el usuario no los haya editado a mano.
                                        if (!editandoProductoId) {
                                            setTituloHogar(prev => (prev === tituloNube ? v : prev));
                                            setTituloGastro(prev => (prev === tituloNube ? v : prev));
                                        }
                                        setTituloNube(v);
                                        if (formErrors.tituloNube) setFormErrors(p => ({ ...p, tituloNube: "" }));
                                    }} placeholder="Título base para Tienda Nube" />
                                {formErrors.tituloNube && <p className="mt-1 text-xs text-red-500">{formErrors.tituloNube}</p>}
                                <span className="mt-0.5 block text-[11px] text-slate-400 dark:text-slate-500">Se usa para Excel/PDF/Dux/SEO. Al crear se copia a KT HOGAR y KT GASTRO; cada canal puede ajustarlo.</span>
                            </label>

                            {/* Imágenes del SKU (solo lectura; click → carousel con todas) */}
                            <div className="block xl:col-span-4">
                                <div className="flex items-center gap-1.5">
                                    <span className={fieldLabelClassName}>Imágenes locales</span>
                                    {imagenesDetectadas.length > 0 && (
                                        <span className="inline-flex items-center justify-center rounded-full bg-blue-100 px-2 py-0.5 text-xs font-semibold text-blue-700 dark:bg-blue-900/40 dark:text-blue-300" title={`${imagenesDetectadas.length} imagen${imagenesDetectadas.length === 1 ? "" : "es"} detectada${imagenesDetectadas.length === 1 ? "" : "s"} para este SKU`}>
                                            {imagenesDetectadas.length}
                                        </span>
                                    )}
                                    <Tooltip content={
                                        <div className="space-y-1 text-sm">
                                            <p>Las imágenes se asocian automáticamente por SKU: el sistema toma de la carpeta de imágenes los archivos cuyo nombre coincide con el SKU del producto. No se cargan a mano desde acá — para agregar o cambiar fotos, poné los archivos en esa carpeta nombrados con el SKU y se detectan solos. Click en una miniatura para verlas todas.</p>
                                            {(() => {
                                                const ej = sku.trim() || "SKU";
                                                const codeCls = "rounded bg-slate-100 px-1 font-mono text-xs text-slate-700 dark:bg-slate-700 dark:text-slate-200";
                                                return (
                                                    <div className="border-t border-slate-200 pt-1 dark:border-slate-700">
                                                        <p><strong>Nombre y orden de los archivos:</strong></p>
                                                        <ul className="ml-4 list-disc space-y-0.5">
                                                            <li><strong>Carátula</strong> (imagen principal, va primera): archivo llamado exactamente como el SKU, <code className={codeCls}>{ej}.jpg</code></li>
                                                            <li><strong>Adicionales:</strong> <code className={codeCls}>{ej}_1.jpg</code>, <code className={codeCls}>{ej}_2.jpg</code>, … Se ordenan por ese número ascendente ({ej}_1 antes que {ej}_2).</li>
                                                        </ul>
                                                        <p className="mt-1">Formatos que se buscan: <strong>JPG, JPEG, PNG, GIF, WEBP, BMP, SVG</strong>. Si un mismo archivo existe en varios formatos, se usa el primero de esa lista (JPG antes que PNG, etc.). No distingue mayúsculas de minúsculas.</p>
                                                    </div>
                                                );
                                            })()}
                                            <div className="border-t border-slate-200 pt-1 dark:border-slate-700">
                                                <p><strong>Carpeta destino:</strong> imágenes finales, listas para publicar ({crudasDisp?.destinoDir.ruta ?? "app.imagenes-dir"}).</p>
                                                <p><strong>Carpeta de entrada (crudas):</strong> imágenes sin procesar, base para generar la carátula ({crudasDisp?.crudaDir.ruta ?? "app.imagenes-raw-dir"}).</p>
                                            </div>
                                        </div>
                                    } className="flex items-center">
                                        <InformationCircleIcon className="h-4 w-4 shrink-0 text-slate-400 transition-colors hover:text-slate-600 dark:hover:text-slate-200" />
                                    </Tooltip>
                                </div>
                                {formErrors.imagenesMl && <p className="mt-1 text-xs font-medium text-red-500">{formErrors.imagenesMl}</p>}
                                <div className="mt-1 rounded-2xl border border-slate-200 bg-slate-50/80 p-3 dark:border-slate-700 dark:bg-slate-800/70">
                                    {imagenesDetectadas.length === 0 ? (
                                        <div className="space-y-1 text-xs text-amber-600 dark:text-amber-400">
                                            <div className="font-medium">&#9888; No hay imágenes para este SKU en la carpeta.</div>
                                            {(subirMl || subirKtHogar || subirKtGastro) && (
                                                <div>Mercado Libre y Tienda Nube requieren al menos una imagen; la subida a esos canales puede fallar o quedar sin foto.</div>
                                            )}
                                        </div>
                                    ) : (
                                        <div className="flex flex-wrap gap-2">
                                            {imagenesDetectadas.map((img) => (
                                                <button key={img.nombre} type="button" onClick={() => setCarouselSku(sku.trim())}
                                                    className="h-16 w-16 overflow-hidden rounded-xl border border-slate-200 hover:border-blue-400 dark:border-slate-700" title={img.nombre}>
                                                    <img src={`${API_BASE_URL}/api/imagenes/${img.nombre}?v=${caratulaCacheBust}`} alt={img.nombre} loading="lazy" className="h-full w-full bg-white object-contain" onError={(e) => { (e.target as HTMLImageElement).style.display = "none"; }} />
                                                </button>
                                            ))}
                                        </div>
                                    )}
                                </div>
                                {editandoProductoId && (
                                    <div className="mt-2">
                                        {!caratulaPreview && !selectorCaratulaAbierto && (
                                            <div className="flex flex-wrap items-center gap-2">
                                                <Button variant="dark" onClick={abrirSelectorCaratula} disabled={generandoCaratula}>
                                                    <SparklesIcon className="h-4 w-4" /> Mejorar carátula con IA
                                                </Button>
                                                {modelImagen && (
                                                    <span className="ml-2 text-xs text-slate-500 dark:text-slate-400">
                                                        Modelo: {modelImagen}{" "}
                                                        <a href="/config-ia?tab=caratula" target="_blank" rel="noreferrer" className="text-blue-600 hover:underline dark:text-blue-400">config</a>
                                                    </span>
                                                )}
                                            </div>
                                        )}
                                        {selectorCaratulaAbierto && !caratulaPreview && (
                                            <div className="mt-3 rounded-2xl border border-slate-200 p-3 dark:border-slate-700">
                                                {!crudasDisp ? (
                                                    <div className="flex items-center gap-2 text-xs text-slate-400"><SpinnerIcon /> Leyendo carpeta cruda…</div>
                                                ) : (
                                                    <>
                                                        <div className="mb-2 text-xs text-slate-500 dark:text-slate-400">
                                                            <div>{crudasDisp.crudaDir.existe && crudasDisp.crudaDir.legible ? "✓" : "⚠"} Carpeta cruda: {crudasDisp.crudaDir.ruta} {crudasDisp.crudaDir.legible ? "(lectura OK)" : "(sin acceso de lectura)"}</div>
                                                            <div>{crudasDisp.destinoDir.escribible ? "✓" : "⚠"} Carpeta destino: {crudasDisp.destinoDir.escribible ? "escritura OK" : "sin acceso de escritura"}</div>
                                                        </div>
                                                        {generandoCaratula ? (
                                                            <div className="flex items-center gap-2 px-1 py-3 text-sm text-slate-500"><SpinnerIcon /> {faseCaratula || "Generando…"} {transcurridoCaratula > 0 && <span className="tabular-nums text-slate-400">· {fmtDuracion(transcurridoCaratula)}</span>}</div>
                                                        ) : crudasDisp.imagenes.length === 0 ? (
                                                            <p className="text-xs text-amber-600 dark:text-amber-400">No hay imágenes crudas para este SKU en la carpeta.</p>
                                                        ) : (
                                                            <>
                                                            <p className="mb-2 text-xs font-medium text-slate-600 dark:text-slate-300">Seleccioná una imagen para generar la carátula:</p>
                                                            <div className="flex flex-wrap gap-2">
                                                                {crudasDisp.imagenes.map(nombre => (
                                                                    <button key={nombre} type="button" onClick={() => generarCaratula(nombre)} title={nombre}
                                                                        className="h-20 w-20 overflow-hidden rounded-xl border border-slate-200 hover:border-blue-400 dark:border-slate-700">
                                                                        <img src={crudaMiniaturaURL(nombre)} alt={nombre} loading="lazy" className="h-full w-full bg-white object-contain" />
                                                                    </button>
                                                                ))}
                                                            </div>
                                                            </>
                                                        )}
                                                        {!generandoCaratula && (
                                                            <div className="mt-2 flex justify-end">
                                                                <Button variant="light" onClick={() => setSelectorCaratulaAbierto(false)}>Cerrar</Button>
                                                            </div>
                                                        )}
                                                    </>
                                                )}
                                            </div>
                                        )}
                                        {caratulaPreview && (
                                            <div className="mt-3 rounded-2xl border border-slate-200 p-3 dark:border-slate-700">
                                                <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-center">
                                                    <figure className="flex-1">
                                                        <figcaption className="mb-1 text-center text-xs text-slate-500">Original</figcaption>
                                                        {caratulaCruda && <img src={`data:image/${caratulaCrudaFormato};base64,${caratulaCruda}`} alt="Imagen original" className="mx-auto max-h-64" />}
                                                    </figure>
                                                    <figure className="flex-1">
                                                        <figcaption className="mb-1 text-center text-xs text-slate-500">Generada con IA{duracionCaratula != null ? ` · ${fmtDuracion(duracionCaratula)}` : ""}</figcaption>
                                                        <img src={`data:image/${caratulaFormato};base64,${caratulaPreview}`} alt="Carátula generada" className="mx-auto max-h-64" />
                                                    </figure>
                                                </div>
                                                <div className="mt-2 flex justify-end gap-2">
                                                    <Button variant="light" onClick={cancelarCaratula} disabled={guardandoCaratula || generandoCaratula}>Cancelar</Button>
                                                    <Button variant="light" onClick={() => generarCaratula()} disabled={generandoCaratula || guardandoCaratula}>
                                                        {generandoCaratula ? <SpinnerIcon /> : <SparklesIcon className="h-4 w-4" />}
                                                        {generandoCaratula ? "Generando..." : "Volver a generar"}
                                                    </Button>
                                                    <Button variant="dark" onClick={guardarCaratula} disabled={guardandoCaratula || generandoCaratula}>
                                                        {guardandoCaratula ? <SpinnerIcon /> : <CheckIcon className="h-4 w-4" />}
                                                        {guardandoCaratula ? "Guardando..." : "Aceptar"}
                                                    </Button>
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                )}
                            </div>
                        </div>
                        {/* EAN / Código de barras */}
                        <div className="mt-4 border-t border-slate-200/70 pt-4 dark:border-slate-700/70">
                            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
                                <label className="block">
                                    <span className={fieldLabelClassName}>EAN / Código universal</span>
                                    <input type="text" inputMode="numeric" className={inputBaseClassName} value={ean}
                                        onChange={e => setEan(e.target.value)} placeholder="Código de barras (8–14 dígitos)" />
                                    {ean.trim() && !gtinValido(ean) && (
                                        <span className="mt-0.5 block text-[11px] text-amber-600">Código de barras inválido (8/12/13/14 dígitos + dígito verificador). No se enviará a ML.</span>
                                    )}
                                    {/* Comparación con lo que hay hoy en cada canal (el local es el que se envía a todos). */}
                                    {(() => {
                                        const local = ean.trim();
                                        const canales = [
                                            { label: "Mercado Libre", valor: estadoMl?.ean },
                                            { label: "KT HOGAR", valor: estadoHogar?.ean },
                                            { label: "KT GASTRO", valor: estadoGastro?.ean },
                                        ].filter(c => c.valor != null && c.valor !== "");
                                        if (canales.length === 0) return null;
                                        return (
                                            <div className="mt-1.5 space-y-0.5 text-[11px]">
                                                {canales.map(c => {
                                                    const difiere = local !== "" && c.valor!.trim() !== local;
                                                    return (
                                                        <div key={c.label} className={difiere ? "font-medium text-amber-600 dark:text-amber-400" : "text-slate-400 dark:text-slate-500"}>
                                                            {difiere ? "⚠" : "✓"} {c.label}: {c.valor}{difiere ? " — difiere del local (se pisará al publicar)" : ""}
                                                        </div>
                                                    );
                                                })}
                                            </div>
                                        );
                                    })()}
                                </label>
                            </div>
                        </div>
                    </fieldset>

                    <div className="grid grid-cols-1 gap-5 xl:grid-cols-[1.05fr_0.95fr]">
                    <fieldset className={`${sectionClassName} ${SECTION_TINT.economicos}`}>
                        <legend className={sectionTitleClassName}><CurrencyDollarIcon /> Económicos</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Base mínima para costos y cálculo de precios.</p>
                        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                            <label className="block">
                                <span className={fieldLabelClassName}>Costo Base <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span></span>
                                <div className="relative">
                                    <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-sm font-semibold text-slate-400">$</span>
                                    <input type="number" min={0.01} className={`${inputBaseClassName} !pl-7 ${formErrors.costo ? inputErrorClassName : ""}`} value={costo} onChange={e => { setCosto(e.target.value === "" ? "" : Number(e.target.value)); if (formErrors.costo) setFormErrors(p => ({ ...p, costo: "" })); }} required />
                                </div>
                                {formErrors.costo
                                    ? <p className="mt-1 text-xs text-red-500">{formErrors.costo}</p>
                                    : esCombo && <p className="mt-1 text-xs text-amber-600 dark:text-amber-400">En combos, el costo no se envía a Dux (Dux lo rechaza); sí se usa para el precio y los demás canales.</p>}
                            </label>
                            <label className="block">
                                <span className={fieldLabelClassName}>IVA (%) <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span></span>
                                <select
                                    className={selectBaseClassName}
                                    value={iva}
                                    onChange={e => setIva(Number(e.target.value))}
                                    required
                                >
                                    <option value={21}>21%</option>
                                    <option value={10.5}>10,5%</option>
                                </select>
                            </label>
                        </div>
                        {producto && (
                            <div className="mt-3 border-t border-slate-200/70 pt-2.5 text-xs text-slate-400 dark:border-slate-700/60 dark:text-slate-500">
                                Últ. costo: <span className="font-medium text-slate-500 dark:text-slate-400">{formatFechaAR(producto.fechaUltimoCosto)}</span>
                            </div>
                        )}
                    </fieldset>

                    <fieldset className={`${sectionClassName} ${SECTION_TINT.margenes}`}>
                        <legend className={sectionTitleClassName}><ReceiptPercentIcon /> Márgenes</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Márgenes minorista y mayorista (porcentaje).{!esCombo ? " Al menos uno obligatorio." : " Opcionales para combos."}</p>
                        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                            <label className="block">
                                <span className={fieldLabelClassName}>Margen minorista (%)</span>
                                <input type="number" step={0.5} className={`${inputBaseClassName} ${formErrors.margen ? inputErrorClassName : ""}`} value={margenMinorista} onChange={e => { setMargenMinorista(e.target.value === "" ? "" : Number(e.target.value)); if (formErrors.margen) setFormErrors(p => ({ ...p, margen: "" })); }} placeholder="Sin definir" />
                            </label>
                            <label className="block">
                                <span className={fieldLabelClassName}>Margen mayorista (%)</span>
                                <input type="number" step={0.5} className={`${inputBaseClassName} ${formErrors.margen ? inputErrorClassName : ""}`} value={margenMayorista} onChange={e => { setMargenMayorista(e.target.value === "" ? "" : Number(e.target.value)); if (formErrors.margen) setFormErrors(p => ({ ...p, margen: "" })); }} placeholder="Sin definir" />
                            </label>
                        </div>
                        {formErrors.margen && <p className="mt-2 text-xs text-red-500">{formErrors.margen}</p>}
                    </fieldset>
                    </div>

                    <fieldset className={`${sectionClassName} ${SECTION_TINT.reposicion}`}>
                        <legend className={sectionTitleClassName}><ArchiveBoxIcon /> Reposición y Stock</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Disponibilidad inicial y prioridades de compra.</p>
                        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
                            <label className="flex flex-col">
                                <span className={fieldLabelClassName}>Stock inicial</span>
                                <div className="mt-auto">
                                    <input type="number" min={0} className={inputBaseClassName} value={stock} onChange={e => setStock(e.target.value === "" ? "" : Number(e.target.value))} placeholder="0" />
                                </div>
                            </label>
                            <label className="flex flex-col">
                                <span className={fieldLabelClassName}>MOQ (mín. pedido)</span>
                                <div className="mt-auto">
                                    <input type="number" min={0} className={inputBaseClassName} value={moq} onChange={e => setMoq(e.target.value === "" ? "" : Number(e.target.value))} placeholder="0" />
                                </div>
                            </label>
                            <label className="flex flex-col">
                                <span className={fieldLabelClassName}>Prioridad de reposición</span>
                                <div className="mt-auto">
                                    <select className={selectBaseClassName} value={tagReposicion} onChange={e => setTagReposicion(e.target.value as "" | "PRIO" | "LIQ")}>
                                        <option value="">Sin tag</option>
                                        <option value="PRIO">PRIO — Prioritaria</option>
                                        <option value="LIQ">LIQ — Liquidación</option>
                                    </select>
                                </div>
                            </label>
                        </div>
                    </fieldset>

                    <fieldset className={`${sectionClassName} ${SECTION_TINT.clasificacion}`}>
                        <legend className={sectionTitleClassName}><Squares2X2Icon /> Clasificación y Relaciones</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Asociaciones maestras para filtros, navegación y reglas del sistema.</p>
                        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
                            <div>
                                <AsyncSelect label={<>Marca {!esCombo && <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span>}</>} loadOptions={searchMarcas} onChange={(v, label) => { setMarcaId(v ? Number(v) : null); setMarcaDisplay(v ? (label ?? "") : ""); if (formErrors.marcaId) setFormErrors(p => ({ ...p, marcaId: "" })); }} value={marcaId} displayValue={marcaDisplay} placeholder="Buscar marca" inputClassName={`${inputBaseClassName} ${formErrors.marcaId ? inputErrorClassName : ""}`} />
                                {formErrors.marcaId && <p className="mt-1 text-xs text-red-500">{formErrors.marcaId}</p>}
                            </div>
                            <div>
                                <AsyncSelect label={<>Origen {!esCombo && <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span>}</>} loadOptions={searchOrigenes} onChange={(v, label) => { setOrigenId(v ? Number(v) : null); setOrigenDisplay(v ? (label ?? "") : ""); if (formErrors.origenId) setFormErrors(p => ({ ...p, origenId: "" })); }} value={origenId} displayValue={origenDisplay} placeholder="Buscar origen" inputClassName={`${inputBaseClassName} ${formErrors.origenId ? inputErrorClassName : ""}`} />
                                {formErrors.origenId && <p className="mt-1 text-xs text-red-500">{formErrors.origenId}</p>}
                            </div>
                            <div>
                                <AsyncSelect label={<>Clasif. Gral <span className="ml-1 rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-amber-700 dark:bg-amber-900/30 dark:text-amber-300">al menos una</span> <Tooltip content={<div className="space-y-1 text-left"><div className="font-medium">A qué canal va esta clasificación:</div><div><span className="font-semibold">Dux:</span> rubro (nivel 1) y subrubro (nivel 2). Si el producto tiene clasif. general y gastronómica, Dux usa la <span className="font-semibold">general</span>.</div><div><span className="font-semibold">Tienda Nube KT HOGAR:</span> arma las categorías (toda la jerarquía) + el Tipo al final. KT GASTRO usa la gastronómica, no esta.</div><div><span className="font-semibold">Mercado Libre:</span> no la usa (su categoría sale del predictor).</div></div>} className="inline-flex align-middle"><InformationCircleIcon className="ml-0.5 inline h-3.5 w-3.5 shrink-0 align-middle text-slate-400 transition-colors hover:text-slate-600 dark:hover:text-slate-200" /></Tooltip></>} loadOptions={searchClasifGral} onChange={(v, label) => { setClasifGralId(v ? Number(v) : null); setClasifGralDisplay(v ? (label ?? "") : ""); if (formErrors.clasificacion) setFormErrors(p => ({ ...p, clasificacion: "" })); }} value={clasifGralId} displayValue={clasifGralDisplay} placeholder="Buscar clasificación" inputClassName={`${inputBaseClassName} ${formErrors.clasificacion ? inputErrorClassName : ""}`} />
                                {formErrors.clasificacion && <p className="mt-1 text-xs text-red-500">{formErrors.clasificacion}</p>}
                            </div>
                            <div>
                                <AsyncSelect label={<>Clasif. Gastro <span className="ml-1 rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-amber-700 dark:bg-amber-900/30 dark:text-amber-300">al menos una</span> <Tooltip content={<div className="space-y-1 text-left"><div className="font-medium">A qué canal va esta clasificación:</div><div><span className="font-semibold">Dux:</span> rubro (nivel 1) y subrubro (nivel 2), <span className="font-semibold">solo si no hay clasif. general</span> (si tiene ambas, Dux usa la general).</div><div><span className="font-semibold">Tienda Nube KT GASTRO:</span> arma las categorías (toda la jerarquía) + el Tipo al final. KT HOGAR usa la general, no esta.</div><div><span className="font-semibold">Mercado Libre:</span> no la usa.</div></div>} className="inline-flex align-middle"><InformationCircleIcon className="ml-0.5 inline h-3.5 w-3.5 shrink-0 align-middle text-slate-400 transition-colors hover:text-slate-600 dark:hover:text-slate-200" /></Tooltip></>} loadOptions={searchClasifGastro} onChange={(v, label) => { setClasifGastroId(v ? Number(v) : null); setClasifGastroDisplay(v ? (label ?? "") : ""); if (formErrors.clasificacion) setFormErrors(p => ({ ...p, clasificacion: "" })); }} value={clasifGastroId} displayValue={clasifGastroDisplay} placeholder="Buscar clasificación" inputClassName={`${inputBaseClassName} ${formErrors.clasificacion ? inputErrorClassName : ""}`} />
                            </div>
                            <div>
                                <AsyncSelect label={<>Tipo <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span> <Tooltip content={<div className="space-y-1 text-left"><div className="font-medium">A qué canal va el tipo:</div><div><span className="font-semibold">Tienda Nube (KT HOGAR y KT GASTRO):</span> se agrega al final de las categorías, después de la clasificación.</div><div><span className="font-semibold">Dux:</span> no lo usa.</div><div><span className="font-semibold">Mercado Libre:</span> no lo usa.</div></div>} className="inline-flex align-middle"><InformationCircleIcon className="ml-0.5 inline h-3.5 w-3.5 shrink-0 align-middle text-slate-400 transition-colors hover:text-slate-600 dark:hover:text-slate-200" /></Tooltip></>} loadOptions={searchTipos} onChange={(v, label) => { setTipoId(v ? Number(v) : null); setTipoDisplay(v ? (label ?? "") : ""); if (formErrors.tipoId) setFormErrors(p => ({ ...p, tipoId: "" })); }} value={tipoId} displayValue={tipoDisplay} placeholder="Buscar tipo" inputClassName={`${inputBaseClassName} ${formErrors.tipoId ? inputErrorClassName : ""}`} />
                                {formErrors.tipoId && <p className="mt-1 text-xs text-red-500">{formErrors.tipoId}</p>}
                            </div>
                            <div>
                                <AsyncSelect label={<>Proveedor {!esCombo && <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span>}</>} loadOptions={searchProveedores} onChange={(v, label) => { setProveedorId(v ? Number(v) : null); setProveedorDisplay(v ? (label ?? "") : ""); if (formErrors.proveedorId) setFormErrors(p => ({ ...p, proveedorId: "" })); }} value={proveedorId} displayValue={proveedorDisplay} placeholder="Buscar proveedor" inputClassName={`${inputBaseClassName} ${formErrors.proveedorId ? inputErrorClassName : ""}`} />
                                {formErrors.proveedorId && <p className="mt-1 text-xs text-red-500">{formErrors.proveedorId}</p>}
                            </div>
                            <div>
                                <AsyncSelect label={<>Material {!esCombo && <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span>}</>} loadOptions={searchMateriales} onChange={(v, label) => { setMaterialId(v ? Number(v) : null); setMaterialDisplay(v ? (label ?? "") : ""); if (formErrors.materialId) setFormErrors(p => ({ ...p, materialId: "" })); }} value={materialId} displayValue={materialDisplay} placeholder="Buscar material" inputClassName={`${inputBaseClassName} ${formErrors.materialId ? inputErrorClassName : ""}`} />
                                {formErrors.materialId && <p className="mt-1 text-xs text-red-500">{formErrors.materialId}</p>}
                            </div>
                            <div>
                                <AsyncSelect
                                    label={<>Sector de depósito <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span></>}
                                    loadOptions={searchSectoresDeposito}
                                    onChange={(v, label) => { setSectorDepositoId(v ? Number(v) : null); setSectorDepositoDisplay(v ? (label ?? "") : ""); if (formErrors.sectorDeposito) setFormErrors(p => ({ ...p, sectorDeposito: "" })); }}
                                    value={sectorDepositoId}
                                    displayValue={sectorDepositoDisplay}
                                    placeholder="Buscar sector (T1, COMBOS, ...)"
                                    inputClassName={`${inputBaseClassName} ${formErrors.sectorDeposito ? inputErrorClassName : ""}`}
                                />
                                {formErrors.sectorDeposito && <p className="mt-1 text-xs text-red-500">{formErrors.sectorDeposito}</p>}
                            </div>
                            <label className="block">
                                <span className={fieldLabelClassName}>Tag {!esCombo && <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span>}</span>
                                <select className={`${selectBaseClassName} ${formErrors.tag ? inputErrorClassName : ""}`} value={tag} onChange={e => { setTag(e.target.value as "" | "MAQUINA" | "REPUESTO" | "MENAJE" | "INSUMO"); if (formErrors.tag) setFormErrors(p => ({ ...p, tag: "" })); }}>
                                    <option value="">-- Seleccionar --</option>
                                    <option value="MAQUINA">Máquina</option>
                                    <option value="REPUESTO">Repuesto</option>
                                    <option value="MENAJE">Menaje</option>
                                    <option value="INSUMO">Insumo</option>
                                </select>
                                {formErrors.tag && <p className="mt-1 text-xs text-red-500">{formErrors.tag}</p>}
                            </label>
                        </div>
                    </fieldset>

                    <fieldset className={`${sectionClassName} ${SECTION_TINT.catalogos}`}>
                        <legend className={sectionTitleClassName}><UserGroupIcon /> Catálogos y Segmentos</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Asociaciones múltiples. Buscá y agregá los que correspondan.</p>
                        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                            <MultiAsyncSelect label="Catálogos" loadOptions={(q) => searchCatalogos(q)} value={catalogosSel} onChange={setCatalogosSel} placeholder="Buscar catálogo" inputClassName={inputBaseClassName} />
                            {clasifGastroId != null && (
                                <MultiAsyncSelect label="Segmentos" loadOptions={(q) => searchSegmentos(q)} value={segmentosSel} onChange={setSegmentosSel} placeholder="Segmentos (panadería, restaurant, …)" inputClassName={inputBaseClassName} />
                            )}
                        </div>
                    </fieldset>

                    <fieldset className={`${sectionClassName} ${SECTION_TINT.dimensiones}`}>
                        <legend className={sectionTitleClassName}><CubeIcon /> Dimensiones Físicas</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Medidas y atributos técnicos para logística y catálogo.</p>
                        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
                            {renderFisico("capacidad", "Capacidad")}
                            {renderFisico("largo", "Largo")}
                            {renderFisico("ancho", "Ancho")}
                            {renderFisico("alto", "Alto")}
                            {renderFisico("diamboca", "Diám. Boca")}
                            {renderFisico("diambase", "Diám. Base")}
                            {renderFisico("espesor", "Espesor")}
                            <div className="md:col-span-2 xl:col-span-4">
                                <MultiAsyncSelect label="Aptos" loadOptions={(q) => searchAptos(q)} value={aptosSel} onChange={setAptosSel} placeholder="Buscar apto" inputClassName={inputBaseClassName} chipClassName="border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-800 dark:bg-emerald-900/30 dark:text-emerald-300" />
                            </div>
                        </div>
                    </fieldset>

                    {subirMl && (
                    <fieldset className={`${sectionClassName} ${SECTION_TINT.ml}`}>
                        <legend className={sectionTitleClassName}><ShoppingBagIcon /> MercadoLibre</legend>
                        <p className={`${sectionDescriptionClassName} mb-2`}>Publicación de MercadoLibre (MLA) asociada al producto.</p>
                        {mlaResuelto && (
                            <a href={mlEditarURL(mlaResuelto)} target="_blank" rel="noreferrer" className="mb-4 inline-flex items-center gap-1 text-xs font-medium text-blue-600 hover:underline dark:text-blue-400">Editar en ML ↗</a>
                        )}
                        {!editandoProductoId && (
                            <div className="mb-4">
                                <VariantesSection
                                    tiene={tieneVariantes} onTiene={setTieneVariantes}
                                    ejeOpciones={ejeOpciones}
                                    ejeAtributoId={ejeAtributoId} onEje={setEjeAtributoId}
                                    ejeValorBase={ejeValorBase} ejeValorBaseId={ejeValorBaseId}
                                    onEjeValorBase={(id, nombre) => { setEjeValorBaseId(id); setEjeValorBase(nombre); }}
                                    skuBase={sku}
                                    cuotasMlOpts={cuotasMlOpts} cuotasHogarOpts={cuotasHogarOpts} cuotasGastroOpts={cuotasGastroOpts}
                                    cuotasDefault={{ ml: cuotaMl, hogar: cuotaHogar, gastro: cuotaGastro }}
                                    variantes={variantesBorrador} onVariantes={setVariantesBorrador}
                                    subirMl={subirMl} subirKtHogar={subirKtHogar} subirKtGastro={subirKtGastro}
                                    inputCls={inputBaseClassName} selectCls={selectBaseClassName}
                                />
                            </div>
                        )}
                        {editandoProductoId && familia && familia.variantes.length > 1 && (
                            <div className="mb-4 rounded-2xl border border-slate-200 bg-white/60 p-3 dark:border-slate-700 dark:bg-slate-800/40">
                                <div className="mb-2 flex flex-wrap items-center gap-2 text-sm font-semibold text-slate-700 dark:text-slate-200">
                                    Familia de variantes{familia.familyName ? ` · ${familia.familyName}` : ""}
                                    <span className="text-xs font-normal text-slate-400">({familia.variantes.length}{familia.ejeNombre ? ` · eje: ${familia.ejeNombre}` : ""})</span>
                                    {familia.modelo === "LEGACY" && <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-medium text-amber-700 dark:bg-amber-900/40 dark:text-amber-300">legacy</span>}
                                </div>
                                <ul className="space-y-1">
                                    {familia.variantes.map((v, i) => (
                                        <li key={v.productoId ?? v.sku ?? i} className={`flex items-center gap-2 text-xs ${v.esActual ? "font-semibold text-slate-800 dark:text-slate-100" : "text-slate-600 dark:text-slate-300"}`}>
                                            <span className="rounded bg-slate-100 px-1.5 py-0.5 font-mono dark:bg-slate-700">{v.sku ?? "—"}</span>
                                            {v.ejeValor && <span className="rounded bg-blue-50 px-1.5 py-0.5 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300">{v.ejeValor}</span>}
                                            {v.stock != null && <span className="text-[10px] text-slate-400">stock {v.stock}</span>}
                                            {v.status && <span className="text-[10px] text-slate-400">{v.status}</span>}
                                            {v.esActual && <span className="text-[10px] text-blue-500">(este)</span>}
                                            <span className="ml-auto flex items-center gap-2">
                                                {!v.esActual && v.productoId != null && onEditarOtro && (
                                                    <button type="button" onClick={() => onEditarOtro(v.productoId!)} className="text-[10px] font-medium text-blue-600 hover:underline dark:text-blue-400">Editar</button>
                                                )}
                                                {familia.modelo === "NUEVO" && v.productoId != null && (
                                                    quitandoId === v.productoId ? (
                                                        <span className="flex items-center gap-1">
                                                            <span className="text-[10px] text-slate-500">¿Pausar y quitar?</span>
                                                            <button type="button" disabled={isSaving} onClick={() => handleQuitarDeFamilia(v.productoId!)} className="rounded bg-red-500 px-1.5 py-0.5 text-[10px] font-semibold text-white disabled:opacity-50">Sí</button>
                                                            <button type="button" disabled={isSaving} onClick={() => setQuitandoId(null)} className="rounded border border-slate-300 px-1.5 py-0.5 text-[10px] dark:border-slate-600">No</button>
                                                        </span>
                                                    ) : (
                                                        <button type="button" onClick={() => setQuitandoId(v.productoId)} className="text-[10px] font-medium text-red-500 hover:underline">Quitar</button>
                                                    )
                                                )}
                                            </span>
                                        </li>
                                    ))}
                                </ul>
                                {familia.modelo === "NUEVO" && (!agregandoVariante ? (
                                    <button type="button" onClick={() => setAgregandoVariante(true)}
                                        className="mt-2 rounded-lg border border-dashed border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-300">+ Agregar variante</button>
                                ) : (
                                    <div className="mt-2 rounded-xl border border-slate-200 bg-slate-50/70 p-2.5 dark:border-slate-700 dark:bg-slate-800/60">
                                        <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                                            <label className="block"><span className="text-[11px] text-slate-500">Eje</span>
                                                <select className={`${selectBaseClassName} w-full`} value={ejeAtributoId} onChange={e => setEjeAtributoId(e.target.value)}>
                                                    <option value="">— elegir eje —</option>
                                                    {ejeOpciones.map(o => <option key={o.id} value={o.id}>{o.name}</option>)}
                                                </select></label>
                                            <label className="block"><span className="text-[11px] text-slate-500">Valor del eje</span>
                                                {(() => {
                                                    const vals = ejeOpciones.find(o => o.id === ejeAtributoId)?.values ?? [];
                                                    return vals.length > 0 ? (
                                                        <select className={`${selectBaseClassName} w-full`} value={nvEjeValorId ?? nvEjeValorNombre} onChange={e => { const opt = vals.find(x => (x.id ?? x.name) === e.target.value); setNvEjeValorId(opt?.id ?? null); setNvEjeValorNombre(opt?.name ?? ""); }}>
                                                            <option value="">— elegir —</option>
                                                            {vals.map(x => <option key={x.id ?? x.name} value={x.id ?? x.name}>{x.name}</option>)}
                                                        </select>
                                                    ) : (
                                                        <input className={inputBaseClassName} value={nvEjeValorNombre} onChange={e => { setNvEjeValorId(null); setNvEjeValorNombre(e.target.value); }} placeholder="Valor del eje" />
                                                    );
                                                })()}</label>
                                            <label className="block"><span className="text-[11px] text-slate-500">SKU</span>
                                                <input className={inputBaseClassName} value={nvSku} onChange={e => setNvSku(e.target.value)} placeholder="SKU de la variante" /></label>
                                            <label className="block"><span className="text-[11px] text-slate-500">Stock</span>
                                                <input type="number" min={0} step={1} className={inputBaseClassName} value={nvStock} onChange={e => setNvStock(e.target.value === "" ? "" : Number(e.target.value))} placeholder="0" /></label>
                                            <label className="block"><span className="text-[11px] text-slate-500">EAN</span>
                                                <input className={inputBaseClassName} value={nvEan} onChange={e => setNvEan(e.target.value)} placeholder="Código de barras" /></label>
                                        </div>
                                        <div className="mt-2 flex justify-end gap-2">
                                            <button type="button" onClick={() => setAgregandoVariante(false)} disabled={isSaving} className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-600 disabled:opacity-50 dark:border-slate-600 dark:text-slate-300">Cancelar</button>
                                            <button type="button" onClick={handleAgregarVariante} disabled={isSaving} className="rounded-lg bg-primary px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-50">{isSaving ? "Creando…" : "Crear variante"}</button>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                        <div className="mb-4 grid grid-cols-1">
                            <label className="block">
                                <span className="flex items-center justify-between gap-2 text-sm font-semibold text-slate-700 dark:text-slate-200">
                                    <span>Título ML{subirMl && <span className="ml-0.5 font-bold text-red-600">*</span>}</span>
                                    {maxTitleLengthMl != null && (
                                        <span className={`rounded-full px-2 py-0.5 text-[11px] font-semibold tabular-nums ${
                                            tituloMl.length >= maxTitleLengthMl
                                                ? "bg-red-100 text-red-600 dark:bg-red-950/40 dark:text-red-400"
                                                : tituloMl.length >= maxTitleLengthMl * 0.9
                                                    ? "bg-amber-100 text-amber-700 dark:bg-amber-950/40 dark:text-amber-400"
                                                    : "bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400"}`}>
                                            {tituloMl.length}/{maxTitleLengthMl}
                                        </span>
                                    )}
                                </span>
                                <input type="text" className={`${inputBaseClassName} ${formErrors.tituloMl ? inputErrorClassName : ""}`} value={tituloMl} onChange={e => { setTituloMl(e.target.value); if (formErrors.tituloMl) setFormErrors(p => ({ ...p, tituloMl: "" })); }} onKeyDown={e => { if (e.key === "Enter") { e.preventDefault(); handlePredecirCategoriasMl(); } }} placeholder="Título para Mercado Libre" maxLength={maxTitleLengthMl ?? undefined} />
                                {formErrors.tituloMl && <p className="mt-1 text-xs text-red-500">{formErrors.tituloMl}</p>}
                                {mlPublicado ? (
                                    <div className="mt-2 flex flex-col gap-1">
                                        <span className="inline-block rounded-lg border border-indigo-300 bg-white px-2 py-1 text-xs font-medium text-indigo-800 dark:border-indigo-600 dark:bg-slate-800 dark:text-indigo-200">
                                            {mlCategoryNombre ? pathConHojaResaltada(mlCategoryNombre) : (mlCategoryId || "—")}
                                        </span>
                                        <span className="text-xs text-amber-600 dark:text-amber-400">Mercado Libre no permite cambiar la categoría de una publicación existente. Para cambiarla hay que republicar (se elimina la publicación con sus visitas y ventas).</span>
                                    </div>
                                ) : (
                                    <div className="mt-2 flex flex-col gap-2">
                                        {cargandoMl ? indicadorCarga("Cargando datos de ML…") : (<>
                                            <div className="flex items-center gap-2">
                                                <Button variant="dark" onClick={handlePredecirCategoriasMl} disabled={!tituloMl.trim() || cargandoPrediccionesMl || cargandoMl}>
                                                    <TagIcon className="w-4 h-4" /> {cargandoPrediccionesMl ? "Prediciendo..." : "Predecir categorías"}
                                                </Button>
                                                {mlCategoryId && (
                                                    <span title={mlCategoryNombre || String(mlCategoryId)} className="inline-block max-w-full rounded-lg border border-indigo-300 bg-white px-2 py-1 text-xs font-medium leading-relaxed text-indigo-800 dark:border-indigo-600 dark:bg-slate-800 dark:text-indigo-200">
                                                        {pathConHojaResaltada(mlCategoryNombre || String(mlCategoryId))}
                                                        <button type="button" onClick={() => { setMlCategoryId(null); setMlCategoryNombre(null); setPrediccionesMl([]); }} className="ml-1 align-middle font-bold leading-none text-indigo-500 hover:text-red-500 dark:text-indigo-300" aria-label="Quitar categoría">×</button>
                                                    </span>
                                                )}
                                            </div>
                                            {prediccionesMl.length > 0 && (
                                                <div className="flex flex-wrap gap-1.5">
                                                    {prediccionesMl.map(p => (
                                                        <button
                                                            key={p.categoryId}
                                                            type="button"
                                                            onClick={() => { setMlCategoryId(p.categoryId); setMlCategoryNombre(p.categoryPath || p.categoryName); setPrediccionesMl([]); if (formErrors.mlCategory) setFormErrors(prev => ({ ...prev, mlCategory: "" })); }}
                                                            className={`rounded-lg border px-2 py-1 text-left text-xs transition-colors ${mlCategoryId === p.categoryId ? "border-yellow-400 bg-yellow-100 text-yellow-900" : "border-blue-300 bg-blue-50 text-blue-800 hover:bg-blue-100 dark:border-blue-700 dark:bg-blue-900/20 dark:text-blue-200 dark:hover:bg-blue-800/40"}`}
                                                        >
                                                            {pathConHojaResaltada(p.categoryPath || p.categoryName)} <span className="text-blue-400 dark:text-blue-300/70">({p.categoryId})</span>
                                                        </button>
                                                    ))}
                                                </div>
                                            )}
                                            {formErrors.mlCategory
                                                ? <p className="text-xs text-red-500">{formErrors.mlCategory}</p>
                                                : <p className="text-xs text-slate-500 dark:text-slate-400">Si cargás Título ML, es obligatorio elegir una categoría: predecí (o Enter) y seleccioná una de las opciones.</p>}
                                        </>)}
                                    </div>
                                )}
                            </label>
                        </div>
                        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                            <span className="text-xs">
                                {mlaCodigo.trim()
                                    ? (mlaId
                                        ? <span className="font-medium text-emerald-600 dark:text-emerald-400">✓ MLA existente en la base — se cargaron sus datos</span>
                                        : <span className="font-medium text-blue-600 dark:text-blue-400">Nuevo — se creará al guardar</span>)
                                    : <span className="text-slate-500 dark:text-slate-400">Sin MLA — si publicás en Mercado Libre se crea y asocia al subir la publicación</span>}
                            </span>
                            <button
                                type="button"
                                onClick={handleObtenerMlaDeML}
                                disabled={obteniendoMla || !mlaCodigo.trim()}
                                title={!mlaCodigo.trim() ? "Cargá primero el código MLA" : "Trae el MLA y sus datos (MLAU, envío, comisión) desde tu publicación de MercadoLibre"}
                                className="inline-flex items-center gap-2 rounded-xl bg-gradient-to-r from-amber-400 to-yellow-400 px-4 py-2 text-sm font-semibold text-slate-900 shadow-sm transition hover:from-amber-300 hover:to-yellow-300 disabled:cursor-not-allowed disabled:opacity-50"
                            >
                                <CloudArrowDownIcon className={`h-4 w-4 ${obteniendoMla ? "animate-pulse" : ""}`} />
                                {obteniendoMla ? "Trayendo de MercadoLibre..." : "Autocompletar desde MercadoLibre"}
                            </button>
                        </div>
                        {mlaVerif && (
                            <p className={`mb-3 -mt-1 text-xs font-medium ${
                                mlaVerif.tone === "emerald" ? "text-emerald-600 dark:text-emerald-400"
                                : mlaVerif.tone === "amber" ? "text-amber-600 dark:text-amber-400"
                                : "text-slate-500 dark:text-slate-400"}`}>
                                {mlaVerif.text}
                            </p>
                        )}
                        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-6">
                            <label className="block xl:col-span-3">
                                <span className={fieldLabelClassName}>Código MLA</span>
                                <input
                                    type="text"
                                    className={`${inputBaseClassName} ${mlaVerif?.alerta ? inputErrorClassName : ""}`}
                                    value={mlaCodigo}
                                    onChange={e => {
                                        const v = e.target.value;
                                        setMlaCodigo(v);
                                        // Al vaciar el código se desasocia y se limpian los datos heredados.
                                        if (!v.trim()) { setMlaId(null); setMlaDetalle(null); setMlaMlau(""); setMlaPrecioEnvio(""); setMlaComision(""); setMlaTope(""); }
                                    }}
                                    placeholder="MLA123456789"
                                />
                            </label>
                            <label className="block xl:col-span-3">
                                <span className={fieldLabelClassName}>MLAU</span>
                                <input type="text" className={inputBaseClassName} value={mlaMlau} onChange={e => setMlaMlau(e.target.value)} placeholder="Opcional" />
                            </label>
                            <label className="block xl:col-span-2">
                                <span className={fieldLabelClassName}>Precio envío</span>
                                <input type="number" min={0} className={inputBaseClassName} value={mlaPrecioEnvio} onChange={e => setMlaPrecioEnvio(e.target.value === "" ? "" : Number(e.target.value))} placeholder="0" />
                                {mlaDetalle?.fechaCalculoEnvio && (
                                    <span className="mt-0.5 block text-[11px] text-slate-400 dark:text-slate-500">calculado el {new Date(mlaDetalle.fechaCalculoEnvio).toLocaleString("es-AR", { dateStyle: "short", timeStyle: "short" })}</span>
                                )}
                            </label>
                            <label className="block xl:col-span-2">
                                <span className={fieldLabelClassName}>Comisión (%)</span>
                                <input type="number" min={0} step={0.5} className={inputBaseClassName} value={mlaComision} onChange={e => setMlaComision(e.target.value === "" ? "" : Number(e.target.value))} placeholder="0" />
                                {mlaDetalle?.fechaCalculoComision && (
                                    <span className="mt-0.5 block text-[11px] text-slate-400 dark:text-slate-500">calculado el {new Date(mlaDetalle.fechaCalculoComision).toLocaleString("es-AR", { dateStyle: "short", timeStyle: "short" })}</span>
                                )}
                            </label>
                            <label className="block xl:col-span-2">
                                <span className={fieldLabelClassName}>Tope promoción</span>
                                <input type="number" min={0} className={inputBaseClassName} value={mlaTope} onChange={e => setMlaTope(e.target.value === "" ? "" : Number(e.target.value))} placeholder="0" />
                            </label>
                        </div>

                        {/* Descripción ML (no se guarda; se lee del canal al abrir y se envía al publicar) */}
                        <div className="mt-6 border-t border-slate-200/70 pt-4 dark:border-slate-700/70">
                            <label className="block">
                                <span className="flex items-center justify-between gap-2">
                                    <span className={fieldLabelClassName}>Descripción Mercado Libre</span>
                                    <button type="button" disabled={sugiriendoDesc || !editandoProductoId}
                                        title="Guardá el producto primero para componer la descripción"
                                        className="text-xs font-medium text-blue-600 hover:underline disabled:opacity-50 dark:text-blue-400"
                                        onClick={async () => {
                                            if (!editandoProductoId) return;
                                            setSugiriendoDesc(true);
                                            try { setDescripcionMl(await getDescripcionSugeridaAPI(editandoProductoId, "ml")); }
                                            catch (e) { if (!esSesionExpirada(e)) notificar.error(e instanceof Error ? e.message : "No se pudo componer la descripción"); }
                                            finally { setSugiriendoDesc(false); }
                                        }}>
                                        Componer descripción sugerida
                                    </button>
                                </span>
                                {cargandoMl
                                    ? indicadorCarga("Cargando datos del canal…")
                                    : <textarea className={`${inputBaseClassName} resize-y min-h-[12rem]`} value={descripcionMl} onChange={e => setDescripcionMl(e.target.value)} rows={9} maxLength={20000} disabled={cargandoMl} placeholder="Texto plano (sin HTML). Lo que ves es lo que se publica en ML." />}
                            </label>
                        </div>

                        {/* Ficha técnica de la categoría ML (Variante / Principales / Secundarias) */}
                        {cargandoMl ? indicadorCarga("Cargando datos de ML…") : (mlCategoryId && mlFicha && mlFicha.secciones.length > 0 && (
                            <div className="mt-6 border-t border-slate-200/70 pt-4 dark:border-slate-700/70">
                                <div className="mb-1 flex items-center gap-1.5">
                                    <span className="text-sm font-semibold text-slate-700 dark:text-slate-200">Características de Mercado Libre</span>
                                </div>
                                <p className={`${sectionDescriptionClassName} mb-4`}>Ficha técnica de la categoría: completá lo que aplique para una mejor publicación.</p>
                                {formErrors.mlAtributos && (
                                    <p className="mb-3 text-sm font-medium text-red-600 dark:text-red-400">{formErrors.mlAtributos}</p>
                                )}
                                {mlFicha.secciones.map(seccion => (
                                    <div key={seccion.id} className="mb-5">
                                        <h4 className="mb-3 border-b border-slate-200/70 pb-1 text-xs font-bold uppercase tracking-wider text-slate-500 dark:border-slate-700/60 dark:text-slate-400">
                                            {SECCION_LABEL_ML[seccion.id] ?? seccion.label}
                                        </h4>
                                        <div className="grid grid-cols-1 items-start gap-4 md:grid-cols-2 xl:grid-cols-3">
                                            {seccion.componentes.flatMap(c => renderMlComponenteCeldas(c))}
                                        </div>
                                    </div>
                                ))}
                            </div>
                        ))}

                        {/* Subsección: dimensiones del paquete de envío de ML */}
                        <div className="mt-6 border-t border-slate-200/70 pt-4 dark:border-slate-700/70">
                            <div className="mb-3 flex items-center gap-1.5">
                                <span className="text-sm font-semibold text-slate-700 dark:text-slate-200">Paquete para envío</span>
                                <Tooltip content="ML exige las dimensiones del paquete para publicar. Cargá alto/ancho/largo en cm (enteros; ML no usa decimales) y el peso en kg (hasta el gramo). Se envían a ML en cm y gramos, redondeados a enteros." className="flex items-center">
                                    <InformationCircleIcon className="h-4 w-4 shrink-0 text-slate-400 transition-colors hover:text-slate-600 dark:hover:text-slate-200" />
                                </Tooltip>
                            </div>
                            {cargandoMl ? indicadorCarga("Cargando datos de ML…") : (
                                <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
                                    <label className="block">
                                        <span className={fieldLabelClassName}>Alto (cm){subirMl && <span className="ml-0.5 font-bold text-red-600">*</span>}</span>
                                        <input type="number" min={1} step={1} className={`${inputBaseClassName} ${formErrors.mlPaqAlto ? inputErrorClassName : ""}`}
                                            value={mlPaqAlto} onChange={e => { setMlPaqAlto(e.target.value === "" ? "" : Number(e.target.value)); if (formErrors.mlPaqAlto) setFormErrors(p => ({ ...p, mlPaqAlto: "" })); }} />
                                        {formErrors.mlPaqAlto && <p className="mt-1 text-xs text-red-500">{formErrors.mlPaqAlto}</p>}
                                    </label>
                                    <label className="block">
                                        <span className={fieldLabelClassName}>Ancho (cm){subirMl && <span className="ml-0.5 font-bold text-red-600">*</span>}</span>
                                        <input type="number" min={1} step={1} className={`${inputBaseClassName} ${formErrors.mlPaqAncho ? inputErrorClassName : ""}`}
                                            value={mlPaqAncho} onChange={e => { setMlPaqAncho(e.target.value === "" ? "" : Number(e.target.value)); if (formErrors.mlPaqAncho) setFormErrors(p => ({ ...p, mlPaqAncho: "" })); }} />
                                        {formErrors.mlPaqAncho && <p className="mt-1 text-xs text-red-500">{formErrors.mlPaqAncho}</p>}
                                    </label>
                                    <label className="block">
                                        <span className={fieldLabelClassName}>Largo (cm){subirMl && <span className="ml-0.5 font-bold text-red-600">*</span>}</span>
                                        <input type="number" min={1} step={1} className={`${inputBaseClassName} ${formErrors.mlPaqLargo ? inputErrorClassName : ""}`}
                                            value={mlPaqLargo} onChange={e => { setMlPaqLargo(e.target.value === "" ? "" : Number(e.target.value)); if (formErrors.mlPaqLargo) setFormErrors(p => ({ ...p, mlPaqLargo: "" })); }} />
                                        {formErrors.mlPaqLargo && <p className="mt-1 text-xs text-red-500">{formErrors.mlPaqLargo}</p>}
                                    </label>
                                    <label className="block">
                                        <span className={fieldLabelClassName}>Peso (kg){subirMl && <span className="ml-0.5 font-bold text-red-600">*</span>}</span>
                                        <input type="number" min={0.1} step={0.1} className={`${inputBaseClassName} ${formErrors.mlPaqPeso ? inputErrorClassName : ""}`}
                                            value={mlPaqPeso} onChange={e => { setMlPaqPeso(e.target.value === "" ? "" : Number(e.target.value)); if (formErrors.mlPaqPeso) setFormErrors(p => ({ ...p, mlPaqPeso: "" })); }} />
                                        {formErrors.mlPaqPeso && <p className="mt-1 text-xs text-red-500">{formErrors.mlPaqPeso}</p>}
                                    </label>
                                </div>
                            )}
                        </div>

                    </fieldset>
                    )}

                    {/* TIENDA NUBE · KT HOGAR */}
                    {subirKtHogar && (
                    <fieldset className={`${sectionClassName} ${SECTION_TINT.seo}`}>
                        <legend className={sectionTitleClassName}><BuildingStorefrontIcon className="h-5 w-5" /> Tienda Nube · KT HOGAR</legend>
                        {estadoHogar?.productId != null && NUBE_ADMIN_SUBDOMINIO.HOGAR && (
                            <a href={nubeEditarURL(NUBE_ADMIN_SUBDOMINIO.HOGAR, estadoHogar.productId)} target="_blank" rel="noreferrer" className="mb-3 inline-flex items-center gap-1 text-xs font-medium text-blue-600 hover:underline dark:text-blue-400">Editar en Tienda Nube ↗</a>
                        )}
                        <div className="grid grid-cols-1 gap-4">
                            <label className="block">
                                <span className={fieldLabelClassName}>Título · KT HOGAR</span>
                                {cargandoHogar ? indicadorCarga("Cargando datos del canal…") : (
                                    <input type="text" className={inputBaseClassName} value={tituloHogar} onChange={e => setTituloHogar(e.target.value)} placeholder="Título en KT HOGAR (vacío = usa el título base)" />
                                )}
                                <span className="mt-0.5 block text-[11px] text-slate-400 dark:text-slate-500">Se lee de KT HOGAR al editar. Si queda vacío, se publica el título base.</span>
                            </label>
                            {renderSeoNube("HOGAR", "KT Hogar", seoHogar, setSeoHogar)}
                            {/* div (no <label>): un <label> redirige el click al primer control interno (el botón
                                "Componer"), lo que disparaba una recomposición al clickear el editor contentEditable. */}
                            <div className="block">
                                <span className="flex items-center justify-between gap-2">
                                    <span className={fieldLabelClassName}>Descripción · KT HOGAR</span>
                                    <button type="button" disabled={sugiriendoDesc || !editandoProductoId}
                                        title="Guardá el producto primero para componer la descripción"
                                        className="text-xs font-medium text-blue-600 hover:underline disabled:opacity-50 dark:text-blue-400"
                                        onClick={async () => { if (!editandoProductoId) return; setSugiriendoDesc(true); try { setDescripcionHogar(await getDescripcionSugeridaAPI(editandoProductoId, "nube")); } catch (e) { if (!esSesionExpirada(e)) notificar.error(e instanceof Error ? e.message : "No se pudo componer la descripción"); } finally { setSugiriendoDesc(false); } }}>
                                        Componer descripción sugerida
                                    </button>
                                </span>
                                {cargandoHogar
                                    ? indicadorCarga("Cargando datos del canal…")
                                    : <HtmlEditor value={descripcionHogar} onChange={setDescripcionHogar} placeholder="HTML. Lo que ves es lo que se publica en KT HOGAR." />}
                            </div>
                            {/* Paquete de envío · KT HOGAR */}
                            <div className="border-t border-slate-200/70 pt-4 dark:border-slate-700/70">
                                <div className="mb-3 flex items-center gap-1.5">
                                    <CubeIcon className="h-4 w-4 text-slate-500" />
                                    <span className="text-sm font-semibold text-slate-700 dark:text-slate-200">Paquete de envío</span>
                                </div>
                                {cargandoHogar ? indicadorCarga("Cargando datos del canal…") : (
                                <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
                                    <label className="block">
                                        <span className={fieldLabelClassName}>Peso (kg)</span>
                                        <input type="number" min={0} step="0.001" className={inputBaseClassName} value={hogarPeso} onChange={e => setHogarPeso(e.target.value)} />
                                    </label>
                                    <label className="block">
                                        <span className={fieldLabelClassName}>Profundidad (cm)</span>
                                        <input type="number" min={0} step="1" className={inputBaseClassName} value={hogarProfundidad} onChange={e => setHogarProfundidad(e.target.value)} />
                                    </label>
                                    <label className="block">
                                        <span className={fieldLabelClassName}>Ancho (cm)</span>
                                        <input type="number" min={0} step="1" className={inputBaseClassName} value={hogarAncho} onChange={e => setHogarAncho(e.target.value)} />
                                    </label>
                                    <label className="block">
                                        <span className={fieldLabelClassName}>Alto (cm)</span>
                                        <input type="number" min={0} step="1" className={inputBaseClassName} value={hogarAlto} onChange={e => setHogarAlto(e.target.value)} />
                                    </label>
                                </div>
                                )}
                            </div>
                        </div>
                    </fieldset>
                    )}

                    {/* TIENDA NUBE · KT GASTRO */}
                    {subirKtGastro && (
                    <fieldset className={`${sectionClassName} ${SECTION_TINT.seo}`}>
                        <legend className={sectionTitleClassName}><BuildingStorefrontIcon className="h-5 w-5" /> Tienda Nube · KT GASTRO</legend>
                        {estadoGastro?.productId != null && NUBE_ADMIN_SUBDOMINIO.GASTRO && (
                            <a href={nubeEditarURL(NUBE_ADMIN_SUBDOMINIO.GASTRO, estadoGastro.productId)} target="_blank" rel="noreferrer" className="mb-3 inline-flex items-center gap-1 text-xs font-medium text-blue-600 hover:underline dark:text-blue-400">Editar en Tienda Nube ↗</a>
                        )}
                        {esEquipamiento && (
                            <div className="mb-4 rounded-xl border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-800 dark:border-amber-700 dark:bg-amber-900/20 dark:text-amber-300">
                                Producto de <b>EQUIPAMIENTO</b>: al subir a KT GASTRO se le agregará <b>*</b> al final del título y un bullet <b>&quot;ENVIO A COTIZAR&quot;</b> a la descripción.
                            </div>
                        )}
                        <div className="grid grid-cols-1 gap-4">
                            <label className="block">
                                <span className={fieldLabelClassName}>Título · KT GASTRO</span>
                                {cargandoGastro ? indicadorCarga("Cargando datos del canal…") : (
                                    <input type="text" className={inputBaseClassName} value={tituloGastro} onChange={e => setTituloGastro(e.target.value)} placeholder="Título en KT GASTRO (vacío = usa el título base)" />
                                )}
                                <span className="mt-0.5 block text-[11px] text-slate-400 dark:text-slate-500">Se lee de KT GASTRO al editar. Si queda vacío, se publica el título base.</span>
                                {esEquipamiento && (tituloGastro.trim() || tituloNube.trim()) && (() => { const t = tituloGastro.trim() || tituloNube.trim(); return (
                                    <span className="mt-1 block text-[11px] font-medium text-amber-700 dark:text-amber-300">
                                        En KT GASTRO se publicará como: «{t.endsWith("*") ? t : t + "*"}»
                                    </span>
                                ); })()}
                            </label>
                            {renderSeoNube("GASTRO", "KT Gastro", seoGastro, setSeoGastro)}
                            {/* div (no <label>): evita que el click en el editor dispare el botón "Componer" (ver KT HOGAR). */}
                            <div className="block">
                                <span className="flex items-center justify-between gap-2">
                                    <span className={fieldLabelClassName}>Descripción · KT GASTRO</span>
                                    <button type="button" disabled={sugiriendoDesc || !editandoProductoId}
                                        title="Guardá el producto primero para componer la descripción"
                                        className="text-xs font-medium text-blue-600 hover:underline disabled:opacity-50 dark:text-blue-400"
                                        onClick={async () => { if (!editandoProductoId) return; setSugiriendoDesc(true); try { setDescripcionGastro(await getDescripcionSugeridaAPI(editandoProductoId, "nube")); } catch (e) { if (!esSesionExpirada(e)) notificar.error(e instanceof Error ? e.message : "No se pudo componer la descripción"); } finally { setSugiriendoDesc(false); } }}>
                                        Componer descripción sugerida
                                    </button>
                                </span>
                                {cargandoGastro
                                    ? indicadorCarga("Cargando datos del canal…")
                                    : <HtmlEditor value={descripcionGastro} onChange={setDescripcionGastro} placeholder="HTML. Lo que ves es lo que se publica en KT GASTRO." />}
                            </div>
                            {/* Paquete de envío · KT GASTRO */}
                            <div className="border-t border-slate-200/70 pt-4 dark:border-slate-700/70">
                                <div className="mb-3 flex items-center gap-1.5">
                                    <CubeIcon className="h-4 w-4 text-slate-500" />
                                    <span className="text-sm font-semibold text-slate-700 dark:text-slate-200">Paquete de envío</span>
                                </div>
                                {cargandoGastro ? indicadorCarga("Cargando datos del canal…") : (
                                <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
                                    <label className="block">
                                        <span className={fieldLabelClassName}>Peso (kg)</span>
                                        <input type="number" min={0} step="0.001" className={inputBaseClassName} value={gastroPeso} onChange={e => setGastroPeso(e.target.value)} />
                                    </label>
                                    <label className="block">
                                        <span className={fieldLabelClassName}>Profundidad (cm)</span>
                                        <input type="number" min={0} step="1" className={inputBaseClassName} value={gastroProfundidad} onChange={e => setGastroProfundidad(e.target.value)} />
                                    </label>
                                    <label className="block">
                                        <span className={fieldLabelClassName}>Ancho (cm)</span>
                                        <input type="number" min={0} step="1" className={inputBaseClassName} value={gastroAncho} onChange={e => setGastroAncho(e.target.value)} />
                                    </label>
                                    <label className="block">
                                        <span className={fieldLabelClassName}>Alto (cm)</span>
                                        <input type="number" min={0} step="1" className={inputBaseClassName} value={gastroAlto} onChange={e => setGastroAlto(e.target.value)} />
                                    </label>
                                </div>
                                )}
                            </div>
                        </div>
                    </fieldset>
                    )}

                    <fieldset className={`${sectionClassName} ${SECTION_TINT.inflados}`}>
                        <legend className={sectionTitleClassName}><BanknotesIcon /> Precios Inflados por Canal</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Asigná, cambiá o quitá el precio inflado de este producto en cada canal.</p>
                        {editandoProductoId
                            ? <PreciosInfladosSection productoId={editandoProductoId} />
                            : <PreciosInfladosSection value={preciosInfladosSel} onChange={setPreciosInfladosSel} />}
                    </fieldset>

                    {/* Estado de las subidas a canales: solo aparece si alguna falló. El producto ya se guardó. */}
                    {resultadosCanal.some(r => r.estado === "error") && (
                        <div className="rounded-xl border border-amber-300 bg-amber-50 px-4 py-3 dark:border-amber-800 dark:bg-amber-950/20">
                            <div className="mb-2 font-semibold text-amber-800 dark:text-amber-300">
                                El producto se guardó. Faltó publicar en algún canal:
                            </div>
                            <ul className="space-y-1">
                                {resultadosCanal.map(r => (
                                    <li key={r.canal} className="flex items-start gap-2">
                                        <span className={r.estado === "ok" ? "font-bold text-emerald-600" : "font-bold text-red-600"}>
                                            {r.estado === "ok" ? "✓" : "✗"}
                                        </span>
                                        <span className="font-medium">{r.canal}:</span>
                                        <span className="text-slate-600 dark:text-slate-300">{r.detalle}</span>
                                    </li>
                                ))}
                            </ul>
                            <div className="mt-3">
                                <Button variant="dark" onClick={reintentarFallidos} disabled={reintentando}>
                                    {reintentando ? "Reintentando…" : "Reintentar los que fallaron"}
                                </Button>
                            </div>
                        </div>
                    )}
                    </div>
                </div>
            </Modal>

            {carouselSku && <ImagenesCarousel sku={carouselSku} onClose={() => setCarouselSku(null)} />}
        </>
    );
}
