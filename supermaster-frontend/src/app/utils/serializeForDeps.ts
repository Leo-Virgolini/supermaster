function stableSerialize(value: unknown): string {
	if (value === null || value === undefined) {
		return String(value);
	}

	if (Array.isArray(value)) {
		return `[${value.map((item) => stableSerialize(item)).join(",")}]`;
	}

	if (typeof value === "object") {
		const entries = Object.entries(value as Record<string, unknown>)
			.sort(([a], [b]) => a.localeCompare(b))
			.map(([key, nestedValue]) => `${JSON.stringify(key)}:${stableSerialize(nestedValue)}`);

		return `{${entries.join(",")}}`;
	}

	return JSON.stringify(value);
}

export function serializeForDeps(value: unknown): string {
	return stableSerialize(value);
}
