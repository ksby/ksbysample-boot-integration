package ksbysample.eipapp.functions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.Pollers;
import org.springframework.messaging.support.MessageBuilder;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
//@Configuration
public class MessageSourceAndLambdaFlow {

    private AtomicInteger count = new AtomicInteger(0);

    @Bean
    public MessageSource<Integer> countMessageSource() {
        return () -> MessageBuilder
                .withPayload(count.addAndGet(1))
                .build();
    }

    @Bean
    public IntegrationFlow countDisplayFlow() {
        return IntegrationFlows
                .from(countMessageSource()
                        , e -> e.poller(Pollers.fixedDelay(1000)))
                .<Integer, String>transform(v -> String.valueOf(v * 2))
                .handle((Function<String, String>) s -> s + "...")
                .handle((Consumer<String>) s -> log.warn(s))
                .get();
    }

}
