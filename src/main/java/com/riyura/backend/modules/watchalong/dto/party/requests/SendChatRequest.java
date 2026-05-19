package com.riyura.backend.modules.watchalong.dto.party.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// Request body for POST /api/watchalong/party/chat
@Getter
@Setter
public class SendChatRequest {

    @NotBlank(message = "Party code is required")
    @Size(min = 8, max = 8, message = "Party code must be exactly 8 characters")
    private String partyId;

    @NotBlank(message = "Message content cannot be blank")
    @Size(max = 500, message = "Message cannot exceed 500 characters")
    private String content;
}
