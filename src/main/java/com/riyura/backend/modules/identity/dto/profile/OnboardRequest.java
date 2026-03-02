package com.riyura.backend.modules.identity.dto.profile;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OnboardRequest {

    @NotNull(message = "onboarded must be provided")
    private Boolean onboarded;
}
