export function camelToSnake(field: string): string {
    return field.replace(/[A-Z]/g, (m) => "_" + m.toLowerCase());
}
