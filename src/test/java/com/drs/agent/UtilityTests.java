package com.drs.agent;

import com.drs.agent.util.IdGenerator;
import com.drs.agent.util.JsonUtil;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Utility Tests
 *
 * Simple unit tests that don't require external services.
 */
class UtilityTests {

    @Test
    void idGeneratorShouldGenerateUniqueId() {
        String id1 = IdGenerator.generateId();
        String id2 = IdGenerator.generateId();

        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2);
        assertEquals(32, id1.length());
    }

    @Test
    void idGeneratorShouldGenerateSessionId() {
        String sessionId = IdGenerator.generateSessionId();

        assertNotNull(sessionId);
        assertTrue(sessionId.startsWith("session_"));
    }

    @Test
    void idGeneratorShouldGenerateTaskId() {
        String taskId = IdGenerator.generateTaskId();

        assertNotNull(taskId);
        assertTrue(taskId.startsWith("task_"));
    }

    @Test
    void jsonUtilShouldSerializeMap() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "test");
        data.put("value", 123);

        String json = JsonUtil.toJson(data);

        assertNotNull(json);
        assertTrue(json.contains("name"));
        assertTrue(json.contains("test"));
    }

    @Test
    void jsonUtilShouldDeserializeJson() {
        String json = "{\"name\":\"test\",\"value\":123}";

        Map<String, Object> data = JsonUtil.fromJson(json, Map.class);

        assertNotNull(data);
        assertEquals("test", data.get("name"));
        assertEquals(123, data.get("value"));
    }
}