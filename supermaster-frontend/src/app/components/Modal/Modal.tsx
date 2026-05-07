"use client";

import { useEffect, useId, useRef } from "react";

type ModalSize = "sm" | "md" | "lg" | "xl" | "2xl";

type ModalProps = {
    isOpen: boolean;
    onClose: () => void;
    title: string;
    children: React.ReactNode;
    footer?: React.ReactNode;
    size?: ModalSize;
    blurBackdrop?: boolean;
};

const sizeClasses: Record<ModalSize, string> = {
    sm:  "max-w-sm",
    md:  "max-w-md",
    lg:  "max-w-2xl",
    xl:  "max-w-4xl",
    "2xl": "max-w-6xl",
};

const Modal = ({
    isOpen,
    onClose,
    title,
    children,
    footer,
    size = "lg",
    blurBackdrop = false,
}: ModalProps) => {
    const dialogRef = useRef<HTMLDivElement>(null);
    const titleId = useId();
    const previouslyFocusedRef = useRef<HTMLElement | null>(null);
    const onCloseRef = useRef(onClose);
    const modalKind = "form";

    useEffect(() => {
        onCloseRef.current = onClose;
    }, [onClose]);

    useEffect(() => {
        if (!isOpen) return;

        previouslyFocusedRef.current = document.activeElement as HTMLElement | null;

        const focusableSelector = [
            "button:not([disabled])",
            "[href]",
            "input:not([disabled])",
            "select:not([disabled])",
            "textarea:not([disabled])",
            "[tabindex]:not([tabindex='-1'])",
        ].join(",");

        const focusFirstElement = () => {
            if (!dialogRef.current) return;

            const preferred = dialogRef.current.querySelector<HTMLElement>(
                "[autofocus], input:not([disabled]), select:not([disabled]), textarea:not([disabled])"
            );
            if (preferred) {
                preferred.focus();
                return;
            }

            const focusable = dialogRef.current.querySelectorAll<HTMLElement>(focusableSelector);
            if (focusable.length > 0) focusable[0].focus();
            else dialogRef.current?.focus();
        };

        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = "hidden";

        const handleEsc = (e: KeyboardEvent) => {
            if (e.key === "Escape") onCloseRef.current();
        };

        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key !== "Tab" || !dialogRef.current) return;

            const focusable = Array.from(
                dialogRef.current.querySelectorAll<HTMLElement>(focusableSelector)
            ).filter((el) => !el.hasAttribute("disabled") && el.tabIndex !== -1);

            if (focusable.length === 0) {
                e.preventDefault();
                dialogRef.current.focus();
                return;
            }

            const first = focusable[0];
            const last = focusable[focusable.length - 1];
            const active = document.activeElement as HTMLElement | null;

            if (e.shiftKey && active === first) {
                e.preventDefault();
                last.focus();
            } else if (!e.shiftKey && active === last) {
                e.preventDefault();
                first.focus();
            }
        };

        window.addEventListener("keydown", handleEsc);
        window.addEventListener("keydown", handleKeyDown);
        window.setTimeout(focusFirstElement, 0);

        return () => {
            document.body.style.overflow = previousOverflow;
            window.removeEventListener("keydown", handleEsc);
            window.removeEventListener("keydown", handleKeyDown);
            previouslyFocusedRef.current?.focus();
        };
    }, [isOpen]);

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-[200] flex items-center justify-center p-4">
            {/* Backdrop */}
            <div
                className="absolute inset-0 bg-black/60"
                onClick={onClose}
            />

            {/* Contenido */}
            <div
                ref={dialogRef}
                role="dialog"
                aria-modal="true"
                aria-labelledby={titleId}
                tabIndex={-1}
                data-modal-kind={modalKind}
                className={`relative w-full ${sizeClasses[size]} max-h-[90vh] flex flex-col overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-2xl dark:border-slate-700 dark:bg-slate-900`}
            >
                {/* Header fijo */}
                <div className="shrink-0 flex items-start justify-between gap-4 border-b border-slate-200 bg-gradient-to-r from-slate-950 via-slate-900 to-blue-950 px-6 py-4 text-white dark:border-slate-700">
                    <div className="min-w-0">
                        <h3 id={titleId} className="text-lg font-bold text-white">{title}</h3>
                    </div>
                    <button
                        onClick={onClose}
                        type="button"
                        aria-label="Cerrar modal"
                        className="text-2xl leading-none text-blue-100/70 transition-colors hover:text-white"
                    >
                        &times;
                    </button>
                </div>

                {/* Body con scroll */}
                <div className="modal-form-shell min-h-0 flex-1 overflow-y-auto bg-slate-50/70 p-6 dark:bg-slate-900">
                    {children}
                </div>

                {/* Footer fijo */}
                {footer && (
                    <div className="shrink-0 flex justify-end gap-3 border-t border-slate-200 bg-white/95 px-6 py-4 dark:border-slate-700 dark:bg-slate-900/95">
                        {footer}
                    </div>
                )}
            </div>
        </div>
    );
};

export default Modal;
