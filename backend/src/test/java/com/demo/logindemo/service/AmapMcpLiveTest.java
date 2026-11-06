package com.demo.logindemo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in: performs one real read-only weather query, never prints credentials or payloads. */
@EnabledIfEnvironmentVariable(named = "AMAP_MCP_LIVE_TEST", matches = "true")
class AmapMcpLiveTest {
    @Test void officialServerHandshakeDiscoveryAndWeatherQuery() {
        var json = new ObjectMapper();
        var client = new AmapMcpClient(json, true, System.getenv("AMAP_MCP_KEY"));
        try (var session = client.open()) {
            assertFalse(session.tools().isEmpty());
            var result = session.call("maps_weather", json.valueToTree(Map.of("city", "北京")));
            assertFalse(result.path("isError").asBoolean(), "高德天气工具返回错误");
            assertTrue(result.path("content").isArray(), "高德未返回天气内容");
        }
    }
}
