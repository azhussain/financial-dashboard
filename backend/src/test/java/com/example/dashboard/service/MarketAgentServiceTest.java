package com.example.dashboard.service;

import com.example.dashboard.agent.StockTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class MarketAgentServiceTest {

    @Mock
    private StockTools stockTools;

    @Test
    void disabledWhenApiKeyMissing() {
        MarketAgentService service = new MarketAgentService(stockTools, "", "gpt-4o-mini");

        assertThat(service.isEnabled()).isFalse();
        assertThatThrownBy(() -> service.chat("hello", "s1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void detectsChartIntentOnlyWhenAsked() {
        assertThat(MarketAgentService.wantsCharts("show me a chart of NVDA")).isTrue();
        assertThat(MarketAgentService.wantsCharts("plot AAPL over the last year")).isTrue();
        assertThat(MarketAgentService.wantsCharts("compare the graphs for MSFT and AMD")).isTrue();
        assertThat(MarketAgentService.wantsCharts("I have $200 where do I invest")).isFalse();
        assertThat(MarketAgentService.wantsCharts("how did NVDA do last week?")).isFalse();
        assertThat(MarketAgentService.wantsCharts("what are the top stocks?")).isFalse();
    }
}
