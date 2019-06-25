package ksbysample.eipapp.aggregator;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.context.IntegrationObjectSupport;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.redis.store.RedisMessageStore;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.integration.support.MessageBuilderFactory;
import org.springframework.integration.support.json.JacksonJsonUtils;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
public class MultiAggregatorTest {

    @Autowired
    private MessageBuilderFactory messageBuilderFactory;

    @Autowired
    private IntegrationFlow a1Flow;

    @Autowired
    private IntegrationFlow a2Flow;

    @Autowired
    private QueueChannel outputChannel;

    @Test
    void sampleTest() {
        final int SEQUENCE_SIZE = 3;

        // メッセージで使用する correlationId を発番する
        UUID correlationId = IntegrationObjectSupport.generateId();

        int sequenceNumber = 1;
        Message<String> msg = messageBuilderFactory
                .fromMessage(MessageBuilder.withPayload("A1").build())
                .pushSequenceDetails(correlationId, sequenceNumber++, SEQUENCE_SIZE)
                .build();
        a1Flow.getInputChannel().send(msg);

        Message<String> msg2 = messageBuilderFactory
                .fromMessage(MessageBuilder.withPayload("A2").build())
                .pushSequenceDetails(correlationId, sequenceNumber++, SEQUENCE_SIZE)
                .build();
        a2Flow.getInputChannel().send(msg2);

        SleepUtils.sleep(1_000);
        Message<String> msg3 = messageBuilderFactory
                .fromMessage(MessageBuilder.withPayload("A3").build())
                .pushSequenceDetails(correlationId, sequenceNumber++, SEQUENCE_SIZE)
                .build();
        a1Flow.getInputChannel().send(msg3);

        Message<?> result = outputChannel.receive(15_000);
        assertThat(result).isNotNull();
        assertThat(result.getPayload()).isEqualTo(Arrays.asList("A1", "A2", "A3"));
        assertThat(result.getHeaders().get(IntegrationMessageHeaderAccessor.SEQUENCE_SIZE))
                .isEqualTo(3);
    }

    @TestConfiguration
    static class FlowConfig {

        @Autowired
        private RedisConnectionFactory redisConnectionFactory;

        /**
         * Redis を MessageStore として使用するための設定。
         * Redis に格納する message は JSON フォーマットにする。
         * https://docs.spring.io/spring-integration/docs/current/reference/html/#redis-message-store
         * 参照。
         *
         * @return {@link RedisMessageStore} object
         */
        @Bean
        public RedisMessageStore redisMessageStore() {
            RedisMessageStore store = new RedisMessageStore(redisConnectionFactory);
            ObjectMapper mapper = JacksonJsonUtils.messagingAwareMapper();
            RedisSerializer<Object> serializer = new GenericJackson2JsonRedisSerializer(mapper);
            store.setValueSerializer(serializer);
            return store;
        }

        @Bean
        public IntegrationFlow a1Flow() {
            return f -> f
                    .log(LoggingHandler.Level.WARN)
                    // 以降の処理を別スレッドにすることで、send メソッド呼び出し時に待機しないようにする
                    .channel(c -> c.executor(Executors.newFixedThreadPool(1)))
                    .aggregate(a -> a.messageStore(redisMessageStore())
                            // a1Flow, a2Flow で同じ LockRegistry を使用するよう設定する
                            .lockRegistry(aggretatorLockRegistry())
                            .releaseStrategy(g -> {
                                log.error("PASS_A1");
                                return g.getMessages().size() == g.getSequenceSize();
                            }))
                    .log(LoggingHandler.Level.ERROR)
                    .channel(outputChannel());
        }

        @Bean
        public IntegrationFlow a2Flow() {
            return f -> f
                    .log()
                    .channel(c -> c.executor(Executors.newFixedThreadPool(1)))
                    .aggregate(a -> a.messageStore(redisMessageStore())
                            .lockRegistry(aggretatorLockRegistry())
                            .releaseStrategy(g -> {
                                SleepUtils.sleep(10_000);
                                log.error("PASS_A2");
                                return g.getMessages().size() == g.getSequenceSize();
                            }))
                    .log();
        }

        @Bean
        public LockRegistry aggretatorLockRegistry() {
            return new RedisLockRegistry(redisConnectionFactory, "AGGREGATOR_LOCK");
        }

        @Bean
        public MessageChannel outputChannel() {
            return MessageChannels.queue().get();
        }

    }

    static class SleepUtils {

        public static void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

    }

}
