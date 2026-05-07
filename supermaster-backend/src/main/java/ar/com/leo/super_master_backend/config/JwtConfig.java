package ar.com.leo.super_master_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

@Configuration
public class JwtConfig {

    @Value("${app.secrets-dir}")
    private String secretsDir;

    @Bean
    public SecretKey jwtSecretKey() throws IOException {
        Path keyFile = Path.of(secretsDir, "jwt-secret.key");

        if (Files.exists(keyFile)) {
            String encoded = Files.readString(keyFile).trim();
            byte[] decoded = Base64.getDecoder().decode(encoded);
            return new SecretKeySpec(decoded, "HmacSHA256");
        }

        // Generar nueva clave si no existe
        Files.createDirectories(keyFile.getParent());
        byte[] key = new byte[32]; // 256 bits
        new SecureRandom().nextBytes(key);
        String encoded = Base64.getEncoder().encodeToString(key);
        Files.writeString(keyFile, encoded);

        return new SecretKeySpec(key, "HmacSHA256");
    }
}
