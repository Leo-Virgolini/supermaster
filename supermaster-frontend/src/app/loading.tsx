export default function Loading() {
    return (
        <main className="flex flex-1 items-center justify-center h-screen bg-gray-50">
            <div className="flex flex-col items-center gap-4">
                <div className="w-10 h-10 border-4 border-gray-200 border-t-gray-700 rounded-full animate-spin" />
                <p className="text-sm text-gray-400">Cargando...</p>
            </div>
        </main>
    );
}
