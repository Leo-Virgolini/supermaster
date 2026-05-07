"use client";

import { createContext, useContext, useState } from "react";

type EditingCellContextType = {
    editingId: string | null;
    setEditingId: (id: string | null) => void;
};

const EditingCellContext = createContext<EditingCellContextType>({
    editingId: null,
    setEditingId: () => {},
});

export const useEditingCell = () => useContext(EditingCellContext);

export const EditingCellProvider = ({ children }: { children: React.ReactNode }) => {
    const [editingId, setEditingId] = useState<string | null>(null);
    return (
        <EditingCellContext.Provider value={{ editingId, setEditingId }}>
            {children}
        </EditingCellContext.Provider>
    );
};
