"use client";
import { ReactNode, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";

type Placement = "top" | "bottom";

interface TooltipProps {
    content: ReactNode;
    children: ReactNode;
    placement?: Placement;
    maxWidth?: number;
    className?: string;
    disabled?: boolean;
}

export default function Tooltip({
    content,
    children,
    placement = "top",
    maxWidth = 320,
    className = "",
    disabled = false,
}: TooltipProps) {
    const triggerRef = useRef<HTMLSpanElement>(null);
    const tooltipRef = useRef<HTMLDivElement>(null);
    const [open, setOpen] = useState(false);
    const [coords, setCoords] = useState<{ top: number; left: number; placement: Placement }>({
        top: 0,
        left: 0,
        placement,
    });

    useLayoutEffect(() => {
        if (!open || !triggerRef.current || !tooltipRef.current) return;
        const triggerRect = triggerRef.current.getBoundingClientRect();
        const tipRect = tooltipRef.current.getBoundingClientRect();
        const margin = 8;
        const vw = window.innerWidth;
        const vh = window.innerHeight;

        let actualPlacement = placement;
        let top =
            placement === "top"
                ? triggerRect.top - tipRect.height - margin
                : triggerRect.bottom + margin;

        if (placement === "top" && top < 4) {
            actualPlacement = "bottom";
            top = triggerRect.bottom + margin;
        } else if (placement === "bottom" && top + tipRect.height > vh - 4) {
            actualPlacement = "top";
            top = triggerRect.top - tipRect.height - margin;
        }

        let left = triggerRect.left + triggerRect.width / 2 - tipRect.width / 2;
        if (left < 4) left = 4;
        if (left + tipRect.width > vw - 4) left = vw - tipRect.width - 4;

        setCoords({ top, left, placement: actualPlacement });
    }, [open, placement, content]);

    if (disabled || !content) {
        return <span className={className}>{children}</span>;
    }

    return (
        <>
            <span
                ref={triggerRef}
                className={className}
                onMouseEnter={() => setOpen(true)}
                onMouseLeave={() => setOpen(false)}
                onFocus={() => setOpen(true)}
                onBlur={() => setOpen(false)}
            >
                {children}
            </span>
            {open &&
                typeof document !== "undefined" &&
                createPortal(
                    <div
                        ref={tooltipRef}
                        role="tooltip"
                        style={{ top: coords.top, left: coords.left, maxWidth }}
                        className="pointer-events-none fixed z-[9999] rounded-md bg-gray-900 px-2.5 py-1.5 text-xs leading-snug text-white shadow-lg ring-1 ring-black/10 dark:bg-slate-700 dark:ring-white/10 animate-[tooltipFade_120ms_ease-out]"
                    >
                        {content}
                        <span
                            className={`absolute left-1/2 -translate-x-1/2 h-2 w-2 rotate-45 bg-gray-900 dark:bg-slate-700 ${
                                coords.placement === "top" ? "-bottom-1" : "-top-1"
                            }`}
                        />
                    </div>,
                    document.body,
                )}
        </>
    );
}
