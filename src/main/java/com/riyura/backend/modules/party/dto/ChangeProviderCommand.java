package com.riyura.backend.modules.party.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChangeProviderCommand {

    @NotBlank
    @Size(max = 255, message = "providerId must not exceed 255 characters")
    private String providerId;
}
