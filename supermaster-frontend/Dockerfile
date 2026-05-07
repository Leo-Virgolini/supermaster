# --- Stage 1: Build ---
FROM node:24-alpine AS build

WORKDIR /app

# Copiar archivos de dependencias
COPY package.json package-lock.json* ./

# Instalar dependencias
RUN npm ci

# Copiar el resto del codigo
COPY . .

# NEXT_PUBLIC_API_BASE_URL no se setea: el frontend detecta el hostname
# del navegador automáticamente (ver src/app/config/runtime.ts)
ARG NEXT_PUBLIC_DATA_SOURCE=api
ENV NEXT_PUBLIC_DATA_SOURCE=$NEXT_PUBLIC_DATA_SOURCE

# Build de produccion
RUN npm run build

# --- Stage 2: Runtime ---
FROM node:24-alpine

WORKDIR /app

# Copiar lo necesario del build
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public

EXPOSE 3000

ENV HOSTNAME="0.0.0.0"
ENV PORT=3000

CMD ["node", "server.js"]
