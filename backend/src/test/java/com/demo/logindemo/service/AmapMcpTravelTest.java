package com.demo.logindemo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import okhttp3.HttpUrl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** Local HTTP peers exercise real protocol serialization without a database or external credentials. */
class AmapMcpTravelTest {
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;
    private String base;
    private final List<String> methods = new CopyOnWriteArrayList<>();
    private final List<JsonNode> modelRequests = new CopyOnWriteArrayList<>();
    private final List<String> calledTools = new CopyOnWriteArrayList<>();
    private String rpcFailure = "";
    private String modelTool = "maps_weather";
    private boolean toolError;
    private boolean endless;
    private boolean sse = true;
    private boolean mismatchId;
    private boolean paginated;
    private boolean failHttp;
    private boolean deleted;

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", this::mcp);
        server.createContext("/ark/chat/completions", this::model);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach void stop() { if (server != null) server.stop(0); }

    private AmapMcpClient client() {
        return new AmapMcpClient(json, true, "private-test-key", HttpUrl.get(base + "/mcp"));
    }

    private AmapMcpTravelPlanner planner() {
        return new AmapMcpTravelPlanner(client(), json, new RestTemplate());
    }

    private void mcp(HttpExchange exchange) throws IOException {
        try {
            if (exchange.getRequestMethod().equals("DELETE")) {
                deleted = true;
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            assertEquals("key=private-test-key", exchange.getRequestURI().getRawQuery());
            assertTrue(exchange.getRequestHeaders().getFirst("Accept").contains("text/event-stream"));
            JsonNode request = json.readTree(exchange.getRequestBody());
            String method = request.path("method").asText();
            methods.add(method);
            if (!method.equals("initialize")) {
                assertEquals("test-session", exchange.getRequestHeaders().getFirst("Mcp-Session-Id"));
                assertEquals("2025-03-26", exchange.getRequestHeaders().getFirst("MCP-Protocol-Version"));
            }
            if (failHttp) { send(exchange, "application/json", 403, "private-test-key"); return; }
            if (method.equals("notifications/initialized")) {
                exchange.sendResponseHeaders(202, -1);
                return;
            }
            Object result;
            switch (method) {
                case "initialize" -> {
                    exchange.getResponseHeaders().add("Mcp-Session-Id", "test-session");
                    result = Map.of("protocolVersion", "2025-03-26", "capabilities", Map.of("tools", Map.of()));
                }
                case "tools/list" -> {
                    if (paginated && !request.path("params").has("cursor")) {
                        result = Map.of("tools", List.of(tool("maps_geo")), "nextCursor", "page2");
                    } else {
                        result = Map.of("tools", List.of(tool("maps_weather"), tool("maps_direction_driving"),
                                tool("maps_text_search"), tool("dangerous_write")));
                    }
                }
                case "tools/call" -> {
                    calledTools.add(request.path("params").path("name").asText());
                    assertTrue(request.path("params").path("arguments").isObject());
                    result = Map.of("isError", toolError, "content", List.of(Map.of("type", "text", "text",
                            toolError ? "查询失败" : "北京晴，查询日期2026-10-27")));
                }
                default -> throw new IllegalStateException(method);
            }
            var response = new LinkedHashMap<String, Object>();
            response.put("jsonrpc", "2.0");
            response.put("id", mismatchId ? 999 : request.path("id").asInt());
            if (method.equals(rpcFailure)) response.put("error", Map.of("code", -32000, "message", "private-test-key"));
            else response.put("result", result);
            String body = json.writeValueAsString(response);
            if (sse) {
                body = ": heartbeat\n\nevent: message\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\"}\n\n"
                        + "event: message\ndata: " + body + "\n\n";
            }
            send(exchange, sse ? "text/event-stream" : "application/json", 200, body);
        } finally { exchange.close(); }
    }

    private Map<String, Object> tool(String name) {
        return Map.of("name", name, "description", "只读查询", "inputSchema",
                Map.of("type", "object", "properties", Map.of("city", Map.of("type", "string"))));
    }

    private void model(HttpExchange exchange) throws IOException {
        try {
            assertEquals("Bearer model-test-key", exchange.getRequestHeaders().getFirst("Authorization"));
            JsonNode request = json.readTree(exchange.getRequestBody());
            modelRequests.add(request);
            boolean call = modelRequests.size() == 1 || endless;
            Map<String, Object> message = call ? Map.of("role", "assistant", "reasoning_content", "查天气",
                    "tool_calls", List.of(Map.of("id", "call_" + modelRequests.size(), "type", "function", "function",
                            Map.of("name", modelTool, "arguments", "{\"city\":\"北京\"}"))))
                    : Map.of("role", "assistant", "content", "北京攻略【高德查询】");
            send(exchange, "application/json", 200, json.writeValueAsString(Map.of("choices", List.of(Map.of("message", message)))));
        } finally { exchange.close(); }
    }

    private void send(HttpExchange exchange, String type, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @Test void discoveryCallAndModelContinuationUseRealMcpSse() {
        String result = planner().generate(base + "/ark/", "model-test-key", "test-model", "输出攻略", "北京一日游");
        assertEquals("北京攻略【高德查询】", result);
        assertEquals(List.of("maps_weather"), calledTools);
        assertEquals(List.of("initialize", "notifications/initialized", "tools/list", "tools/call"), methods);
        assertEquals("required", modelRequests.get(0).path("tool_choice").asText());
        JsonNode messages = modelRequests.get(1).path("messages");
        assertEquals("查天气", messages.get(2).path("reasoning_content").asText());
        assertEquals("call_1", messages.get(3).path("tool_call_id").asText());
        assertTrue(messages.get(3).path("content").asText().contains("北京晴"));
        assertFalse(modelRequests.get(0).path("tools").toString().contains("dangerous_write"));
        assertTrue(deleted);
    }

    @Test void jsonTransportSupportsPaginationAndRejectsUndiscoveredTools() {
        sse = false;
        paginated = true;
        try (var session = client().open()) {
            assertEquals(4, session.tools().size());
            assertThrows(IllegalArgumentException.class, () -> session.call("dangerous_write", json.createObjectNode()));
            assertThrows(IllegalArgumentException.class, () -> session.call("maps_weather", json.createArrayNode()));
            assertFalse(session.call("maps_weather", json.createObjectNode().put("city", "北京")).path("isError").asBoolean());
        }
        assertEquals(List.of("maps_weather"), calledTools);
    }

    @Test void unapprovedModelToolNeverReachesMcpServer() {
        modelTool = "dangerous_write";
        assertThrows(IllegalStateException.class,
                () -> planner().generate(base + "/ark", "model-test-key", "test", "攻略", "北京"));
        assertTrue(calledTools.isEmpty());
        assertTrue(modelRequests.get(1).path("messages").get(3).path("content").asText().contains("isError"));
    }

    @Test void toolFailureIsPassedToModelAsErrorInsteadOfSuccess() {
        toolError = true;
        assertThrows(IllegalStateException.class,
                () -> planner().generate(base + "/ark", "model-test-key", "test", "攻略", "北京"));
        assertTrue(modelRequests.get(1).path("messages").get(3).path("content").asText().contains("\"isError\":true"));
    }

    @Test void enabledWithoutKeyFailsClearlyBeforeNetwork() {
        var client = new AmapMcpClient(json, true, "", HttpUrl.get(base + "/mcp"));
        assertTrue(assertThrows(IllegalStateException.class, client::open).getMessage().contains("AMAP_MCP_KEY"));
        assertTrue(methods.isEmpty());
        assertFalse(new AmapMcpClient(json, false, "", HttpUrl.get(base + "/mcp")).isEnabled());
    }

    @Test void rpcAndHttpErrorsNeverExposeCredentials() {
        rpcFailure = "tools/list";
        var error = assertThrows(IllegalStateException.class,
                () -> planner().generate(base + "/ark", "model-test-key", "test", "攻略", "北京"));
        assertFalse(error.toString().contains("private-test-key"));
        assertTrue(deleted);
        failHttp = true;
        error = assertThrows(IllegalStateException.class, () -> client().open());
        assertFalse(error.toString().contains("private-test-key"));
    }

    @Test void mismatchedResponseIdIsRejected() {
        mismatchId = true;
        assertThrows(IllegalStateException.class, () -> client().open());
    }

    @Test void modelCannotLoopPastConfiguredRoundBudget() {
        endless = true;
        assertThrows(IllegalStateException.class,
                () -> planner().generate(base + "/ark", "model-test-key", "test", "攻略", "北京"));
        assertEquals(3, calledTools.size());
        assertEquals(4, modelRequests.size());
        assertEquals("none", modelRequests.get(3).path("tool_choice").asText());
        assertTrue(deleted);
    }
}
