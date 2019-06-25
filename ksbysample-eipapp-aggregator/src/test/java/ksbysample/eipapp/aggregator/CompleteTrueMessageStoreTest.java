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
import org.springframework.integration.annotation.ReleaseStrategy;
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

import java.util.List;
import java.util.UUID;

@Slf4j
@SpringBootTest
public class CompleteTrueMessageStoreTest {

    @Autowired
    private MessageBuilderFactory messageBuilderFactory;

    @Autowired
    private IntegrationFlow aggregateFlow;

    @Autowired
    private QueueChannel outputChannel;

    @Autowired
    private QueueChannel discardChannel;

    @Test
    void sampleTest() {
        // メッセージで使用する correlationId を発番する
        UUID correlationId = IntegrationObjectSupport.generateId();

        // A1 メッセージを送信する
        // sequenceSize = 1 なので、このメッセージを受信すると aggregagtor は release する
        // MESSAGE_STORE も complete = true になる
        Message<String> msg = messageBuilderFactory
                .fromMessage(MessageBuilder.withPayload("A1").build())
                .pushSequenceDetails(correlationId, 1, 1)
                .build();
        aggregateFlow.getInputChannel().send(msg);

        // A1 と correlationId が同じ A2 メッセージを送信する
        Message<String> msg2 = messageBuilderFactory
                .fromMessage(MessageBuilder.withPayload("A2").build())
                .pushSequenceDetails(correlationId, 2, 1)
                .build();
        aggregateFlow.getInputChannel().send(msg2);
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
                    .aggregate(a -> a.messageStore(redisMessageStore())
                            .processor(new sampleAggregator())
                            .discardChannel(discardFlow().getInputChannel()))
                    .channel(outputChannel());
        }

        @Bean
        public IntegrationFlow discardFlow() {
            return f -> f
                    .log(LoggingHandler.Level.ERROR)
                    .channel(discardChannel());
        }

        @Bean
        public MessageChannel outputChannel() {
            return MessageChannels.queue().get();
        }

        @Bean
        public MessageChannel discardChannel() {
            return MessageChannels.queue().get();
        }

    }

    static class sampleAggregator {

        @ReleaseStrategy
        public boolean canRelease(List<Message<?>> messages) {
            return messages.size() == 1;
        }

    }

}
