package ksbysample.eipapp.mscontroll;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Configuration
public class FlowConfig {

    private boolean messageSourceControlFlg = true;

    @Bean
    public MessageSource<String> helloMessageSource() {
        return () -> messageSourceControlFlg ? MessageBuilder.withPayload("hello").build() : null;
    }

    @Bean
    public IntegrationFlow helloFlow() {
        return IntegrationFlows.from(helloMessageSource()
                , e -> e.poller(Pollers.fixedDelay(1000)))
                .handle((p, h) -> {
                    log.info("★★★ " + p);
                    return null;
                })
                .get();
    }

    @Scheduled(initialDelay = 5000, fixedDelay = 5000)
    public void messageSourceControlFlgChanger() {
        messageSourceControlFlg = !messageSourceControlFlg;
        log.warn(String.valueOf(messageSourceControlFlg));
    }

}
