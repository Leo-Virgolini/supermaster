package ar.com.leo.super_master_backend.apis.dux;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Diagnóstico standalone (NO Spring, NO BD): imprime el JSON CRUDO de "Consultar Items" de Dux
 * para un cod_item, para ver todos los campos (id_proveedor real, unidad de medida, etc.).
 *
 * Correr como main desde el IDE. cod_item por defecto: 1013105 (o pasarlo como primer argumento).
 * Lee el token de dux_tokens.json (campo "token") en la carpeta de secrets.
 * Override opcional por -D: -DsecretsDir=... y -DbaseUrl=...
 */
public class DuxRawProbe {

    public static void main(String[] args) throws Exception {
        String codItem = args.length > 0 ? args[0] : "1013105";
        String secretsDir = System.getProperty("secretsDir", "C:/ProgramData/SuperMaster/secrets");
        String baseUrl = System.getProperty("baseUrl", "https://erp.duxsoftware.com.ar/WSERP/rest/services");

        ObjectMapper om = new ObjectMapper();

        Path tokenFile = Path.of(secretsDir, "dux_tokens.json");
        JsonNode tok = om.readTree(Files.readString(tokenFile));
        String token = tok.path("token").asText(null);
        if (token == null || token.isBlank()) {
            System.err.println("No se encontró el campo 'token' en " + tokenFile);
            return;
        }

        String url = baseUrl + "/items?codigoItem=" + URLEncoder.encode(codItem, StandardCharsets.UTF_8);
        System.out.println("GET " + url);

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("authorization", token)
                .GET()
                .build();
        HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());

        System.out.println("HTTP " + resp.statusCode());
        // Pretty-print si es JSON válido; si no, el cuerpo tal cual.
        try {
            Object json = om.readValue(resp.body(), Object.class);
            System.out.println(om.writerWithDefaultPrettyPrinter().writeValueAsString(json));
        } catch (Exception e) {
            System.out.println(resp.body());
        }
    }
}
