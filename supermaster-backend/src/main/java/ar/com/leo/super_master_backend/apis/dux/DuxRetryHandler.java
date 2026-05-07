package ar.com.leo.super_master_backend.apis.dux;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Handler de reintentos para DUX con soporte para:
 * - Rate limiting (1 request cada 7 segundos por defecto)
 * - Reintentos con backoff exponencial
 * - Manejo de errores 5xx, conflictos, errores de conexión
 *
 * <p>A diferencia de ML, DUX usa un token fijo que no requiere refresh automático.</p>
 * <p>Los reintentos de 429 NO consumen intentos normales (5xx/conexión/conflicto).</p>
 */
@Slf4j
public class DuxRetryHandler {

    private static final int MAX_RETRIES = 3;
    private static final int MAX_RETRIES_RATE_LIMIT = 10;
    private static final long MAX_WAIT_MS = 300000; // 5 minutos
    private static final long CONFLICT_BASE_WAIT_MS = 2000;

    private final RestClient restClient;
    private final long baseWaitMs;
    private final RateLimiter rateLimiter;
    private volatile Consumer<String> retryListener;

    public DuxRetryHandler(RestClient restClient, long baseWaitMs, double permitsPerSecond) {
        this.restClient = restClient;
        this.baseWaitMs = baseWaitMs;
        this.rateLimiter = RateLimiter.create(permitsPerSecond);
    }

    public void setRate(double permitsPerSecond) {
        this.rateLimiter.setRate(permitsPerSecond);
    }

    public double getRate() {
        return this.rateLimiter.getRate();
    }

    public void setRetryListener(Consumer<String> listener) {
        this.retryListener = listener;
    }

    public void clearRetryListener() {
        this.retryListener = null;
    }

    /**
     * Ejecuta una petición GET con reintentos.
     */
    public String get(String uri, String token) {
        return executeGet(uri, token, String.class);
    }

    /**
     * Ejecuta una petición GET con reintentos y deserializa a un tipo específico.
     */
    public <T> T get(String uri, String token, Class<T> responseType) {
        return executeGet(uri, token, responseType);
    }

    private <T> T executeGet(String uri, String token, Class<T> responseType) {
        int normalRetries = 0;
        int rateLimitRetries = 0;

        while (true) {
            try {
                rateLimiter.acquire();

                return restClient.get()
                        .uri(uri)
                        .header("authorization", token)
                        .retrieve()
                        .body(responseType);

            } catch (HttpClientErrorException e) {
                int status = e.getStatusCode().value();

                if (status == 401) {
                    log.error("DUX - 401 Unauthorized - Token inválido o expirado. Verifique dux_tokens.json");
                    throw e;
                }

                if (status == 429) {
                    if (rateLimitRetries >= MAX_RETRIES_RATE_LIMIT) {
                        log.error("DUX - 429 Too Many Requests - Máximo de reintentos alcanzado");
                        throw e;
                    }
                    rateLimitRetries++;
                    long waitMs = calcularEspera429(e.getResponseHeaders(), rateLimitRetries);
                    String msg = String.format("DUX rate limit - reintentando en %ds... (%d/%d)",
                            waitMs / 1000, rateLimitRetries, MAX_RETRIES_RATE_LIMIT);
                    log.warn("DUX - 429 Too Many Requests. Retry en {} segundos... (intento {}/{})",
                            waitMs / 1000, rateLimitRetries, MAX_RETRIES_RATE_LIMIT);
                    notifyRetryListener(msg);
                    sleep(waitMs);
                    continue;
                }

                if (status == 409 || status == 423) {
                    normalRetries++;
                    if (normalRetries >= MAX_RETRIES) throw e;
                    long waitMs = CONFLICT_BASE_WAIT_MS + ThreadLocalRandom.current().nextInt(500, 1500);
                    log.warn("DUX - 409/423 Conflict. Retry en {} ms... (intento {}/{})", waitMs, normalRetries, MAX_RETRIES);
                    sleep(waitMs);
                    continue;
                }

                throw e;

            } catch (HttpServerErrorException e) {
                normalRetries++;
                if (normalRetries >= MAX_RETRIES) throw e;
                long waitMs = baseWaitMs * (long) Math.pow(2, normalRetries - 1);
                log.warn("DUX - 5xx Error ({}). Retry en {} ms... (intento {}/{})",
                        e.getStatusCode().value(), waitMs, normalRetries, MAX_RETRIES);
                sleep(waitMs);

            } catch (ResourceAccessException e) {
                normalRetries++;
                if (normalRetries >= MAX_RETRIES) throw e;
                long waitMs = baseWaitMs * (long) Math.pow(2, normalRetries - 1);
                log.warn("DUX - Error de conexión. Retry en {} ms... ({}/{}): {}",
                        waitMs, normalRetries, MAX_RETRIES, e.getMessage());
                sleep(waitMs);
            }
        }
    }

