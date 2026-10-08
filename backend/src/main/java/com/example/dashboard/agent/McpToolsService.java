package com.example.dashboard.agent;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.service.tool.ToolProvider;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Optional MCP (Model Context Protocol) tool servers for the Virtual Agent.
 *
 * Config: {@code agent.mcp.servers} is a comma-separated list of entries
 *   {@code name|http|https://host/mcp-endpoint}   — streamable HTTP transport
 *   {@code name|stdio|command arg1 arg2}          — spawn a local MCP process
 *
 * e.g. agent.mcp.servers=edgar|stdio|uvx --from edgartools edgar-mcp,web|http|https://example.com/mcp
 *
 * Clients connect lazily on first agent use; a server that fails to start is
 * logged and skipped — it can never break the chat endpoint.
 */
@Service
public class McpToolsService {

    private static final Logger log = LoggerFactory.getLogger(McpToolsService.class);

    record ServerSpec(String name, String transport, String spec) {}

    private final List<ServerSpec> specs;
    private final List<McpClient> clients = new CopyOnWriteArrayList<>();
    private volatile ToolProvider provider;
    private volatile boolean initialized;

    public McpToolsService(@Value("${agent.mcp.servers:}") String servers) {
        this.specs = parse(servers);
    }

    static List<ServerSpec> parse(String servers) {
        if (!StringUtils.hasText(servers)) return List.of();
        List<ServerSpec> out = new ArrayList<>();
        for (String entry : servers.split(",")) {
            String[] parts = entry.trim().split("\\|", 3);
            if (parts.length != 3 || !StringUtils.hasText(parts[0])
                    || !(parts[1].equals("http") || parts[1].equals("stdio"))) {
                log.warn("Ignoring malformed MCP server entry: {}", entry.trim());
                continue;
            }
            out.add(new ServerSpec(parts[0].trim(), parts[1], parts[2].trim()));
        }
        return List.copyOf(out);
    }

    public boolean isConfigured() {
        return !specs.isEmpty();
    }

    /** Null when no MCP servers are configured or all failed to start. */
    public ToolProvider toolProvider() {
        if (!isConfigured()) return null;
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    connectAll();
                    initialized = true;
                }
            }
        }
        return provider;
    }

    private void connectAll() {
        for (ServerSpec s : specs) {
            try {
                McpTransport transport = s.transport().equals("stdio")
                        ? new StdioMcpTransport.Builder()
                            .command(Arrays.asList(s.spec().split("\\s+")))
                            .logEvents(true)
                            .build()
                        : new StreamableHttpMcpTransport.Builder()
                            .url(s.spec())
                            .timeout(Duration.ofSeconds(30))
                            .build();
                McpClient client = new DefaultMcpClient.Builder()
                        .key("stocks-explorer:" + s.name())
                        .transport(transport)
                        .toolExecutionTimeout(Duration.ofSeconds(45))
                        .reconnectInterval(Duration.ofSeconds(30))
                        .autoHealthCheck(true)
                        .build();
                clients.add(client);
                log.info("MCP server '{}' connected — tools: {}",
                        s.name(),
                        client.listTools().stream()
                                .map(dev.langchain4j.agent.tool.ToolSpecification::name)
                                .toList());
            } catch (Exception e) {
                log.warn("MCP server '{}' failed to start — skipping: {}", s.name(), e.getMessage());
            }
        }
        if (!clients.isEmpty()) {
            provider = McpToolProvider.builder()
                    .mcpClients(List.copyOf(clients))
                    .failIfOneServerFails(false)
                    .build();
        }
    }

    @PreDestroy
    void shutdown() {
        for (McpClient c : clients) {
            try {
                c.close();
            } catch (Exception e) {
                log.debug("MCP client close failed: {}", e.getMessage());
            }
        }
    }
}
