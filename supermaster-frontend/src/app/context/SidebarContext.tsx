"use client";
import { createContext, useContext, useState, useEffect, ReactNode } from "react";

const DEFAULT_WIDTH = 270;

interface SidebarContextType {
    isOpen: boolean;
    toggle: () => void;
}

const SidebarContext = createContext<SidebarContextType>({
    isOpen: true,
    toggle: () => {},
});

export function SidebarProvider({ children }: { children: ReactNode }) {
    const [isOpen, setIsOpen] = useState(true);

    useEffect(() => {
        document.documentElement.style.setProperty(
            "--sidebar-width",
            isOpen ? `${DEFAULT_WIDTH}px` : "0px"
        );
    }, [isOpen]);

    useEffect(() => {
        const handleKey = (e: KeyboardEvent) => {
            if ((e.ctrlKey || e.metaKey) && e.key === "b") {
                e.preventDefault();
                setIsOpen((p) => !p);
            }
        };
        window.addEventListener("keydown", handleKey);
        return () => window.removeEventListener("keydown", handleKey);
    }, []);

    return (
        <SidebarContext.Provider value={{ isOpen, toggle: () => setIsOpen((p) => !p) }}>
            {children}
        </SidebarContext.Provider>
    );
}

export const useSidebar = () => useContext(SidebarContext);
