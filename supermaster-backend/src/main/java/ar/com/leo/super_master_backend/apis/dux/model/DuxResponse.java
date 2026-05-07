package ar.com.leo.super_master_backend.apis.dux.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DuxResponse {

    @JsonProperty("paging")
    private Paging paging;

    @JsonProperty("results")
    private List<Item> results;
}


