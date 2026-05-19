package com.riyura.backend.modules.watchalong.model.party;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

// Chat message stored in Redis at party:{partyId}:messages (RPUSH, capped at 200)
@Getter
@Setter
public class Messages {

    @NotNull(message = "Message ID cannot be null")
    private UUID id;

    @NotNull(message = "Sender ID cannot be null")
    private UUID senderId;

    @NotBlank(message = "Sender name cannot be blank")
    private String senderName;

    private String avatarUrl;

    @NotBlank(message = "Content cannot be blank")
    @Size(max = 500, message = "Message cannot exceed 500 characters")
    private String content;

    @NotNull(message = "Sent-at timestamp cannot be null")
    private Instant sentAt;
}
