package ksbysample.eipapp.pollertest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.core.Pollers;
import org.springframework.integration.dsl.support.GenericHandler;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
public class FlowConfig {

    @Bean
    public MessageSource<List<Person>> personListMessageSource() {
        return () -> {
            List<Person> personList = new ArrayList<>();
            personList.add(new Person("tanaka", 35));
            personList.add(new Person("suzuki", 28));
            personList.add(new Person("kimura", 41));
            return MessageBuilder.withPayload(personList).build();
        };
    }

    @Bean
    public MessageChannel nextChannel() {
        return new QueueChannel(100);
    }

    @Bean
    public IntegrationFlow getFlow() {
        // 指定された時間毎に personListMessageSource からデータを取得して
        // ログを出力した後、nextChannel にデータを渡す
        return IntegrationFlows.from(personListMessageSource()
                , c -> c.poller(Pollers.fixedRate(1000)))
                .handle((p, h) -> {
                    log.info("★★★ getFlow");
                    return p;
                })
                .channel(nextChannel())
                .get();
    }

    @Bean
    public IntegrationFlow nextFlow() {
        // 指定された時間毎に nextChannel からデータを取得してログを出力する
        return IntegrationFlows.from(nextChannel())
                .handle((GenericHandler<Object>) (p, h) -> {
                    log.info("■■■ nextFlow");
                    return null;
                }, c -> c.poller(Pollers.fixedRate(5000, 5000)))
                .get();
    }

}
