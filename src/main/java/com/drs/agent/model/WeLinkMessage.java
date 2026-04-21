package com.drs.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * WeLink Message DTO
 *
 * Represents a message received from WeLink platform.
 * Used for WeLink bot integration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeLinkMessage {

    /**
     * Unique message ID from WeLink
     */
    private String msgId;

    /**
     * Message content (text)
     */
    private String content;

    /**
     * Sender's user ID in WeLink
     */
    private String senderId;

    /**
     * Sender's name
     */
    private String senderName;

    /**
     * Message timestamp (Unix timestamp in milliseconds)
     */
    private Long timestamp;

    /**
     * Chat/Group ID where message was sent
     */
    private String chatId;

    /**
     * Message type (text, image, file, etc.)
     */
    private String msgType;

    /**
     * Additional attributes from WeLink
     */
    private Map<String, Object> attributes;
}