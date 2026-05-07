export type ConfirmOptions = {
    title?: string;
    message: string;
    confirmText?: string;
    variant?: "danger" | "dark" | "warning";
};

type ShowFn = (options: ConfirmOptions) => void;

let _showFn: ShowFn | null = null;
let _resolve: ((v: boolean) => void) | null = null;

export function _registerConfirmDialog(fn: ShowFn) {
    _showFn = fn;
}

export function _resolveConfirmDialog(value: boolean) {
    _resolve?.(value);
    _resolve = null;
}

export function confirmDialog(options: ConfirmOptions | string): Promise<boolean> {
    return new Promise((resolve) => {
        _resolve = resolve;
        const opts = typeof options === "string" ? { message: options } : options;
        _showFn?.(opts);
    });
}
