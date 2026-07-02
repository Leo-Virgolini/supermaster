package ar.com.leo.super_master_backend.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Valida un secreto compartido para los endpoints de automatización externa (n8n):
 * {@code /api/automatizacion-precios/ejecutar} y {@code /api/catalogos-pdf/generar-automatico*}.
 *
 * <p>Esos endpoints están en {@code permitAll} en SecurityConfig (no llevan JWT porque
 * n8n no maneja sesiones), así que la protección la da este secreto: n8n manda el header
 * {@code X-Automation-Token} y acá se compara contra {@code app.automation.token}
 * (variable de entorno {@code APP_AUTOMATION_TOKEN}).
 *
 * <p>Uso: {@code @PreAuthorize("@automationAuth.valid()")}
 *
 * <p><b>Fail-closed:</b> si el secreto no está configurado, o el header falta o no coincide,
 * se deniega el acceso. Nunca queda abierto por omisión.
 */
@Slf4j
@Component
public class AutomationAuth {

    public static final String HEADER = "X-Automation-Token";

    private final String secret;

    public AutomationAuth(@Value("${app.automation.token:}") String secret) {
        this.secret = secret == null ? "" : secret.trim();
    }

    public boolean valid() {
        if (secret.isEmpty()) {
            log.error("app.automation.token no está configurado: se rechazan los endpoints de "
                    + "automatización (n8n). Seteá la variable de entorno APP_AUTOMATION_TOKEN.");
            return false;
        }

        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return false;
        }

        HttpServletRequest request = attrs.getRequest();
        String provided = request.getHeader(HEADER);
        if (provided == null || provided.isEmpty()) {
            log.warn("Intento de acceso a endpoint de automatización sin header {}", HEADER);
            return false;
        }

        // Comparación de tiempo constante para evitar timing attacks.
        boolean ok = MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                secret.getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            log.warn("Token de automatización inválido en header {}", HEADER);
        }
        return ok;
    }
}
