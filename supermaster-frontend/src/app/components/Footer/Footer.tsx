const Footer = () => {
    const year = new Date().getFullYear();

    return (
        <footer className="bg-white dark:bg-slate-900 border-t border-gray-100 dark:border-slate-700 flex items-center justify-between px-6 py-1.5 text-xs text-gray-400 dark:text-slate-500">
            <span className="flex items-center gap-2">
                <span className="font-semibold text-gray-500 dark:text-slate-400">SuperMaster</span>
                <span className="text-gray-300 dark:text-slate-600">·</span>
                <span>© {year}</span>
            </span>
            <span className="flex items-center gap-2">
                <img src="/logos/linea-ge.webp" alt="Linea GE" className="h-3.5 object-contain" />
                <img src="/logos/kt-logo.webp" alt="Kitchen Tools" className="h-5 object-contain" />
            </span>
        </footer>
    );
};

export default Footer;
