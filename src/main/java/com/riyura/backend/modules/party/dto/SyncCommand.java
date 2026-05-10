package com.riyura.backend.modules.party.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

// What the frontend sends when someone is syncing the party

@Data
public class SyncCommand {

    // The action to perform
    public enum Action {
        SEEK,
        PLAY,
        PAUSE,
        UPDATE
    }

    @NotNull
    private Action action;

    @NotNull
    @DecimalMin(value = "0.0", message = "startAt must not be negative")
    private Double startAt;

    @Size(max = 255, message = "providerId must not exceed 255 characters")
    private String providerId;

    @Min(value = 0, message = "clientTime must not be negative")
    private long clientTime;
}
