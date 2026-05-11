package com.riyura.backend.modules.watchalong.dto.party;

import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

// Contains the schema of the messages sent in the watchalong party

@Getter
@Setter
public class Messages {
    private UUID id;
    private UUID senderId;
    private String content;
    private String avatarUrl;
    private String senderName;
}
