package ksbysample.eipapp.redisqueue;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.channel.MessageChannels;
import org.springframework.integration.dsl.core.Pollers;
import org.springframework.integration.endpoint.MessageProducerSupport;
import org.springframework.integration.redis.inbound.RedisQueueMessageDrivenEndpoint;
import org.springframework.integration.redis.store.RedisChannelMessageStore;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Configuration
public class FlowConfig {

    private static final String REDIS_LISTS_NAME = "REDIS_QUEUE";

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
        return MessageChannels.queue("redisQueue", redisMessageStore(), REDIS_LISTS_NAME).get();
    }

    /**
     * {@link FlowConfig#REDIS_LISTS_NAME} の Redis Lists を 1秒毎にチェックして、データがあれば Message を送信する
     * {@link org.springframework.integration.core.MessageProducer}
     * データチェックは 5スレッドで行う
     *
     * @return new {@link MessageProducerSupport}
     */
    @Bean
    public MessageProducerSupport redisMessageProducer() {
        RedisQueueMessageDrivenEndpoint messageProducer
                = new RedisQueueMessageDrivenEndpoint(REDIS_LISTS_NAME, this.redisConnectionFactory);
        messageProducer.setRecoveryInterval(1000);
        // setExpectMessage(true) を入れないと Redis Lists から取得したデータ ( headers + SampleData オブジェクトの payload )
        // が全て payload にセットされる
        messageProducer.setExpectMessage(true);
        return messageProducer;
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
                .from(redisMessageProducer())
                .log()
                .channel(c -> c.executor(Executors.newFixedThreadPool(5)))
                .<SampleData>handle((p, h) -> {
                    log.info("★★★ " + p.getMessage());
                    return null;
                })
                .get();
    }

}
