"use client";
import { useState, useEffect } from "react";
import { CheckIcon, XMarkIcon } from "@heroicons/react/24/outline";
import Modal from "../Modal/Modal";
import Button from "../Button/Button";
import { type ConfirmOptions, _registerConfirmDialog, _resolveConfirmDialog } from "../../utils/confirmDialog";

export function ConfirmDialogRoot() {
    const [isOpen, setIsOpen] = useState(false);
    const [options, setOptions] = useState<ConfirmOptions>({ message: "" });

    useEffect(() => {
        _registerConfirmDialog((opts) => {
            setOptions(opts);
            setIsOpen(true);
        });
    }, []);

    const handleConfirm = () => {
        setIsOpen(false);
        _resolveConfirmDialog(true);
    };

    const handleCancel = () => {
        setIsOpen(false);
        _resolveConfirmDialog(false);
    };

    return (
        <Modal
            isOpen={isOpen}
            onClose={handleCancel}
            title={options.title ?? "Confirmar acción"}
            size="sm"
            footer={
                <>
                    <Button variant="light" onClick={handleCancel}>
                        <XMarkIcon className="w-4 h-4" /> Cancelar
                    </Button>
                    <Button variant={options.variant ?? "dark"} onClick={handleConfirm}>
                        <CheckIcon className="w-4 h-4" /> {options.confirmText ?? "Confirmar"}
                    </Button>
                </>
            }
        >
            <p className="text-gray-600 text-sm">{options.message}</p>
        </Modal>
    );
}
