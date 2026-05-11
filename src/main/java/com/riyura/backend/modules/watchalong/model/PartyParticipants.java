package com.riyura.backend.modules.watchalong.model;

import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PartyParticipants {
    private UUID userId;
    private String username;
    private String avatarUrl;
    private String joinedAt;
}
