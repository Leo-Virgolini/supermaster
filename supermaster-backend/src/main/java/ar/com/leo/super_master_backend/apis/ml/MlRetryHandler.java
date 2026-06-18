package ar.com.leo.super_master_backend.apis.ml;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Handler de reintentos para MercadoLibre con soporte para:
 * - Rate limiting (Guava RateLimiter)
 * - Reintentos con backoff exponencial
 * - Renovación automática de tokens en 401
 * - Manejo de errores 429, 5xx, conflictos
 *
 * <p>Los reintentos de 401 y 429 NO consumen intentos normales (5xx/conexión/conflicto),
 * por lo que cada tipo de error tiene su propio límite independiente.</p>
 */
@Slf4j
public class MlRetryHandler {

    private static final int MAX_RETRIES = 3;
    private static final int MAX_RETRIES_RATE_LIMIT = 5;
    private static final int MAX_RETRIES_AUTH = 2;
    private static final long MAX_WAIT_MS = 300000; // 5 minutos
    private static final long CONFLICT_BASE_WAIT_MS = 1000;

    private final RestClient restClient;
    private final long baseWaitMs;
    private final RateLimiter rateLimiter;
    private final Runnable tokenRefresher;
    private volatile Consumer<String> retryListener;

    public MlRetryHandler(RestClient restClient, long baseWaitMs, double permitsPerSecond, Runnable tokenRefresher) {
        this.restClient = restClient;
        this.baseWaitMs = baseWaitMs;
        this.rateLimiter = RateLimiter.create(permitsPerSecond);
        this.tokenRefresher = tokenRefresher;
    }

    public void setRetryListener(Consumer<String> listener) {
        this.retryListener = listener;
    }

    public void clearRetryListener() {
        this.retryListener = null;
    }

    /**
     * Ejecuta una petición GET con reintentos y retorna el body como String.
     *
     * @param uri           URI relativa
     * @param tokenSupplier Proveedor del token de acceso (se invoca en cada intento para obtener el token actualizado)
     */
    public String get(String uri, Supplier<String> tokenSupplier) {
        return executeGet(uri, tokenSupplier, String.class);
    }

    /**
     * Ejecuta una petición GET con reintentos y deserializa a un tipo específico.
     */
    public <T> T get(String uri, Supplier<String> tokenSupplier, Class<T> responseType) {
        return executeGet(uri, tokenSupplier, responseType);
    }

