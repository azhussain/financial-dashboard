package com.example.dashboard.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class McpToolsServiceTest {

    @Test
    void disabledWhenUnconfigured() {
        McpToolsService service = new McpToolsService("");

        assertThat(service.isConfigured()).isFalse();
        assertThat(service.toolProvider()).isNull();
    }

    @Test
    void parsesHttpAndStdioEntries() {
        var specs = McpToolsService.parse(
                "web|http|https://mcp.example.com/mcp, edgar|stdio|uvx --from edgartools edgar-mcp");

        assertThat(specs).hasSize(2);
        assertThat(specs.get(0).name()).isEqualTo("web");
        assertThat(specs.get(0).transport()).isEqualTo("http");
        assertThat(specs.get(0).spec()).isEqualTo("https://mcp.example.com/mcp");
        assertThat(specs.get(1).name()).isEqualTo("edgar");
        assertThat(specs.get(1).transport()).isEqualTo("stdio");
        assertThat(specs.get(1).spec()).isEqualTo("uvx --from edgartools edgar-mcp");
    }

    @Test
    void skipsMalformedEntries() {
        var specs = McpToolsService.parse(
                "no-transport-separator, bad|weird|x, |http|https://ok.example/mcp");

        assertThat(specs).hasSize(0);
        var ok = McpToolsService.parse("bad|ftp|x, good|http|https://ok.example/mcp");
        assertThat(ok).hasSize(1);
        assertThat(ok.get(0).name()).isEqualTo("good");
    }

    @Test
    void unreachableServerNeverBreaksTheAgent() {
        // Connects lazily; a bad stdio command must not throw — whether the
        // spawn fails synchronously or the handshake fails asynchronously.
        McpToolsService service = new McpToolsService(
                "broken|stdio|definitely-not-a-real-command-xyz");

        assertThat(service.isConfigured()).isTrue();
        assertThatCode(service::toolProvider).doesNotThrowAnyException();
    }
}
