"use client";
import { useState, useEffect, useCallback } from "react";
import { toast } from "sonner";
import { notificar } from "../utils/notificar";
import { BuildingStorefrontIcon, CheckIcon, CheckCircleIcon, CloudArrowDownIcon, CubeIcon, FireIcon, HomeIcon, XMarkIcon, IdentificationIcon, CurrencyDollarIcon, ArchiveBoxIcon, ReceiptPercentIcon, Squares2X2Icon, UserGroupIcon, ShoppingBagIcon, BanknotesIcon, InformationCircleIcon } from "@heroicons/react/24/outline";
import Tooltip from "../components/Tooltip/Tooltip";
import { API_BASE_URL } from "../config/runtime";
import Button from "../components/Button/Button";
import Modal from "../components/Modal/Modal";
import AsyncSelect from "../components/AsyncSelect/AsyncSelect";
import {
    getSiguienteSkuAPI, existeSkuAPI, getMlaPorSkuAPI, createMlaAPI, getMlaPorIdAPI, patchMlaAPI, type MlaDetalleDTO,
    searchMarcas, searchClasifGral, searchClasifGastro, searchTipos, searchProveedores, searchOrigenes, searchMateriales, searchMlas,
    searchCatalogos, searchAptos, searchClientes, searchCanales, addProductoCatalogoAPI, addProductoAptoAPI, addProductoClienteAPI,
    removeProductoCatalogoAPI, removeProductoAptoAPI, removeProductoClienteAPI, updateProductoAPI, getNombreById,
    exportarProductosADuxAPI, calcularEnvioMlaAPI, exportarProductosANubeAPI, exportarProductosAMlAPI, recalcularProductoAPI,
    generarSeoAPI, type DestinoNube, type SeoNube,
    searchSectoresDeposito, predecirCategoriasMlAPI, type PrediccionCategoriaMl,
    getImagenDetalleAPI, type ImagenDetalle,
} from "./productosService";
import { updateProductoMargenAPI } from "./productoMargenService";
import { getCuotasPorCanalAPI } from "../canal-concepto-cuotas/canalConceptoCuotaService";
import ImagenesCarousel from "./ImagenesCarousel";
import {
    getProductoAptosAPI, getProductoCatalogosAPI, getProductoClientesAPI,
    getAllAptosAPI, getAllCatalogosAPI, getAllClientesAPI, asignarPrecioInfladoAPI,
} from "./productoSubRecursosService";
import MultiAsyncSelect, { type MultiOption } from "../components/MultiAsyncSelect/MultiAsyncSelect";
import { PreciosInfladosSection, type PrecioInfladoDraft } from "./PreciosInfladosSection";
import { HistorialSection } from "./HistorialSection";
import { ProductoCreateDTO, ProductoDTO, ProductoPatchDTO } from "./types";
import { formatFechaAR } from "../utils/formatDate";


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

const EXT_ML = new Set(["jpg", "jpeg", "png"]);
const EXT_NUBE = new Set(["gif", "jpg", "jpeg", "png", "webp"]);
const MAX_BYTES_IMG = 10 * 1024 * 1024; // 10 MB

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
};

