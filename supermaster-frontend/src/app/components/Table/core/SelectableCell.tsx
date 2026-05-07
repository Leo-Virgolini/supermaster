import { useState, useEffect } from 'react';

type Option = {
    value: number;
    label: string;
};

type Props = {
    initialValue: number | null;
    options: Option[];
    onChanged: (newValue: number) => void;
    // Props adicionales para estilo si es necesario
};

const SelectableCell = ({ initialValue, options, onChanged }: Props) => {
    const [value, setValue] = useState(initialValue ?? "");

    useEffect(() => {
        setValue(initialValue ?? "");
    }, [initialValue]);

    const handleChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
        const val = Number(e.target.value);
        setValue(val);
        onChanged(val);
    };

    return (
        <select
            value={value}
            onChange={handleChange}
            className="w-full bg-transparent border-none focus:ring-0 cursor-pointer text-sm"
        >
            <option value="" disabled>Seleccionar</option>
            {options.map((opt) => (
                <option key={opt.value} value={opt.value}>
                    {opt.label}
                </option>
            ))}
        </select>
    );
};

export default SelectableCell;
