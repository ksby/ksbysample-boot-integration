package ksbysample.eipapp.redisqueue;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.channel.MessageChannels;
import org.springframework.integration.dsl.core.Pollers;
import org.springframework.integration.redis.store.RedisChannelMessageStore;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class FlowConfig {

    private final RedisConnectionFactory redisConnectionFactory;

    public FlowConfig(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    /**
     * Message の palyload にセットする SampleData クラス
     */
    @Data
    @AllArgsConstructor
    public static class SampleData implements Serializable {

        private static final long serialVersionUID = 6103271054731633658L;

        private Integer id;

        private LocalDateTime createDateTime;

        private String message;

    }

    /**
     * SampleData オブジェクトを送信する MessageSource
     */
    public static class SampleDataMessageSource implements MessageSource<SampleData> {

        private AtomicInteger idGenerator = new AtomicInteger(0);

        @Override
        public Message<SampleData> receive() {
            Integer id = idGenerator.getAndIncrement();
            LocalDateTime createDateTime = LocalDateTime.now();
            String message = String.format("idGenerator = %d は %s に生成されました"
                    , id, createDateTime.format(DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm:ss")));

            SampleData sampleData = new SampleData(id, createDateTime, message);
            return MessageBuilder.withPayload(sampleData).build();
        }

    }

    @Bean
    public MessageSource<SampleData> sampleDataMessageSource() {
        return new SampleDataMessageSource();
    }

    @Bean
    public RedisChannelMessageStore redisMessageStore() {
        return new RedisChannelMessageStore(this.redisConnectionFactory);
    }

    @Bean
    public QueueChannel redisQueue() {
        return MessageChannels.queue("redisQueue", redisMessageStore(), "REDIS_QUEUE").get();
    }

    /**
     * メインフロー
     * 10秒毎に {@link SampleDataMessageSource} から SampleData オブジェクトが payload にセットされた Message を受信し、
     * {@link FlowConfig#redisQueue()} へ送信する
     *
     * @return {@link IntegrationFlow} オブジェクト
     */
    @Bean
    public IntegrationFlow mainFlow() {
        return IntegrationFlows
                .from(sampleDataMessageSource()
                        , e -> e.poller(Pollers.fixedDelay(200)))
                .log()
                .channel(redisQueue())
                .get();
    }

    /**
     * {@link SampleData} オブジェクトが payload にセットされた Message を受信し、
     * {@link SampleData#getMessage()} の出力結果を標準出力に出力する
     *
     * @return {@link IntegrationFlow} オブジェクト
     */
    @Bean
    public IntegrationFlow printFlow() {
        return IntegrationFlows
                .from(redisQueue())
                .bridge(e -> e.poller(Pollers.fixedDelay(1000)))
                .<SampleData>handle((p, h) -> {
                    System.out.println("★★★ " + p.getMessage());
                    return null;
                })
                .get();
    }

}
