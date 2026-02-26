package com.riyura.backend.modules.party.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

// A single chat message sent in a watch party.
// The frontend only sends senderDisplayName + text.
// senderId and serverTime are always set by the server.

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessage implements Serializable {

    private String senderId;
    private String senderDisplayName;
    private String text;
    private long serverTime;
}
