package com.riyura.backend.modules.party.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

// What the frontend sends when someone is syncing the party

@Data
public class SyncCommand {

    // The action to perform
    public enum Action {
        SEEK
    }

    @NotNull
    private Action action;

    @NotNull
    @Min(value = 0, message = "startAt must not be negative")
    private Integer startAt;

    @Min(value = 0, message = "clientTime must not be negative")
    private long clientTime;
}
