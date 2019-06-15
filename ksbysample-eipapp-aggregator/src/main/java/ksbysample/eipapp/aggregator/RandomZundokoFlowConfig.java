package ksbysample.eipapp.aggregator;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.redis.store.RedisMessageStore;
import org.springframework.integration.support.json.JacksonJsonUtils;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

@Slf4j
//@Configuration
public class RandomZundokoFlowConfig {

    private static final Random r = new Random();
    private static final String[] ZUNDOKO = new String[]{"ずん", "ずん", "ずん", "ずん", "どこ"};

    private static final long POLLER_DELAY_PERIOD = 15000L;

    // Redis の設定はデフォルト値をそのまま使用するので、application.poperties には何も記載していない
    // https://docs.spring.io/spring-boot/docs/current/reference/html/common-application-properties.html#common-application-properties
    // の spring.redis.～　を参照。
    private final RedisConnectionFactory redisConnectionFactory;

    public RandomZundokoFlowConfig(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

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
    public MessageSource<String> zundokoMessageSource() {
        return () -> MessageBuilder.withPayload(ZUNDOKO[r.nextInt(5)]).build();
    }

    @Bean
    public IntegrationFlow zundokoFlow() {
        return IntegrationFlows.from(zundokoMessageSource()
                , e -> e.poller(Pollers.fixedDelay(POLLER_DELAY_PERIOD)))
                // kiyoshiFlow にメッセージを送信する
                .channel(kiyoshiFlow().getInputChannel())
                .get();
    }

    @Bean
    public IntegrationFlow kiyoshiFlow() {
        return f -> f
                // メッセージが５つ溜まったら集約して次の処理に流す
                // * 流れてくる message の header に correlationId がないので
                //   correlationExpression("1") を記述して固定で "1" という扱いにする。
                //   これで全てのメッセージが１つの MESSAGE_QUEUE に蓄積される。
                // * expireGroupsUponCompletion(true) を記述すると５つ集約したメッセージを
                //   次の処理に流した後に再びメッセージが蓄積されるようになる。
                .aggregate(a -> a.correlationExpression("1")
                        .messageStore(redisMessageStore())
                        .releaseStrategy(g -> g.size() == 5)
                        .expireGroupsUponCompletion(true))
                // {"ずん", "ずん", "ずん", "ずん", "どこ"} と揃っていたら "きよし！" を出力する
                .log()
                .<List<String>>handle((p, h) -> {
                    if (p.size() == 5 && p.equals(Arrays.asList(ZUNDOKO))) {
                        log.error("きよし！");
                    }
                    return null;
                });
    }

}