export default function ProductoFormModal({ producto, canExportarDux, createProducto, onClose, onSuccess }: ProductoFormModalProps) {
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
    const [esCombo, setEsCombo] = useState(false);
    const [subirADux, setSubirADux] = useState(true);
    const [subirKtHogar, setSubirKtHogar] = useState(true);
    const [subirKtGastro, setSubirKtGastro] = useState(true);
    const [subirMl, setSubirMl] = useState(true);
    const [imagenesDetectadas, setImagenesDetectadas] = useState<ImagenDetalle[]>([]);
    const [cuotaHogar, setCuotaHogar] = useState<number>(-1);
    const [cuotaGastro, setCuotaGastro] = useState<number>(6);
    const [cuotasHogarOpts, setCuotasHogarOpts] = useState<CuotaOpcion[]>([]);
    const [cuotasGastroOpts, setCuotasGastroOpts] = useState<CuotaOpcion[]>([]);
    // SEO de Tienda Nube por canal (NO se persiste: arranca vacío siempre, en alta y edición).
    // Si el usuario genera/edita estos campos, se usan al exportar; si quedan vacíos, se autogeneran.
    const [seoHogar, setSeoHogar] = useState<{ title: string; description: string; tags: string }>({ title: "", description: "", tags: "" });
    const [seoGastro, setSeoGastro] = useState<{ title: string; description: string; tags: string }>({ title: "", description: "", tags: "" });
    const [generandoSeo, setGenerandoSeo] = useState<null | "GASTRO" | "HOGAR">(null);
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
    // Relaciones N-a-N (se asocian tras crear el producto)
    const [catalogosSel, setCatalogosSel] = useState<MultiOption[]>([]);
    const [aptosSel, setAptosSel] = useState<MultiOption[]>([]);
    const [clientesSel, setClientesSel] = useState<MultiOption[]>([]);
    // Precios inflados a asignar tras crear el producto (solo modo alta).
    const [preciosInfladosSel, setPreciosInfladosSel] = useState<PrecioInfladoDraft[]>([]);
    // null = modo crear; con id = modo editar (mismo modal/form).
    const [editandoProductoId, setEditandoProductoId] = useState<number | null>(null);
    // Tab activo del panel en modo edición: form de datos o historial de cambios.
    const [panelTab, setPanelTab] = useState<"datos" | "historial">("datos");
    // Snapshot de N-a-N al abrir en edición, para calcular el diff al guardar.
    const [catalogosOriginal, setCatalogosOriginal] = useState<MultiOption[]>([]);
    const [aptosOriginal, setAptosOriginal] = useState<MultiOption[]>([]);
    const [clientesOriginal, setClientesOriginal] = useState<MultiOption[]>([]);
    const [moq, setMoq] = useState<number | "">("");
    const [stock, setStock] = useState<number | "">(0);
    const [tagReposicion, setTagReposicion] = useState<"" | "PRIO" | "LIQ">("");
    const [tag, setTag] = useState<"" | "MAQUINA" | "REPUESTO" | "MENAJE" | "INSUMO">("");
    const [formErrors, setFormErrors] = useState<Record<string, string>>({});

    // Carga las cuotas reales de cada canal de Tienda Nube (KT HOGAR / KT GASTRO) para
    // poblar los selectores. Si un canal no se encuentra o no tiene cuotas, queda con
    // las opciones por defecto del JSX (Transferencia / 6 cuotas) como red de seguridad.
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
            const [hogar, gastro] = await Promise.all([
                cargarCuotasCanal("KT HOGAR"),
                cargarCuotasCanal("KT GASTRO"),
            ]);
            if (cancelado) return;
            setCuotasHogarOpts(hogar);
            setCuotasGastroOpts(gastro);
            // Respeta el default (Transferencia / 6 cuotas) si existe en el canal; si no, la primera.
            if (hogar.length && !hogar.some(c => c.cuotas === -1)) setCuotaHogar(hogar[0].cuotas);
            if (gastro.length && !gastro.some(c => c.cuotas === 6)) setCuotaGastro(gastro[0].cuotas);
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
        if (tituloMl.trim() && !mlCategoryId) errors.mlCategory = "Si hay Título ML, predecí y elegí una categoría de Mercado Libre";
        if (tituloNube.trim().length > 100) errors.tituloNube = "Máximo 100 caracteres";
        else if ((subirKtHogar || subirKtGastro) && !tituloNube.trim()) errors.tituloNube = "Requerido para subir a Tienda Nube";
        if (costo === "" || Number(costo) <= 0) errors.costo = "El costo debe ser mayor a 0";
        if (uxb < 1) errors.uxb = "UxB debe ser al menos 1";
        if (!clasifGralId && !clasifGastroId) errors.clasificacion = "Seleccioná al menos una clasificación (general o gastronómica)";
        if (!tipoId) errors.tipoId = "El tipo es obligatorio";
        if (!sectorDepositoId) errors.sectorDeposito = "El sector de depósito es obligatorio (Dux lo requiere)";
        if (!esCombo) {
            if (!marcaId) errors.marcaId = "La marca es obligatoria";
            if (!origenId) errors.origenId = "El origen es obligatorio";
            if (!proveedorId) errors.proveedorId = "El proveedor es obligatorio";
            if (!materialId) errors.materialId = "El material es obligatorio";
            if (!tag) errors.tag = "El tag es obligatorio";
            const tieneMargen = (margenMinorista !== "" && Number(margenMinorista) > 0) || (margenMayorista !== "" && Number(margenMayorista) > 0);
            if (!tieneMargen) errors.margen = "Cargá al menos un margen (minorista o mayorista) mayor a 0";
        }
        if (largo.length > 45) errors.largo = "Máximo 45 caracteres";
        if (ancho.length > 45) errors.ancho = "Máximo 45 caracteres";
        if (alto.length > 45) errors.alto = "Máximo 45 caracteres";
        if (subirMl) {
            if (mlPaqAlto === "" || Number(mlPaqAlto) <= 0) errors.mlPaqAlto = "Requerido para subir a ML";
            if (mlPaqAncho === "" || Number(mlPaqAncho) <= 0) errors.mlPaqAncho = "Requerido para subir a ML";
            if (mlPaqLargo === "" || Number(mlPaqLargo) <= 0) errors.mlPaqLargo = "Requerido para subir a ML";
            if (mlPaqPeso === "" || Number(mlPaqPeso) <= 0) errors.mlPaqPeso = "Requerido para subir a ML";
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
                ...clientesSel.map((c) => addProductoClienteAPI(productoId, Number(c.id))),
            ]);
        } catch {
            notificar.error("El producto se creó, pero falló al asociar algún catálogo/apto/cliente");
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
    const ejecutarExportsCanales = async (skuExport: string, canales: CanalExport[]): Promise<ResultadoCanal[]> => {
        const tareas: Promise<ResultadoCanal>[] = [];
        if (canales.includes("Dux")) {
            tareas.push((async (): Promise<ResultadoCanal> => {
                // Dux confirma el alta de forma asíncrona (puede tardar). Mostramos un toast de
                // carga mientras se espera la respuesta del proceso y lo cerramos al terminar.
                const toastId = toast.loading("Subiendo a Dux… esperando confirmación del proceso (puede tardar)");
                try {
                    const r = await exportarProductosADuxAPI([skuExport]);
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
                const tiendas: DestinoNube[] = [];
                if (subirKtHogar) tiendas.push({ tienda: "KT HOGAR", cuotas: cuotaHogar, seo: await resolverSeo(seoHogar, "HOGAR") });
                if (subirKtGastro) tiendas.push({ tienda: "KT GASTRO", cuotas: cuotaGastro, seo: await resolverSeo(seoGastro, "GASTRO") });
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
                    const r = await exportarProductosAMlAPI([skuExport]);
                    return clasificarExport("Mercado Libre", r, skuExport);
                } catch (e) {
                    return { canal: "Mercado Libre", estado: "error", detalle: e instanceof Error ? e.message : "error al subir" };
                }
            })());
        }
        return Promise.all(tareas);
    };

    const handleCreate = async () => {
        if (!validateForm()) return;
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
                notificar.error(e instanceof Error ? e.message : "Error al guardar el MLA");
                setIsSaving(false);
                return;
            }
            const payload: ProductoCreateDTO = {
                sku: sku.trim(), codExt, tituloDux: tituloDux.trim(), tituloMl: tituloMl.trim() || null, tituloNube: tituloNube.trim() || null, esCombo, uxb, activo,
                capacidad, largo: largo || null, ancho: ancho || null, alto: alto || null,
                diamboca: diamboca || null, diambase: diambase || null, espesor: espesor || null,
                costo: costoNum, iva,
                stock: stock !== "" ? stock : null,
                moq: moq !== "" ? moq : null,
                tagReposicion: tagReposicion || null,
                tag: tag || null,
                marcaId, origenId, clasifGralId: clasifGralId!, clasifGastroId, tipoId: tipoId!, proveedorId, materialId, sectorDepositoId, mlaId: mlaIdFinal,
                mlCategoryId: mlCategoryId, mlCategoryNombre: mlCategoryNombre,
                mlPaqAlto: mlPaqAlto === "" ? null : Number(mlPaqAlto),
                mlPaqAncho: mlPaqAncho === "" ? null : Number(mlPaqAncho),
                mlPaqLargo: mlPaqLargo === "" ? null : Number(mlPaqLargo),
                mlPaqPeso: mlPaqPeso === "" ? null : Number(mlPaqPeso),
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
        if (producto) {
            setEditandoProductoId(producto.id);
            setPanelTab("datos");
            setSku(producto.sku ?? "");
            setCodExt(producto.codExt ?? "");
            setTituloDux(producto.tituloDux ?? "");
            setTituloMl(producto.tituloMl ?? "");
            setMlCategoryId(producto.mlCategoryId ?? null);
            setMlCategoryNombre(producto.mlCategoryNombre ?? null);
            setPrediccionesMl([]);
            setTituloNube(producto.tituloNube ?? "");
            setEsCombo(!!producto.esCombo);
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
            setFormErrors({});
            setCatalogosSel([]); setAptosSel([]); setClientesSel([]);
            setCatalogosOriginal([]); setAptosOriginal([]); setClientesOriginal([]);
            // El SEO de Nube no se persiste: arranca vacío y se autogenera/edita por sesión.
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
                    const [aptosAsig, allAptos, catAsig, allCat, cliAsig, allCli] = await Promise.all([
                        getProductoAptosAPI(producto.id), getAllAptosAPI(),
                        getProductoCatalogosAPI(producto.id), getAllCatalogosAPI(),
                        getProductoClientesAPI(producto.id), getAllClientesAPI(),
                    ]);
                    const aptos: MultiOption[] = aptosAsig.map(a => ({ id: a.aptoId, label: allAptos.find(x => x.id === a.aptoId)?.nombre ?? String(a.aptoId) }));
                    const catalogos: MultiOption[] = catAsig.map(c => ({ id: c.catalogoId, label: allCat.find(x => x.id === c.catalogoId)?.nombre ?? String(c.catalogoId) }));
                    const clientes: MultiOption[] = cliAsig.map(c => ({ id: c.clienteId, label: allCli.find(x => x.id === c.clienteId)?.nombre ?? String(c.clienteId) }));
                    setAptosSel(aptos); setAptosOriginal(aptos);
                    setCatalogosSel(catalogos); setCatalogosOriginal(catalogos);
                    setClientesSel(clientes); setClientesOriginal(clientes);
                } catch {
                    notificar.error("No se pudieron cargar catálogos/aptos/clientes del producto");
                }
            })();
        } else {
            setEditandoProductoId(null);
            setPanelTab("datos");
            setSeoHogar({ title: "", description: "", tags: "" });
            setSeoGastro({ title: "", description: "", tags: "" });
            void cargarSkuSugerido(false);
        }
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
                notificar.error(e instanceof Error ? e.message : "Error al guardar el MLA");
                setIsSaving(false);
                return;
            }
            const patch = {
                codExt, tituloDux: tituloDux.trim(), tituloMl: tituloMl.trim() || null, tituloNube: tituloNube.trim() || null, esCombo, uxb, activo,
                capacidad, largo: largo || null, ancho: ancho || null, alto: alto || null,
                diamboca: diamboca || null, diambase: diambase || null, espesor: espesor || null,
                costo: costoNum, iva, stock: stock !== "" ? stock : null, moq: moq !== "" ? moq : null,
                tagReposicion: tagReposicion || null, tag: tag || null,
                marcaId, origenId, clasifGralId, clasifGastroId, tipoId, proveedorId, materialId, sectorDepositoId, mlaId: mlaIdFinal,
                mlCategoryId: mlCategoryId, mlCategoryNombre: mlCategoryNombre,
                mlPaqAlto: mlPaqAlto === "" ? null : Number(mlPaqAlto),
                mlPaqAncho: mlPaqAncho === "" ? null : Number(mlPaqAncho),
                mlPaqLargo: mlPaqLargo === "" ? null : Number(mlPaqLargo),
                mlPaqPeso: mlPaqPeso === "" ? null : Number(mlPaqPeso),
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
            const dCli = diff(clientesOriginal, clientesSel);
            await Promise.all([
                ...dCat.add.map(x => addProductoCatalogoAPI(id, x)),
                ...dCat.remove.map(x => removeProductoCatalogoAPI(id, x)),
                ...dApt.add.map(x => addProductoAptoAPI(id, x)),
                ...dApt.remove.map(x => removeProductoAptoAPI(id, x)),
                ...dCli.add.map(x => addProductoClienteAPI(id, x)),
                ...dCli.remove.map(x => removeProductoClienteAPI(id, x)),
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
            notificar.error(e instanceof Error ? e.message : "Error al guardar los cambios");
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

    // Busca la publicación en ML por el SKU del form: crea/asegura el MLA, calcula
    // envío + comisión y lo deja seleccionado.
    const handleObtenerMlaDeML = async () => {
        if (!sku.trim()) {
            toast.error("Cargá primero el SKU para buscar en MercadoLibre");
            return;
        }
        setObteniendoMla(true);
        try {
            const mla = await getMlaPorSkuAPI(sku.trim());
            aplicarMlaEnForm(mla);
            notificar.success(`MLA ${mla.mla} obtenido de MercadoLibre`);
        } catch (e) {
            notificar.error(e instanceof Error ? e.message : "Error al obtener el MLA");
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
            notificar.error(e instanceof Error ? e.message : "No se pudieron predecir categorías");
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
        setGenerandoSeo(canal);
        try {
            const r = await generarSeoAPI(canal, construirContextoSeo());
            const next = { title: r.seoTitle, description: r.seoDescription, tags: r.seoTags };
            if (canal === "HOGAR") setSeoHogar(next); else setSeoGastro(next);
            notificar.success(`SEO de ${canal === "HOGAR" ? "KT Hogar" : "KT Gastro"} generado`);
        } catch (e) {
            notificar.error(e instanceof Error ? e.message : "No se pudo generar el SEO con IA");
        } finally {
            setGenerandoSeo(null);
        }
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
    const inputBaseClassName = "mt-1 w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-900 shadow-sm outline-none transition placeholder:text-slate-400 focus:border-blue-400 focus:ring-4 focus:ring-blue-100 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100 dark:focus:ring-blue-500/20";
    const inputErrorClassName = "border-red-300 bg-red-50 focus:border-red-400 focus:ring-red-100 dark:border-red-700 dark:bg-red-950/20 dark:focus:ring-red-500/20";
    const checkboxCardClassName = "flex items-center gap-3 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm font-semibold text-slate-700 dark:border-slate-700 dark:bg-slate-800/80 dark:text-slate-200";
    // Card de canal (sección Canales de venta): layout en columna y altura uniforme para que el
    // selector de cuotas no comprima el título ni desalinee las tarjetas entre sí.
    const canalCardClassName = "flex flex-col gap-2 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm font-semibold text-slate-700 dark:border-slate-700 dark:bg-slate-800/80 dark:text-slate-200";
    const selectBaseClassName = `${inputBaseClassName} appearance-none`;

    return (
        <>
            {/* MODAL CREAR / EDITAR PRODUCTO */}
            <Modal isOpen={true} onClose={onClose} title={editandoProductoId ? `Editar Producto${sku ? ` · ${sku}` : ""}` : "Nuevo Producto"} size="3xl" closeOnEscape={false} busy={isSaving}
                footer={<><Button variant="light" onClick={onClose}><XMarkIcon className="w-4 h-4" /> Cancelar</Button><Button variant="dark" onClick={editandoProductoId ? handleGuardarEdicion : handleCreate} disabled={isSaving || (!editandoProductoId && skuYaExiste)}>{isSaving ? <SpinnerIcon /> : <CheckIcon className="w-4 h-4" />} {isSaving ? (editandoProductoId ? "Guardando..." : "Creando Producto...") : (editandoProductoId ? "Guardar Cambios" : "Crear Producto")}</Button></>}>
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
                    {Object.values(formErrors).some(Boolean) && (
                        <div className="rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-800 dark:bg-red-950/20 dark:text-red-300">
                            Revisá los campos marcados antes de guardar.
                        </div>
                    )}

                    <fieldset className={`${sectionClassName} ${SECTION_TINT.canales}`}>
                        <legend className={sectionTitleClassName}><BuildingStorefrontIcon className="h-5 w-5" /> Canales de venta</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Dónde publicar/subir el producto.</p>
                        <div className="grid grid-cols-1 items-start gap-3 md:grid-cols-2 xl:grid-cols-4">
                            {canExportarDux && (
                                <div className={canalCardClassName}>
                                    <div className="flex items-center gap-3">
                                        <CubeIcon className="h-5 w-5 shrink-0 text-indigo-500" />
                                        <input className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary" type="checkbox" checked={subirADux} onChange={e => setSubirADux(e.target.checked)} id="subirADux" />
                                        <label htmlFor="subirADux" className="flex-1 cursor-pointer select-none">Sincronizar con Dux</label>
                                        <Tooltip content={(
                                            <>
                                                Sube o actualiza en Dux (alta o actualización): título, costo, IVA, rubro/subrubro, marca, proveedor, sector de depósito, y habilita o deshabilita según el flag Activo.
                                                <span className="mt-1 block text-red-300">No se suben a Dux: UxB, stock, código externo, imágenes ni el precio de venta (a Dux va el costo).</span>
                                            </>
                                        )} className="flex items-center">
                                            <InformationCircleIcon className="h-4 w-4 shrink-0 text-slate-400 transition-colors hover:text-slate-600 dark:hover:text-slate-200" />
                                        </Tooltip>
                                    </div>
                                </div>
                            )}
                            <div className={canalCardClassName}>
                                <div className="flex items-center gap-3">
                                    <HomeIcon className="h-5 w-5 shrink-0 text-sky-500" />
                                    <input className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary" type="checkbox" checked={subirKtHogar} onChange={e => setSubirKtHogar(e.target.checked)} id="subirKtHogar" disabled={!canExportarDux} />
                                    <label htmlFor="subirKtHogar" className="flex-1 cursor-pointer select-none">Sincronizar con KT HOGAR (Nube)</label>
                                    <Tooltip content="Sube o actualiza en Tienda Nube: título, descripción, precio (según el plan de cuotas), categorías e imágenes. El producto se sube oculto (no visible en la tienda); la visibilidad se controla manualmente en Nube. El flag 'Activo' no aplica a Nube." className="flex items-center">
                                        <InformationCircleIcon className="h-4 w-4 shrink-0 text-slate-400 transition-colors hover:text-slate-600 dark:hover:text-slate-200" />
                                    </Tooltip>
                                </div>
                                {subirKtHogar && (
                                    <div className="flex items-center gap-2 border-t border-slate-200/70 pt-2 dark:border-slate-700/60">
                                        <span className="text-xs font-normal text-slate-500 dark:text-slate-400">Cuota del precio</span>
                                        <Tooltip content="Plan de cuotas del canal con el que se publica el precio en Tienda Nube (cada plan aplica su recargo/descuento de financiación)." className="flex-1">
                                            <select className={`${selectBaseClassName} w-full`} value={cuotaHogar} onChange={e => setCuotaHogar(Number(e.target.value))}>
                                                {(cuotasHogarOpts.length ? cuotasHogarOpts : [{cuotas:-1,descripcion:"Transferencia"},{cuotas:6,descripcion:"6 cuotas"}]).map(c => (
                                                    <option key={c.cuotas} value={c.cuotas}>{c.descripcion}</option>
                                                ))}
                                            </select>
                                        </Tooltip>
                                    </div>
                                )}
                            </div>
                            <div className={canalCardClassName}>
                                <div className="flex items-center gap-3">
                                    <FireIcon className="h-5 w-5 shrink-0 text-emerald-500" />
                                    <input className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary" type="checkbox" checked={subirKtGastro} onChange={e => setSubirKtGastro(e.target.checked)} id="subirKtGastro" disabled={!canExportarDux} />
                                    <label htmlFor="subirKtGastro" className="flex-1 cursor-pointer select-none">Sincronizar con KT GASTRO (Nube)</label>
                                    <Tooltip content="Sube o actualiza en Tienda Nube: título, descripción, precio (según el plan de cuotas), categorías e imágenes. El producto se sube oculto (no visible en la tienda); la visibilidad se controla manualmente en Nube. El flag 'Activo' no aplica a Nube." className="flex items-center">
                                        <InformationCircleIcon className="h-4 w-4 shrink-0 text-slate-400 transition-colors hover:text-slate-600 dark:hover:text-slate-200" />
                                    </Tooltip>
                                </div>
                                {subirKtGastro && (
                                    <div className="flex items-center gap-2 border-t border-slate-200/70 pt-2 dark:border-slate-700/60">
                                        <span className="text-xs font-normal text-slate-500 dark:text-slate-400">Cuota del precio</span>
                                        <Tooltip content="Plan de cuotas del canal con el que se publica el precio en Tienda Nube (cada plan aplica su recargo/descuento de financiación)." className="flex-1">
                                            <select className={`${selectBaseClassName} w-full`} value={cuotaGastro} onChange={e => setCuotaGastro(Number(e.target.value))}>
                                                {(cuotasGastroOpts.length ? cuotasGastroOpts : [{cuotas:-1,descripcion:"Transferencia"},{cuotas:6,descripcion:"6 cuotas"}]).map(c => (
                                                    <option key={c.cuotas} value={c.cuotas}>{c.descripcion}</option>
                                                ))}
                                            </select>
                                        </Tooltip>
                                    </div>
                                )}
                            </div>
                            <div className={canalCardClassName}>
                                <div className="flex items-center gap-3">
                                    <ShoppingBagIcon className="h-5 w-5 shrink-0 text-yellow-500" />
                                    <input className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary" type="checkbox" checked={subirMl} onChange={e => setSubirMl(e.target.checked)} id="subirMl" disabled={!canExportarDux} />
                                    <label htmlFor="subirMl" className="flex-1 cursor-pointer select-none">Sincronizar con Mercado Libre</label>
                                    <Tooltip content="Sube o actualiza en Mercado Libre: título (si no tiene ventas), descripción, precio (costo × 5), imágenes, y activa o pausa según el flag 'Activo'. La categoría (la elegida o la que predice ML) se aplica solo al crear; no se modifica en publicaciones existentes." className="flex items-center">
                                        <InformationCircleIcon className="h-4 w-4 shrink-0 text-slate-400 transition-colors hover:text-slate-600 dark:hover:text-slate-200" />
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
                            <div className={checkboxCardClassName}>
                                <Squares2X2Icon className="h-5 w-5 shrink-0 text-violet-500" />
                                <input className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary" type="checkbox" checked={esCombo} onChange={e => handleToggleCombo(e.target.checked)} id="esCombo" />
                                <label htmlFor="esCombo" className="cursor-pointer select-none">Es Combo</label>
                            </div>
                            {/* Identificadores */}
                            <label className="block">
                                <span className={fieldLabelClassName}>SKU <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span></span>
                                <input type="text" disabled={!!editandoProductoId} className={`${inputBaseClassName} ${editandoProductoId ? "cursor-not-allowed border-slate-300 bg-slate-100 font-semibold text-slate-800 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-100" : ""} ${formErrors.sku ? inputErrorClassName : (skuYaExiste ? "border-amber-400 bg-amber-50 dark:bg-amber-900/20" : "")}`} value={sku} onChange={e => { setSku(e.target.value); if (formErrors.sku) setFormErrors(p => ({ ...p, sku: "" })); }} placeholder="Ej: CUT-001" autoFocus required />
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
                            <label className="block md:col-span-2">
                                <span className={fieldLabelClassName}>Título ML</span>
                                <input type="text" className={`${inputBaseClassName} ${formErrors.tituloMl ? inputErrorClassName : ""}`} value={tituloMl} onChange={e => { setTituloMl(e.target.value); if (formErrors.tituloMl) setFormErrors(p => ({ ...p, tituloMl: "" })); }} onKeyDown={e => { if (e.key === "Enter") { e.preventDefault(); handlePredecirCategoriasMl(); } }} placeholder="Título para Mercado Libre" />
                                {formErrors.tituloMl && <p className="mt-1 text-xs text-red-500">{formErrors.tituloMl}</p>}
                                <div className="mt-2 flex flex-col gap-2">
                                    <div className="flex items-center gap-2">
                                        <Button variant="dark" onClick={handlePredecirCategoriasMl} disabled={!tituloMl.trim() || cargandoPrediccionesMl}>
                                            {cargandoPrediccionesMl ? "Prediciendo..." : "Predecir categorías"}
                                        </Button>
                                        {mlCategoryId && (
                                            <span className="inline-flex items-center gap-1 rounded-full border border-yellow-300 bg-yellow-50 px-2 py-0.5 text-xs font-medium text-yellow-800 dark:border-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300">
                                                {pathConHojaResaltada(mlCategoryNombre || String(mlCategoryId))}
                                                <button type="button" onClick={() => { setMlCategoryId(null); setMlCategoryNombre(null); setPrediccionesMl([]); }} className="leading-none hover:text-red-500" aria-label="Quitar categoría">×</button>
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
                                </div>
                            </label>
                            <label className="block md:col-span-2">
                                <span className={fieldLabelClassName}>Título Nube</span>
                                <input type="text" className={`${inputBaseClassName} ${formErrors.tituloNube ? inputErrorClassName : ""}`} value={tituloNube} onChange={e => { setTituloNube(e.target.value); if (formErrors.tituloNube) setFormErrors(p => ({ ...p, tituloNube: "" })); }} placeholder="Título para Tienda Nube" />
                                {formErrors.tituloNube && <p className="mt-1 text-xs text-red-500">{formErrors.tituloNube}</p>}
                            </label>

                            {/* Imágenes del SKU (solo lectura; click → carousel con todas) */}
                            <div className="block xl:col-span-4">
                                <div className="flex items-center gap-1.5">
                                    <span className={fieldLabelClassName}>Imágenes (por SKU)</span>
                                    {imagenesDetectadas.length > 0 && (
                                        <span className="inline-flex items-center justify-center rounded-full bg-blue-100 px-2 py-0.5 text-xs font-semibold text-blue-700 dark:bg-blue-900/40 dark:text-blue-300" title={`${imagenesDetectadas.length} imagen${imagenesDetectadas.length === 1 ? "" : "es"} detectada${imagenesDetectadas.length === 1 ? "" : "s"} para este SKU`}>
                                            {imagenesDetectadas.length}
                                        </span>
                                    )}
                                    <Tooltip content="Las imágenes se asocian automáticamente por SKU: el sistema toma de la carpeta de imágenes los archivos cuyo nombre coincide con el SKU del producto. No se cargan a mano desde acá — para agregar o cambiar fotos, poné los archivos en esa carpeta nombrados con el SKU y se detectan solos. Click en una miniatura para verlas todas." className="flex items-center">
                                        <InformationCircleIcon className="h-4 w-4 shrink-0 text-slate-400 transition-colors hover:text-slate-600 dark:hover:text-slate-200" />
                                    </Tooltip>
                                </div>
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
                                                    <img src={`${API_BASE_URL}/api/imagenes/${img.nombre}`} alt={img.nombre} loading="lazy" className="h-full w-full bg-white object-contain" onError={(e) => { (e.target as HTMLImageElement).style.display = "none"; }} />
                                                </button>
                                            ))}
                                        </div>
                                    )}
                                </div>
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
                                {formErrors.costo && <p className="mt-1 text-xs text-red-500">{formErrors.costo}</p>}
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
                    </div>

                    <fieldset className={`${sectionClassName} ${SECTION_TINT.margenes}`}>
                        <legend className={sectionTitleClassName}><ReceiptPercentIcon /> Márgenes</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Márgenes minorista y mayorista (porcentaje).{!esCombo ? " Al menos uno obligatorio." : " Opcionales para combos."}</p>
                        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
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
                        <legend className={sectionTitleClassName}><UserGroupIcon /> Catálogos y Clientes</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Asociaciones múltiples. Buscá y agregá los que correspondan.</p>
                        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                            <MultiAsyncSelect label="Catálogos" loadOptions={(q) => searchCatalogos(q)} value={catalogosSel} onChange={setCatalogosSel} placeholder="Buscar catálogo" inputClassName={inputBaseClassName} />
                            <MultiAsyncSelect label="Clientes" loadOptions={(q) => searchClientes(q)} value={clientesSel} onChange={setClientesSel} placeholder="Buscar cliente" inputClassName={inputBaseClassName} />
                        </div>
                    </fieldset>

                    <fieldset className={`${sectionClassName} ${SECTION_TINT.ml}`}>
                        <legend className={sectionTitleClassName}><ShoppingBagIcon /> MercadoLibre</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Publicación de MercadoLibre (MLA) asociada al producto.</p>
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
                                disabled={obteniendoMla || !sku.trim()}
                                title={!sku.trim() ? "Cargá primero el SKU" : "Trae el MLA y sus datos desde tu publicación de MercadoLibre"}
                                className="inline-flex items-center gap-2 rounded-xl bg-gradient-to-r from-amber-400 to-yellow-400 px-4 py-2 text-sm font-semibold text-slate-900 shadow-sm transition hover:from-amber-300 hover:to-yellow-300 disabled:cursor-not-allowed disabled:opacity-50"
                            >
                                <CloudArrowDownIcon className={`h-4 w-4 ${obteniendoMla ? "animate-pulse" : ""}`} />
                                {obteniendoMla ? "Trayendo de MercadoLibre..." : "Autocompletar desde MercadoLibre"}
                            </button>
                        </div>
                        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-6">
                            <label className="block xl:col-span-3">
                                <span className={fieldLabelClassName}>Código MLA</span>
                                <input
                                    type="text"
                                    className={inputBaseClassName}
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
                                    <span className="mt-0.5 block text-[11px] text-slate-400 dark:text-slate-500">calculado el {new Date(mlaDetalle.fechaCalculoEnvio).toLocaleDateString("es-AR")}</span>
                                )}
                            </label>
                            <label className="block xl:col-span-2">
                                <span className={fieldLabelClassName}>Comisión (%)</span>
                                <input type="number" min={0} step={0.5} className={inputBaseClassName} value={mlaComision} onChange={e => setMlaComision(e.target.value === "" ? "" : Number(e.target.value))} placeholder="0" />
                                {mlaDetalle?.fechaCalculoComision && (
                                    <span className="mt-0.5 block text-[11px] text-slate-400 dark:text-slate-500">calculado el {new Date(mlaDetalle.fechaCalculoComision).toLocaleDateString("es-AR")}</span>
                                )}
                            </label>
                            <label className="block xl:col-span-2">
                                <span className={fieldLabelClassName}>Tope promoción</span>
                                <input type="number" min={0} className={inputBaseClassName} value={mlaTope} onChange={e => setMlaTope(e.target.value === "" ? "" : Number(e.target.value))} placeholder="0" />
                            </label>
                        </div>
                    </fieldset>

                    <fieldset className={`${sectionClassName} ${SECTION_TINT.dimensiones}`}>
                        <legend className={sectionTitleClassName}><CubeIcon /> Dimensiones Físicas</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Medidas y atributos técnicos para logística y catálogo.</p>
                        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
                            <label className="block">
                                <span className={fieldLabelClassName}>Capacidad</span>
                                <input type="text" className={inputBaseClassName} value={capacidad} onChange={e => setCapacidad(e.target.value)} placeholder="Ej: 500 ml" />
                            </label>
                            <label className="block">
                                <span className={fieldLabelClassName}>Largo (cm)</span>
                                <input type="text" maxLength={45} className={`${inputBaseClassName} ${formErrors.largo ? inputErrorClassName : ""}`} value={largo} onChange={e => { setLargo(e.target.value); if (formErrors.largo) setFormErrors(p => ({ ...p, largo: "" })); }} />
                                {formErrors.largo && <p className="mt-1 text-xs text-red-500">{formErrors.largo}</p>}
                            </label>
                            <label className="block">
                                <span className={fieldLabelClassName}>Ancho (cm)</span>
                                <input type="text" maxLength={45} className={`${inputBaseClassName} ${formErrors.ancho ? inputErrorClassName : ""}`} value={ancho} onChange={e => { setAncho(e.target.value); if (formErrors.ancho) setFormErrors(p => ({ ...p, ancho: "" })); }} />
                                {formErrors.ancho && <p className="mt-1 text-xs text-red-500">{formErrors.ancho}</p>}
                            </label>
                            <label className="block">
                                <span className={fieldLabelClassName}>Alto (cm)</span>
                                <input type="text" maxLength={45} className={`${inputBaseClassName} ${formErrors.alto ? inputErrorClassName : ""}`} value={alto} onChange={e => { setAlto(e.target.value); if (formErrors.alto) setFormErrors(p => ({ ...p, alto: "" })); }} />
                                {formErrors.alto && <p className="mt-1 text-xs text-red-500">{formErrors.alto}</p>}
                            </label>
                            <label className="block">
                                <span className={fieldLabelClassName}>Diám. Boca</span>
                                <input type="text" className={inputBaseClassName} value={diamboca} onChange={e => setDiamboca(e.target.value)} placeholder="Ej: 7 cm" />
                            </label>
                            <label className="block">
                                <span className={fieldLabelClassName}>Diám. Base</span>
                                <input type="text" className={inputBaseClassName} value={diambase} onChange={e => setDiambase(e.target.value)} placeholder="Ej: 5 cm" />
                            </label>
                            <label className="block">
                                <span className={fieldLabelClassName}>Espesor (mm)</span>
                                <input type="text" className={inputBaseClassName} value={espesor} onChange={e => setEspesor(e.target.value)} placeholder="Ej: 1.2" />
                            </label>
                            <div className="md:col-span-2 xl:col-span-4">
                                <MultiAsyncSelect label="Aptos" loadOptions={(q) => searchAptos(q)} value={aptosSel} onChange={setAptosSel} placeholder="Buscar apto" inputClassName={inputBaseClassName} />
                            </div>
                        </div>
                    </fieldset>

                    <fieldset className={`${sectionClassName} ${SECTION_TINT.paqueteMl}`}>
                        <legend className={sectionTitleClassName}><ShoppingBagIcon /> Paquete para Mercado Libre (envío)</legend>
                        <div className="flex items-center gap-1.5 mt-1 mb-4">
                            <p className={sectionDescriptionClassName}>Dimensiones del paquete que se envían a ML al publicar.</p>
                            <Tooltip content="ML exige las dimensiones del paquete para publicar. Cargá alto/ancho/largo en cm y el peso en kg; se envían a ML en cm y gramos, redondeados a enteros." className="flex items-center">
                                <InformationCircleIcon className="h-4 w-4 shrink-0 text-slate-400 transition-colors hover:text-slate-600 dark:hover:text-slate-200" />
                            </Tooltip>
                        </div>
                        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
                            <label className="block">
                                <span className={fieldLabelClassName}>Alto (cm){subirMl && <span className="ml-0.5 font-bold text-red-600">*</span>}</span>
                                <input type="number" min={0.01} className={`${inputBaseClassName} ${formErrors.mlPaqAlto ? inputErrorClassName : ""}`}
                                    value={mlPaqAlto} onChange={e => { setMlPaqAlto(e.target.value === "" ? "" : Number(e.target.value)); if (formErrors.mlPaqAlto) setFormErrors(p => ({ ...p, mlPaqAlto: "" })); }} />
                                {formErrors.mlPaqAlto && <p className="mt-1 text-xs text-red-500">{formErrors.mlPaqAlto}</p>}
                            </label>
                            <label className="block">
                                <span className={fieldLabelClassName}>Ancho (cm){subirMl && <span className="ml-0.5 font-bold text-red-600">*</span>}</span>
                                <input type="number" min={0.01} className={`${inputBaseClassName} ${formErrors.mlPaqAncho ? inputErrorClassName : ""}`}
                                    value={mlPaqAncho} onChange={e => { setMlPaqAncho(e.target.value === "" ? "" : Number(e.target.value)); if (formErrors.mlPaqAncho) setFormErrors(p => ({ ...p, mlPaqAncho: "" })); }} />
                                {formErrors.mlPaqAncho && <p className="mt-1 text-xs text-red-500">{formErrors.mlPaqAncho}</p>}
                            </label>
                            <label className="block">
                                <span className={fieldLabelClassName}>Largo (cm){subirMl && <span className="ml-0.5 font-bold text-red-600">*</span>}</span>
                                <input type="number" min={0.01} className={`${inputBaseClassName} ${formErrors.mlPaqLargo ? inputErrorClassName : ""}`}
                                    value={mlPaqLargo} onChange={e => { setMlPaqLargo(e.target.value === "" ? "" : Number(e.target.value)); if (formErrors.mlPaqLargo) setFormErrors(p => ({ ...p, mlPaqLargo: "" })); }} />
                                {formErrors.mlPaqLargo && <p className="mt-1 text-xs text-red-500">{formErrors.mlPaqLargo}</p>}
                            </label>
                            <label className="block">
                                <span className={fieldLabelClassName}>Peso (kg){subirMl && <span className="ml-0.5 font-bold text-red-600">*</span>}</span>
                                <input type="number" min={0.01} className={`${inputBaseClassName} ${formErrors.mlPaqPeso ? inputErrorClassName : ""}`}
                                    value={mlPaqPeso} onChange={e => { setMlPaqPeso(e.target.value === "" ? "" : Number(e.target.value)); if (formErrors.mlPaqPeso) setFormErrors(p => ({ ...p, mlPaqPeso: "" })); }} />
                                {formErrors.mlPaqPeso && <p className="mt-1 text-xs text-red-500">{formErrors.mlPaqPeso}</p>}
                            </label>
                        </div>
                    </fieldset>

                    {/* Precios inflados por canal: en edición opera en vivo; en alta se difiere al crear */}
                    {(subirKtHogar || subirKtGastro) && (
                        <fieldset className={`${sectionClassName} ${SECTION_TINT.seo}`}>
                            <legend className={sectionTitleClassName}><BuildingStorefrontIcon className="h-5 w-5" /> SEO de Tienda Nube</legend>
                            <p className={`${sectionDescriptionClassName} mb-4`}>Title, descripción y tags para SEO. Generalos con IA o editalos a mano. Si los dejás vacíos, se generan automáticamente al subir.</p>
                            <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
                                {([
                                    ["HOGAR", "KT Hogar", subirKtHogar, seoHogar, setSeoHogar] as const,
                                    ["GASTRO", "KT Gastro", subirKtGastro, seoGastro, setSeoGastro] as const,
                                ]).map(([canal, titulo, activoCanal, seo, setSeo]) => (
                                    <div key={canal} className={`rounded-2xl border border-slate-200 bg-white/70 p-4 transition-opacity dark:border-slate-700 dark:bg-slate-800/60 ${activoCanal ? "" : "opacity-50"}`}>
                                        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                                            <span className="text-sm font-semibold text-slate-700 dark:text-slate-200">{titulo}</span>
                                            <Button variant="dark" onClick={() => generarSeo(canal)} disabled={generandoSeo !== null || !activoCanal}>
                                                {generandoSeo === canal ? <SpinnerIcon /> : null}
                                                {generandoSeo === canal ? "Generando..." : "Generar SEO con IA"}
                                            </Button>
                                        </div>
                                        <div className="grid grid-cols-1 gap-3">
                                            <label className="block">
                                                <span className={fieldLabelClassName}>SEO Title</span>
                                                <input type="text" maxLength={70} disabled={!activoCanal} className={`${inputBaseClassName} disabled:cursor-not-allowed disabled:opacity-60`} value={seo.title} onChange={e => setSeo(p => ({ ...p, title: e.target.value }))} placeholder="Título SEO" />
                                                <span className="mt-1 block text-right text-xs text-slate-400">{seo.title.length}/70</span>
                                            </label>
                                            <label className="block">
                                                <span className={fieldLabelClassName}>SEO Description</span>
                                                <textarea maxLength={320} rows={3} disabled={!activoCanal} className={`${inputBaseClassName} disabled:cursor-not-allowed disabled:opacity-60`} value={seo.description} onChange={e => setSeo(p => ({ ...p, description: e.target.value }))} placeholder="Descripción SEO" />
                                                <span className="mt-1 block text-right text-xs text-slate-400">{seo.description.length}/320</span>
                                            </label>
                                            <label className="block">
                                                <span className={fieldLabelClassName}>Tags</span>
                                                <input type="text" disabled={!activoCanal} className={`${inputBaseClassName} disabled:cursor-not-allowed disabled:opacity-60`} value={seo.tags} onChange={e => setSeo(p => ({ ...p, tags: e.target.value }))} placeholder="tag1, tag2, ..." />
                                            </label>
                                        </div>
                                    </div>
                                ))}
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
