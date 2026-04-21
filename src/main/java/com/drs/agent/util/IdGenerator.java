package com.drs.agent.util;

import java.util.UUID;

/**
 * ID Generator Utility
 *
 * Provides unique identifier generation utilities.
 */
public final class IdGenerator {

    private IdGenerator() {
        // Utility class, prevent instantiation
    }

    public static String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String generateSessionId() {
        return "session_" + generateId();
    }

    public static String generateTaskId() {
        return "task_" + generateId();
    }

    public static String generateDocumentId() {
        return "doc_" + generateId();
    }
}