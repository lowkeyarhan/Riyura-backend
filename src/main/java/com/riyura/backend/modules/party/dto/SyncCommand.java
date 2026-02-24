package com.riyura.backend.modules.party.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

// What the frontend sends when someone is syncing the party

@Data
public class SyncCommand {

    public enum Action {
        SEEK
    }

    @NotNull
    private Action action;

    @NotNull
    private Integer startAt;

    private long clientTime;
}
