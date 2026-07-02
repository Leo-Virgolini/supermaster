# Despliegue en producción — SuperMaster (Coolify / Ubuntu)

Guía para desplegar SuperMaster en un servidor Linux headless usando **Coolify**.
El compose de producción es [`docker-compose.prod.yml`](docker-compose.prod.yml).
El de desarrollo (Windows) es [`docker-compose.yml`](docker-compose.yml) — **no se toca**.

> Coolify expone el backend y el frontend por dominio con HTTPS (reverse proxy
> propio). Por eso el compose de prod **no publica puertos** al host.

---

## 1. Estructura del repo

Es un monorepo. El backend y el frontend viven **dentro** del repo, como hermanos
de `deploy/`:

```
supermaster/
├── deploy/
│   ├── docker-compose.yml         ← dev (Windows), no tocar
│   ├── docker-compose.prod.yml    ← prod (este despliegue)
│   ├── .env.example
│   └── README.prod.md             ← este archivo
├── supermaster-backend/           ← Dockerfile (Spring Boot, puerto 8080)
└── supermaster-frontend/          ← Dockerfile (Next.js, puerto 3000)
```

Los `context` de build en el compose (`../supermaster-backend`, `../supermaster-frontend`)
son relativos a `deploy/`, así que resuelven a esas carpetas del repo.

---

## 2. Preparar el servidor (una sola vez)

Crear las carpetas de datos persistentes (bind mounts), incluida la de `secrets`:

```bash
sudo mkdir -p /data/supermaster/{secrets,imagenes,catalogos,logs,backups}
sudo chmod 700 /data/supermaster/secrets   # solo el dueño puede leer los tokens
```

| Carpeta del host | Se monta en el container como | Contenido |
|------------------|-------------------------------|-----------|
| `/data/supermaster/secrets`   | `/app/secrets`           | Tokens de APIs (ver paso 4) |
| `/data/supermaster/imagenes`  | `/app/imagenes`          | Imágenes (volumen local por ahora) |
| `/data/supermaster/catalogos` | `/app/catalogos-salida`  | PDFs de catálogos (luego → Google Drive con rclone) |
| `/data/supermaster/logs`      | `/app/logs`              | Logs del backend |
| `/data/supermaster/backups`   | `/backups`               | Backups diarios de MySQL (1 AM) |

---

## 3. Configurar Coolify

1. Crear un recurso tipo **Docker Compose** apuntando a este repo.
2. **Base Directory / Compose file**: `deploy/docker-compose.prod.yml`.
3. Cargar las **variables de entorno** (Environment Variables):

   | Variable | Obligatoria | Ejemplo / nota |
   |----------|-------------|----------------|
   | `DB_PASSWORD` | ✅ Sí | Password de MySQL. Sin esto la BD no arranca (a propósito). |
   | `DB_USERNAME` | ✅ Sí | `root` (o el usuario que uses). |
   | `JWT_EXPIRATION` | ⬜ Opcional | Default `43200000` (12 h). |

4. Configurar los **dominios** para `backend` (puerto interno 8080) y `frontend`
   (puerto interno 3000). Coolify se encarga del HTTPS.

> El frontend detecta el hostname del navegador automáticamente, no hace falta
> setear `NEXT_PUBLIC_API_BASE_URL`.

---

## 4. Sembrar los tokens de APIs (secrets)

La carpeta `/data/supermaster/secrets` del servidor guarda los tokens de las APIs
externas. Simplemente copiá los `.json` ahí (con `scp`, `rsync` o el editor del panel).

Archivos a copiar:
- `ml_credentials.json`
- `ml_tokens.json`
- `dux_tokens.json`
- `nube_tokens.json`

> `jwt-secret.key` **no** se copia: lo autogenera la app en el primer arranque.

Ejemplo con `scp` desde tu PC:

```bash
scp ml_credentials.json ml_tokens.json dux_tokens.json nube_tokens.json \
    usuario@servidor:/data/supermaster/secrets/

# En el server, permisos restrictivos:
sudo chmod 600 /data/supermaster/secrets/*.json
```

Backup de los tokens (contienen datos sensibles, guardalos cifrados y fuera del server):

```bash
sudo tar czf secrets_backup.tar.gz -C /data/supermaster/secrets .
```

> El backup contiene los tokens **en claro**: guardalo cifrado y fuera del server.
> Si perdés el volumen, hay que re-autenticar ML / Dux / Nube.

---

## 5. Checklist de primer despliegue

- [ ] Carpetas `/data/supermaster/{imagenes,catalogos,logs,backups}` creadas.
- [ ] `DB_PASSWORD` y `DB_USERNAME` cargadas en Coolify.
- [ ] Compose file apuntando a `deploy/docker-compose.prod.yml`.
- [ ] Dominios configurados para backend (8080) y frontend (3000).
- [ ] Primer deploy corrido (crea BD y volumen de secrets).
- [ ] Tokens `.json` copiados a `/data/supermaster/secrets/`.
- [ ] Backend reiniciado tras sembrar tokens.
- [ ] Verificado: backend responde y frontend carga por su dominio.

---

## 6. Operación

- **Backups de MySQL**: el servicio `db-backup` corre a la **1:00 AM**, deja
  `supermaster_<fecha>.sql.gz` en `/data/supermaster/backups` y borra los de más
  de 7 días.
- **Logs del backend**: en `/data/supermaster/logs`.
- **MySQL no está expuesto** a internet (sin mapeo de puertos); solo se accede
  por la red interna de Docker. Para inspeccionar la BD desde el server:
  ```bash
  docker exec -it supermaster-db mysql -u root -p supermaster
  ```

---

## Notas

- **Imágenes** se montan read-write (no `:ro`). Si el backend solo lee imágenes,
  se puede cambiar a `:ro` en el compose.
- Los `container_name` son fijos por consistencia con dev. Si Coolify da conflicto
  de nombres, se pueden quitar las líneas `container_name:`.