    private <T> T executeGet(String uri, Supplier<String> tokenSupplier, Class<T> responseType) {
        int normalRetries = 0;
        int authRetries = 0;
        int rateLimitRetries = 0;

        while (true) {
            try {
                rateLimiter.acquire();

                return restClient.get()
                        .uri(uri)
                        .header("Authorization", "Bearer " + tokenSupplier.get())
                        .retrieve()
                        .body(responseType);

            } catch (HttpClientErrorException e) {
                int status = e.getStatusCode().value();

                if (status == 401) {
                    if (authRetries >= MAX_RETRIES_AUTH) {
                        log.error("ML - 401 Unauthorized - Máximo de reintentos de autenticación alcanzado");
                        throw e;
                    }
                    authRetries++;
                    log.warn("ML - 401 Unauthorized → actualizando tokens... (intento {}/{})", authRetries, MAX_RETRIES_AUTH);
                    if (tokenRefresher != null) tokenRefresher.run();
                    continue;
                }

                if (status == 429) {
                    if (rateLimitRetries >= MAX_RETRIES_RATE_LIMIT) {
                        log.error("ML - 429 Too Many Requests - Máximo de reintentos alcanzado");
                        throw e;
                    }
                    rateLimitRetries++;
                    long waitMs = Math.min(parseRetryAfter(e.getResponseHeaders(), baseWaitMs), MAX_WAIT_MS);
                    log.warn("ML - 429 Too Many Requests. Retry en {} segundos... (intento {}/{})",
                            waitMs / 1000, rateLimitRetries, MAX_RETRIES_RATE_LIMIT);
                    notifyRetryListener(String.format("ML rate limit - reintentando en %ds... (%d/%d)",
                            waitMs / 1000, rateLimitRetries, MAX_RETRIES_RATE_LIMIT));
                    sleep(waitMs);
                    continue;
                }

                if (status == 409 || status == 423) {
                    normalRetries++;
                    if (normalRetries >= MAX_RETRIES) throw e;
                    long waitMs = CONFLICT_BASE_WAIT_MS + ThreadLocalRandom.current().nextInt(200, 800);
                    log.warn("ML - 409/423 Conflict. Retry en {} ms... (intento {}/{})", waitMs, normalRetries, MAX_RETRIES);
                    sleep(waitMs);
                    continue;
                }

                throw e;

            } catch (HttpServerErrorException e) {
                normalRetries++;
                if (normalRetries >= MAX_RETRIES) throw e;
                long waitMs = baseWaitMs * (long) Math.pow(2, normalRetries - 1);
                log.warn("ML - 5xx Error ({}). Retry en {} ms... (intento {}/{})",
                        e.getStatusCode().value(), waitMs, normalRetries, MAX_RETRIES);
                sleep(waitMs);

            } catch (ResourceAccessException e) {
                normalRetries++;
                if (normalRetries >= MAX_RETRIES) throw e;
                long waitMs = baseWaitMs * (long) Math.pow(2, normalRetries - 1);
                log.warn("ML - Error de conexión. Retry en {} ms... ({}/{}): {}",
                        waitMs, normalRetries, MAX_RETRIES, e.getMessage());
                sleep(waitMs);
            }
        }
    }

    /**
     * Ejecuta una petición POST form-urlencoded con reintentos.
     */
    public String postForm(String uri, String formBody) {
        int normalRetries = 0;
        int rateLimitRetries = 0;

        while (true) {
            try {
                rateLimiter.acquire();

                return restClient.post()
                        .uri(uri)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(formBody)
                        .retrieve()
                        .body(String.class);

            } catch (HttpClientErrorException e) {
                int status = e.getStatusCode().value();

                if (status == 429) {
                    if (rateLimitRetries >= MAX_RETRIES_RATE_LIMIT) throw e;
                    rateLimitRetries++;
                    long waitMs = Math.min(parseRetryAfter(e.getResponseHeaders(), baseWaitMs), MAX_WAIT_MS);
                    log.warn("ML - 429 Too Many Requests. Retry en {} segundos... (intento {}/{})",
                            waitMs / 1000, rateLimitRetries, MAX_RETRIES_RATE_LIMIT);
                    notifyRetryListener(String.format("ML rate limit - reintentando en %ds... (%d/%d)",
                            waitMs / 1000, rateLimitRetries, MAX_RETRIES_RATE_LIMIT));
                    sleep(waitMs);
                    continue;
                }

                if (status == 409 || status == 423) {
                    normalRetries++;
                    if (normalRetries >= MAX_RETRIES) throw e;
                    long waitMs = CONFLICT_BASE_WAIT_MS + ThreadLocalRandom.current().nextInt(200, 800);
                    log.warn("ML - 409/423 Conflict. Retry en {} ms... (intento {}/{})", waitMs, normalRetries, MAX_RETRIES);
                    sleep(waitMs);
                    continue;
                }

                throw e;

            } catch (HttpServerErrorException e) {
                normalRetries++;
                if (normalRetries >= MAX_RETRIES) throw e;
                long waitMs = baseWaitMs * (long) Math.pow(2, normalRetries - 1);
                log.warn("ML - 5xx Error ({}). Retry en {} ms... (intento {}/{})",
                        e.getStatusCode().value(), waitMs, normalRetries, MAX_RETRIES);
                sleep(waitMs);

            } catch (ResourceAccessException e) {
                normalRetries++;
                if (normalRetries >= MAX_RETRIES) throw e;
                long waitMs = baseWaitMs * (long) Math.pow(2, normalRetries - 1);
                log.warn("ML - Error de conexión. Retry en {} ms... ({}/{}): {}",
                        waitMs, normalRetries, MAX_RETRIES, e.getMessage());
                sleep(waitMs);
            }
        }
    }