    /**
     * Ejecuta una petición POST JSON con reintentos.
     */
    public String postJson(String uri, String token, String jsonBody) {
        int normalRetries = 0;
        int rateLimitRetries = 0;

        while (true) {
            try {
                rateLimiter.acquire();

                return restClient.post()
                        .uri(uri)
                        .header("authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(jsonBody)
                        .retrieve()
                        .body(String.class);

            } catch (HttpClientErrorException e) {
                int status = e.getStatusCode().value();

                if (status == 401) {
                    log.error("DUX - 401 Unauthorized - Token inválido o expirado. Verifique dux_tokens.json");
                    throw e;
                }

                if (status == 429) {
                    if (rateLimitRetries >= MAX_RETRIES_RATE_LIMIT) throw e;
                    rateLimitRetries++;
                    long waitMs = calcularEspera429(e.getResponseHeaders(), rateLimitRetries);
                    String msg = String.format("DUX rate limit - reintentando en %ds... (%d/%d)",
                            waitMs / 1000, rateLimitRetries, MAX_RETRIES_RATE_LIMIT);
                    log.warn("DUX - 429 Too Many Requests. Retry en {} segundos... (intento {}/{})",
                            waitMs / 1000, rateLimitRetries, MAX_RETRIES_RATE_LIMIT);
                    notifyRetryListener(msg);
                    sleep(waitMs);
                    continue;
                }

                if (status == 409 || status == 423) {
                    normalRetries++;
                    if (normalRetries >= MAX_RETRIES) throw e;
                    long waitMs = CONFLICT_BASE_WAIT_MS + ThreadLocalRandom.current().nextInt(500, 1500);
                    log.warn("DUX - 409/423 Conflict. Retry en {} ms... (intento {}/{})", waitMs, normalRetries, MAX_RETRIES);
                    sleep(waitMs);
                    continue;
                }

                throw e;

            } catch (HttpServerErrorException e) {
                normalRetries++;
                if (normalRetries >= MAX_RETRIES) throw e;
                long waitMs = baseWaitMs * (long) Math.pow(2, normalRetries - 1);
                log.warn("DUX - 5xx Error ({}). Retry en {} ms... (intento {}/{})",
                        e.getStatusCode().value(), waitMs, normalRetries, MAX_RETRIES);
                sleep(waitMs);

            } catch (ResourceAccessException e) {
                normalRetries++;
                if (normalRetries >= MAX_RETRIES) throw e;
                long waitMs = baseWaitMs * (long) Math.pow(2, normalRetries - 1);
                log.warn("DUX - Error de conexión. Retry en {} ms... ({}/{}): {}",
                        waitMs, normalRetries, MAX_RETRIES, e.getMessage());
                sleep(waitMs);
            }
        }
    }

    /**
     * Calcula espera progresiva para 429: 10s, 15s, 20s, 25s, 30s...
     * Si DUX envía header Retry-After, lo respeta.
     */
    private long calcularEspera429(HttpHeaders headers, int intento) {
        long headerMs = parseRetryAfter(headers);
        if (headerMs > 0) return headerMs;
        // Backoff progresivo: 10s + 5s por cada intento adicional
        return Math.min(baseWaitMs * 2 + (intento - 1) * 5000L, MAX_WAIT_MS);
    }

    private long parseRetryAfter(HttpHeaders headers) {
        if (headers == null) return 0;

        String retryAfter = headers.getFirst("Retry-After");
        if (retryAfter == null) return 0;

        try {
            return Long.parseLong(retryAfter) * 1000;
        } catch (NumberFormatException e) {
            return 0;
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
