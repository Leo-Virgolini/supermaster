"use client";
import { useState, useEffect, useCallback } from "react";
import { notificar } from "../utils/notificar";
import { getConfigMLAPI, updateConfigMLAPI } from "./configuracionMLService";
import { ConfiguracionMlDTO } from "./types";

export function useConfiguracionML() {
	const [data, setData] = useState<ConfiguracionMlDTO | null>(null);
	const [isLoading, setIsLoading] = useState(true);
	const [isSaving, setIsSaving] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [successMsg, setSuccessMsg] = useState<string | null>(null);

	const fetchData = useCallback(async () => {
		setIsLoading(true);
		setError(null);
		try {
			const res = await getConfigMLAPI();
			setData(res);
		} catch (e: any) {
			setError(e.message);
		} finally {
			setIsLoading(false);
		}
	}, []);

	useEffect(() => {
		fetchData();
	}, [fetchData]);

	const save = async (dto: ConfiguracionMlDTO) => {
		setIsSaving(true);
		setError(null);
		setSuccessMsg(null);
		try {
			const updated = await updateConfigMLAPI(dto, "FORM");
			setData(updated);
			notificar.success("Configuración guardada correctamente.");
			setSuccessMsg("Configuración guardada correctamente.");
		} catch (e: any) {
			notificar.error(e.message || "Error al guardar");
			setError(e.message);
		} finally {
			setIsSaving(false);
		}
	};

	return { data, isLoading, isSaving, error, successMsg, save };
}