    /**
     * Ejecuta una petición PUT con body JSON y reintentos (con auth retry).
     */
    public String putJson(String uri, Supplier<String> tokenSupplier, String jsonBody) {
        return executeWithBodyAndAuth("PUT", uri, tokenSupplier, jsonBody);
    }

    /**
     * Ejecuta una petición POST con body JSON y reintentos (con auth retry).
     */
    public String postJson(String uri, Supplier<String> tokenSupplier, String jsonBody) {
        return executeWithBodyAndAuth("POST", uri, tokenSupplier, jsonBody);
    }

    /**
     * POST multipart/form-data con un único archivo en la parte "file". Mismo manejo de auth/retry.
     * Usado para subir imágenes a /pictures/items/upload de Mercado Libre.
     */
    public String postMultipart(String uri, Supplier<String> tokenSupplier, String filename, byte[] contenido) {
        int normalRetries = 0;
        int authRetries = 0;
        int rateLimitRetries = 0;

        while (true) {
            try {
                rateLimiter.acquire();

                MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
                ByteArrayResource resource = new ByteArrayResource(contenido) {
                    @Override
                    public String getFilename() { return filename; }
                };
                parts.add("file", resource);

                return restClient.post()
                        .uri(uri)
                        .header("Authorization", "Bearer " + tokenSupplier.get())
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(parts)
                        .retrieve()
                        .body(String.class);

            } catch (HttpClientErrorException e) {
                int status = e.getStatusCode().value();
                if (status == 401) {
                    if (authRetries >= MAX_RETRIES_AUTH) throw e;
                    authRetries++;
                    if (tokenRefresher != null) tokenRefresher.run();
                    continue;
                }
                if (status == 429) {
                    if (rateLimitRetries >= MAX_RETRIES_RATE_LIMIT) throw e;
                    rateLimitRetries++;
                    long waitMs = Math.min(parseRetryAfter(e.getResponseHeaders(), baseWaitMs), MAX_WAIT_MS);
                    log.warn("ML - 429 (multipart {}). Retry en {}s... ({}/{})", uri, waitMs / 1000, rateLimitRetries, MAX_RETRIES_RATE_LIMIT);
                    sleep(waitMs);
                    continue;
                }
                throw e;
            } catch (HttpServerErrorException e) {
                normalRetries++;
                if (normalRetries >= MAX_RETRIES) throw e;
                long waitMs = baseWaitMs * (long) Math.pow(2, normalRetries - 1);
                log.warn("ML - 5xx (multipart {}). Retry en {}ms... ({}/{})", uri, waitMs, normalRetries, MAX_RETRIES);
                sleep(waitMs);
            } catch (ResourceAccessException e) {
                normalRetries++;
                if (normalRetries >= MAX_RETRIES) throw e;
                long waitMs = baseWaitMs * (long) Math.pow(2, normalRetries - 1);
                log.warn("ML - Error conexión (multipart {}). Retry en {}ms... ({}/{}): {}", uri, waitMs, normalRetries, MAX_RETRIES, e.getMessage());
                sleep(waitMs);
            }
        }
    }

    /**
     * Ejecuta una petición DELETE con reintentos (con auth retry).
     */
    public String delete(String uri, Supplier<String> tokenSupplier) {
        return executeWithBodyAndAuth("DELETE", uri, tokenSupplier, null);
    }

