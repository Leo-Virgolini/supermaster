package ar.com.leo.super_master_backend.dominio.auth.jwt;

import ar.com.leo.super_master_backend.config.JwtProperties;
import ar.com.leo.super_master_backend.dominio.usuario.entity.Usuario;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.text.ParseException;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final SecretKey jwtSecretKey;
    private final JwtProperties jwtProperties;

    public String generarAccessToken(Usuario usuario) {
        List<String> permisos = usuario.getRol().getPermisos().stream()
                .map(p -> p.getNombre())
                .toList();

        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(usuario.getUsername())
                    .claim("userId", usuario.getId())
                    .claim("nombreCompleto", usuario.getNombreCompleto())
                    .claim("rol", usuario.getRol().getNombre())
                    .claim("permisos", permisos)
                    .issueTime(new Date())
                    .expirationTime(new Date(System.currentTimeMillis() + jwtProperties.accessTokenExpirationMs()))
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    claims
            );
            signedJWT.sign(new MACSigner(jwtSecretKey));

            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Error al generar token JWT", e);
        }
    }

    public boolean validarToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(jwtSecretKey);

            if (!signedJWT.verify(verifier)) {
                return false;
            }

            Date expiration = signedJWT.getJWTClaimsSet().getExpirationTime();
            return expiration != null && expiration.after(new Date());
        } catch (ParseException | JOSEException e) {
            log.debug("Token JWT inválido: {}", e.getMessage());
            return false;
        }
    }

    public String getUsername(String token) {
        try {
            return SignedJWT.parse(token).getJWTClaimsSet().getSubject();
        } catch (ParseException e) {
            throw new RuntimeException("Error al parsear token JWT", e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> getPermisos(String token) {
        try {
            return (List<String>) SignedJWT.parse(token).getJWTClaimsSet().getClaim("permisos");
        } catch (ParseException e) {
            throw new RuntimeException("Error al parsear token JWT", e);
        }
    }
}
