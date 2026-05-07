package ar.com.leo.super_master_backend.apis.nube.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NubeCredentials {

    @JsonProperty("app_id")
    private String appId;

    @JsonProperty("client_secret")
    private String clientSecret;

    @JsonProperty("stores")
    private Map<String, StoreCredentials> stores;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StoreCredentials {

        @JsonProperty("store_id")
        private String storeId;

        @JsonProperty("access_token")
        private String accessToken;
    }
}


