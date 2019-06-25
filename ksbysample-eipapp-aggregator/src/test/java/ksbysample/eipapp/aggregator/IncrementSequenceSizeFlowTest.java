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
import org.springframework.integration.support.MessageBuilderFactory;
import org.springframework.integration.support.json.JacksonJsonUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
public class IncrementSequenceSizeFlowTest {

    @Autowired
    private MessageBuilderFactory messageBuilderFactory;

    @Autowired
    private IntegrationFlow aggregateFlow;

    @Autowired
    private QueueChannel outputChannel;

    @Test
    void sequenceSizeをインクリメントするメッセージを３件送信した時に最後のメッセージを受信したaggregagorがreleaseするかのテスト() {
        // メッセージで使用する correlationId を発番する
        UUID correlationId = IntegrationObjectSupport.generateId();

        // A1, A2, A3 のメッセージを送信する
        // sequenceSize を 3, 4, 5 と１つずつ増やす（3が正しい値）
        Arrays.asList(1, 2, 3).forEach(n -> {
            String payload = String.format("A%d", n);
            Message<String> msg = messageBuilderFactory
                    .fromMessage(MessageBuilder.withPayload(payload).build())
                    .pushSequenceDetails(correlationId, n, n + 2)
                    .build();
            aggregateFlow.getInputChannel().send(msg);
        });

        // aggregator が release しているか確認する
        Message<?> result = outputChannel.receive(5_000);
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
        public IntegrationFlow aggregateFlow() {
            return f -> f
                    .log(LoggingHandler.Level.WARN)
                    .aggregate(a -> a.messageStore(redisMessageStore()))
                    .log(LoggingHandler.Level.ERROR)
                    .channel(outputChannel());
        }

        @Bean
        public MessageChannel outputChannel() {
            return MessageChannels.queue().get();
        }

    }

}
