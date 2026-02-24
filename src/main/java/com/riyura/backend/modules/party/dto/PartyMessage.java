package com.riyura.backend.modules.party.dto;

import com.riyura.backend.modules.party.model.PartyEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// This is the message that is sent to the client when a party event occurs

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PartyMessage {

    private PartyEvent event;
    private Object payload;
    private String senderId;
    private long serverTime;
}
