package ksbysample.eipapp.functions;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.NullChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Configuration
public class SupplierAndFunction2Flow {

    @Autowired
    private NullChannel nullChannel;

    private AtomicInteger count = new AtomicInteger(0);

    // Supplier<Message<?>> という記述にして Message オブジェクトを返すようにすることも出来る
    // この場合 MessageSource の時のように header を追加することが可能。
    @Bean
    public Supplier<Message<Integer>> countSupplier() {
        return () -> MessageBuilder
                .withPayload(count.addAndGet(1))
                .setHeader("random", ThreadLocalRandom.current().nextInt(100))
                .build();
    }

    @Bean
    public IntegrationFlow countDisplayFlow() {
        return IntegrationFlows
                .from(countSupplier()
                        , e -> e.poller(Pollers.fixedDelay(1000)))
                .log(LoggingHandler.Level.WARN)
                .channel(nullChannel)
                .get();
    }

}
