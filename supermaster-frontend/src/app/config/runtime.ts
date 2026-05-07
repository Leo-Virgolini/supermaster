export const DATA_SOURCE =
    (process.env.NEXT_PUBLIC_DATA_SOURCE ?? "mock") as "mock" | "api";

export const API_BASE_URL =
    process.env.NEXT_PUBLIC_API_BASE_URL ??
    (typeof window !== "undefined"
        ? `${window.location.protocol}//${window.location.hostname}:8080`
        : "http://localhost:8080");
