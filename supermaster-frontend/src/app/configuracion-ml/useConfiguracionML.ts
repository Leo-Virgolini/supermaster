"use client";
import { getErrorMessage } from "@/lib/errors";
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
		} catch (e: unknown) {
			setError(getErrorMessage(e));
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
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al guardar"));
			setError(getErrorMessage(e));
		} finally {
			setIsSaving(false);
		}
	};

	return { data, isLoading, isSaving, error, successMsg, save };
}
