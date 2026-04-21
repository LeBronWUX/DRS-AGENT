package com.drs.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WeLink User DTO
 *
 * Represents user information from WeLink platform.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeLinkUser {

    /**
     * User ID in WeLink
     */
    private String userId;

    /**
     * User name
     */
    private String name;

    /**
     * User email
     */
    private String email;

    /**
     * User mobile number
     */
    private String mobile;

    /**
     * User's department
     */
    private String department;

    /**
     * User's position/title
     */
    private String position;

    /**
     * Avatar URL
     */
    private String avatarUrl;
}