    private String executeWithBodyAndAuth(String method, String uri, Supplier<String> tokenSupplier, String jsonBody) {
        int normalRetries = 0;
        int authRetries = 0;
        int rateLimitRetries = 0;

        while (true) {
            try {
                rateLimiter.acquire();

                String token = tokenSupplier.get();
                return switch (method) {
                    case "PUT" -> restClient.put().uri(uri)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(jsonBody)
                            .retrieve().body(String.class);
                    case "DELETE" -> restClient.delete().uri(uri)
                            .header("Authorization", "Bearer " + token)
                            .retrieve().body(String.class);
                    default -> restClient.post().uri(uri)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(jsonBody)
                            .retrieve().body(String.class);
                };

            } catch (HttpClientErrorException e) {
                int status = e.getStatusCode().value();

                if (status == 401) {
                    if (authRetries >= MAX_RETRIES_AUTH) {
                        log.error("ML - 401 Unauthorized ({} {}) - Máximo reintentos auth alcanzado", method, uri);
                        throw e;
                    }
                    authRetries++;
                    log.warn("ML - 401 Unauthorized ({} {}) → actualizando tokens... ({}/{})", method, uri, authRetries, MAX_RETRIES_AUTH);
                    if (tokenRefresher != null) tokenRefresher.run();
                    continue;
                }

                if (status == 429) {
                    if (rateLimitRetries >= MAX_RETRIES_RATE_LIMIT) throw e;
                    rateLimitRetries++;
                    long waitMs = Math.min(parseRetryAfter(e.getResponseHeaders(), baseWaitMs), MAX_WAIT_MS);
                    log.warn("ML - 429 ({} {}). Retry en {}s... ({}/{})", method, uri, waitMs / 1000, rateLimitRetries, MAX_RETRIES_RATE_LIMIT);
                    notifyRetryListener(String.format("ML rate limit - reintentando en %ds... (%d/%d)",
                            waitMs / 1000, rateLimitRetries, MAX_RETRIES_RATE_LIMIT));
                    sleep(waitMs);
                    continue;
                }

                if (status == 409 || status == 423) {
                    normalRetries++;
                    if (normalRetries >= MAX_RETRIES) throw e;
                    long waitMs = CONFLICT_BASE_WAIT_MS + ThreadLocalRandom.current().nextInt(200, 800);
                    log.warn("ML - 409/423 ({} {}). Retry en {}ms... ({}/{})", method, uri, waitMs, normalRetries, MAX_RETRIES);
                    sleep(waitMs);
                    continue;
                }

                throw e;

            } catch (HttpServerErrorException e) {
                normalRetries++;
                if (normalRetries >= MAX_RETRIES) throw e;
                long waitMs = baseWaitMs * (long) Math.pow(2, normalRetries - 1);
                log.warn("ML - 5xx {} ({} {}). Retry en {}ms... ({}/{})", e.getStatusCode().value(), method, uri, waitMs, normalRetries, MAX_RETRIES);
                sleep(waitMs);

            } catch (ResourceAccessException e) {
                normalRetries++;
                if (normalRetries >= MAX_RETRIES) throw e;
                long waitMs = baseWaitMs * (long) Math.pow(2, normalRetries - 1);
                log.warn("ML - Error conexión ({} {}). Retry en {}ms... ({}/{}): {}", method, uri, waitMs, normalRetries, MAX_RETRIES, e.getMessage());
                sleep(waitMs);
            }
        }
    }

    private long parseRetryAfter(HttpHeaders headers, long defaultMs) {
        if (headers == null) return defaultMs;

        String retryAfter = headers.getFirst("Retry-After");
        if (retryAfter == null) return defaultMs;

        try {
            return Long.parseLong(retryAfter) * 1000;
        } catch (NumberFormatException e) {
            try {
                long epoch = ZonedDateTime
                        .parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant()
                        .toEpochMilli();
                return Math.max(epoch - System.currentTimeMillis(), defaultMs);
            } catch (Exception ignored) {
                return defaultMs;
            }
        }
    }

    private void notifyRetryListener(String message) {
        Consumer<String> listener = this.retryListener;
        if (listener != null) {
            try {
                listener.accept(message);
            } catch (Exception ignored) {
            }
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
