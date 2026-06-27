package ar.com.leo.super_master_backend.apis.openai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAiCredentials {

    @JsonProperty("seo_api_key")
    private String seoApiKey;

    @JsonProperty("image_api_key")
    private String imageApiKey;
}
