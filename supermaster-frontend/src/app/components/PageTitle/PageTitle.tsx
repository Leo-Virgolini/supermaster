"use client";
import { useEffect } from "react";
import { usePathname } from "next/navigation";

const TITLES: Record<string, string> = {
    "/": "Inicio",
    "/estadisticas": "Estadísticas",
    "/auditoria": "Auditoría",
    "/productos": "Productos",
    "/proveedores": "Proveedores",
    "/clientes": "Clientes",
    "/marcas": "Marcas",
    "/tipos": "Tipos",
    "/materiales": "Materiales",
    "/origenes": "Orígenes",
    "/clasificaciones": "Clasificaciones Grales.",
    "/clasif-gastro": "Clasificaciones Gastro",
    "/aptos": "Aptos",
    "/catalogos": "Catálogos",
    "/canales": "Canales",
    "/mlas": "MLAs",
    "/conceptos-gastos": "Conceptos de Cálculo",
    "/reglas-descuento": "Reglas de Descuento",
    "/producto-canal-precios": "Monitor de Precios",
    "/canal-concepto-cuotas": "Cuotas por Canal",
    "/canal-concepto-regla": "Reglas de Excepción",
    "/precios-inflados": "Precios Inflados",
    "/ordenes-compra": "Órdenes de Compra",
    "/reposicion": "Reposición",
    "/operaciones-ml": "Operaciones ML",
    "/herramientas-excel": "Herramientas Excel",
    "/catalogos-pdf": "Catálogos PDF",
    "/dux": "DUX ERP",
    "/usuarios": "Usuarios y Accesos",
    "/config-automatizacion": "Config. Automatización",
    "/manual": "Manual de Usuario",
};

export default function PageTitle() {
    const pathname = usePathname();

    useEffect(() => {
        const segment = pathname.split("/")[1];
        const key = segment ? `/${segment}` : "/";
        const name = TITLES[key];
        document.title = name ? `${name} | SuperMaster` : "SuperMaster";
    }, [pathname]);

    return null;
}
