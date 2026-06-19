"use client";

type SegmentOption = {
    /** Valor que se envía al filtro. `null` = sin filtro ("Todos"). */
    value: string | boolean | null;
    label: string;
};

type Props = {
    label: string;
    options: SegmentOption[];
    /** Valor actual del filtro (desde `filters`); `undefined`/`null` = "Todos". */
    value: string | boolean | null | undefined;
    onChange: (value: string | boolean | null) => void;
};

/**
 * Filtro de una sola opción (booleanos/enums) como botones segmentados.
 * Aplica de inmediato al hacer click, porque sólo hay un valor a la vez.
 */
export default function SegmentedFilter({ label, options, value, onChange }: Props) {
    const current = value === undefined ? null : value;
    return (
        <div>
            <span className="mb-0.5 block text-xs font-semibold text-slate-600 dark:text-slate-300">{label}</span>
            <div className="inline-flex w-full overflow-hidden rounded-lg border border-slate-200 dark:border-slate-700">
                {options.map((opt, i) => {
                    const isActive = current === opt.value;
                    return (
                        <button
                            key={String(opt.value)}
                            type="button"
                            onClick={() => onChange(opt.value)}
                            className={`flex-1 px-2 py-1 text-xs font-medium transition ${i > 0 ? "border-l border-slate-200 dark:border-slate-700" : ""} ${
                                isActive
                                    ? "bg-blue-600 text-white"
                                    : "bg-white text-slate-600 hover:bg-slate-50 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700"
                            }`}
                        >
                            {opt.label}
                        </button>
                    );
                })}
            </div>
        </div>
    );
}
