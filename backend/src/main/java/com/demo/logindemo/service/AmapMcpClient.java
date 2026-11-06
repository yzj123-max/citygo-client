package com.demo.logindemo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Request-scoped Streamable HTTP sessions; credentials and upstream bodies never enter errors. */
@Service
public class AmapMcpClient {
    private static final Set<String> VERSIONS = Set.of("2025-03-26", "2025-06-18", "2025-11-25");
    static final Set<String> ALLOWED_TOOLS = Set.of(
            "maps_text_search", "maps_around_search", "maps_search_detail", "maps_geo", "maps_regeocode",
            "maps_weather", "maps_direction_walking", "maps_direction_driving",
            "maps_direction_bicycling", "maps_direction_transit_integrated", "maps_distance");
    private final ObjectMapper json;
    private final boolean enabled;
    private final String key;
    private final HttpUrl endpoint;
    private final OkHttpClient http;

    @Autowired
    public AmapMcpClient(ObjectMapper json,
                         @Value("${amap.mcp.enabled:${AMAP_MCP_ENABLED:false}}") boolean enabled,
                         @Value("${amap.mcp.api-key:${AMAP_MCP_KEY:}}") String key) {
        this(json, enabled, key, HttpUrl.get("https://mcp.amap.com/mcp"));
    }

    AmapMcpClient(ObjectMapper json, boolean enabled, String key, HttpUrl endpoint) {
        this.json = json;
        this.enabled = enabled;
        this.key = key;
        this.endpoint = endpoint;
        this.http = new OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build();
    }

    public boolean isEnabled() { return enabled; }

    public Session open() {
        if (key == null || key.isBlank()) throw new IllegalStateException("高德 MCP 已启用，但未配置 AMAP_MCP_KEY");
        Session session = new Session();
        try {
            JsonNode result = session.rpc("initialize", Map.of("protocolVersion", "2025-03-26",
                    "capabilities", Map.of(), "clientInfo", Map.of("name", "citygo-travel", "version", "1.0")));
            String version = result.path("protocolVersion").asText();
            if (!VERSIONS.contains(version)) throw new IllegalStateException("高德 MCP 协议版本不兼容");
            session.version = version;
            session.post(Map.of("jsonrpc", "2.0", "method", "notifications/initialized"), null);
            return session;
        } catch (RuntimeException e) {
            session.close();
            throw e;
        }
    }

    public final class Session implements AutoCloseable {
        private String sessionId;
        private String version;
        private int nextId;
        private final Set<String> discovered = new HashSet<>();

        public List<Map<String, Object>> tools() {
            List<Map<String, Object>> functions = new ArrayList<>();
            String cursor = "";
            for (int page = 0; page < 5; page++) {
                JsonNode result = rpc("tools/list", cursor.isEmpty() ? Map.of() : Map.of("cursor", cursor));
                for (JsonNode tool : result.path("tools")) {
                    String name = tool.path("name").asText();
                    if (!ALLOWED_TOOLS.contains(name) || !tool.path("inputSchema").isObject()
                            || !discovered.add(name)) continue;
                    functions.add(Map.of("type", "function", "function", Map.of("name", name,
                            "description", tool.path("description").asText(), "parameters", tool.get("inputSchema"))));
                }
                cursor = result.path("nextCursor").asText("");
                if (cursor.isEmpty()) return functions;
            }
            throw new IllegalStateException("高德 MCP 工具列表分页超限");
        }

        public JsonNode call(String name, JsonNode arguments) {
            if (!discovered.contains(name) || !ALLOWED_TOOLS.contains(name)) {
                throw new IllegalArgumentException("不允许调用该高德工具");
            }
            if (!arguments.isObject()) throw new IllegalArgumentException("高德工具参数必须为对象");
            return rpc("tools/call", Map.of("name", name, "arguments", arguments));
        }

        private JsonNode rpc(String method, Map<String, ?> params) {
            int id = ++nextId;
            JsonNode response = post(Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", params), id);
            if (response.has("error")) throw new IllegalStateException("高德 MCP 请求失败（" + method + "）");
            if (!response.has("result")) throw new IllegalStateException("高德 MCP 返回格式错误");
            return response.get("result");
        }

        private Request.Builder request() {
            Request.Builder builder = new Request.Builder().url(endpoint.newBuilder().addQueryParameter("key", key).build())
                    .header("Accept", "application/json, text/event-stream");
            if (sessionId != null) builder.header("Mcp-Session-Id", sessionId);
            if (version != null) builder.header("MCP-Protocol-Version", version);
            return builder;
        }

        private JsonNode post(Map<String, ?> message, Integer id) {
            try {
                Request req = request().post(RequestBody.create(json.writeValueAsBytes(message),
                        MediaType.get("application/json; charset=utf-8"))).build();
                try (Response response = http.newCall(req).execute()) {
                    if (!response.isSuccessful()) throw new IllegalStateException("高德 MCP HTTP " + response.code());
                    if (response.header("Mcp-Session-Id") != null) sessionId = response.header("Mcp-Session-Id");
                    if (id == null) return json.nullNode();
                    if (response.body() == null) throw new IOException();
                    boolean sse = response.header("Content-Type", "").startsWith("text/event-stream");
                    if (!sse) {
                        var source = response.body().source();
                        source.request(1_048_577);
                        if (source.buffer().size() > 1_048_576) throw new IOException();
                        String body = source.readUtf8();
                        JsonNode node = json.readTree(body);
                        if (matches(node, id)) return node;
                        throw new IOException();
                    }
                    BufferedReader reader = new BufferedReader(response.body().charStream());
                    StringBuilder data = new StringBuilder();
                    int size = 0;
                    for (String line; (line = reader.readLine()) != null;) {
                        size += line.length();
                        if (size > 1_048_576) throw new IOException();
                        if (line.isEmpty()) {
                            if (!data.isEmpty()) {
                                JsonNode node = json.readTree(data.toString());
                                if (matches(node, id)) return node;
                                data.setLength(0);
                            }
                        } else if (line.startsWith("data:")) {
                            if (!data.isEmpty()) data.append('\n');
                            data.append(line.substring(5).stripLeading());
                        }
                    }
                    throw new IOException();
                }
            } catch (IOException e) {
                // IOException messages may contain the URL with its key. Never attach the cause.
                throw new IllegalStateException("高德 MCP 网络超时或响应无效，请检查网络和 Key");
            }
        }

        private boolean matches(JsonNode node, int id) {
            return node != null && "2.0".equals(node.path("jsonrpc").asText())
                    && node.path("id").isIntegralNumber() && node.path("id").asInt() == id;
        }

        @Override public void close() {
            if (sessionId == null) return;
            try (Response ignored = http.newCall(request().delete().build()).execute()) {
                // Session deletion is best effort; some stateless servers do not support it.
            } catch (IOException | RuntimeException ignored) { }
        }
    }
}
