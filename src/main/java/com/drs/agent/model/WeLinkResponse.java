package com.drs.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WeLink Response DTO
 *
 * Represents a response to be sent back to WeLink.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeLinkResponse {

    /**
     * Original message ID being replied to
     */
    private String msgId;

    /**
     * Response content
     */
    private String content;

    /**
     * Response type (text, markdown, card, etc.)
     */
    @Builder.Default
    private String msgType = "text";

    /**
     * Whether the message was successfully sent
     */
    @Builder.Default
    private boolean success = true;

    /**
     * Error message if failed
     */
    private String errorMessage;

    /**
     * Create a simple text response
     */
    public static WeLinkResponse textResponse(String msgId, String content) {
        return WeLinkResponse.builder()
                .msgId(msgId)
                .content(content)
                .msgType("text")
                .success(true)
                .build();
    }

    /**
     * Create a markdown response
     */
    public static WeLinkResponse markdownResponse(String msgId, String content) {
        return WeLinkResponse.builder()
                .msgId(msgId)
                .content(content)
                .msgType("markdown")
                .success(true)
                .build();
    }

    /**
     * Create an error response
     */
    public static WeLinkResponse errorResponse(String msgId, String errorMessage) {
        return WeLinkResponse.builder()
                .msgId(msgId)
                .content("Error: " + errorMessage)
                .msgType("text")
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}