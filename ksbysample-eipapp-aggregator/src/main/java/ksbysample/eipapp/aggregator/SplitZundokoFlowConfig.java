package ksbysample.eipapp.aggregator;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.dsl.DelayerEndpointSpec;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.redis.store.RedisMessageStore;
import org.springframework.integration.support.json.JacksonJsonUtils;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Configuration
public class SplitZundokoFlowConfig {

    private static final String[] ZUNDOKO = new String[]{"ずん", "ずん", "ずん", "ずん", "どこ"};

    private static final long POLLER_DELAY_PERIOD = 5000L;
    private static final int DELAY_MAX_VALUE = 5000;
    private static final long MESSAGE_GROUP_TIMEOUT = 3000L;

    // Redis の設定はデフォルト値をそのまま使用するので、application.poperties には何も記載していない
    // https://docs.spring.io/spring-boot/docs/current/reference/html/common-application-properties.html#common-application-properties
    // の spring.redis.～　を参照。
    private final RedisConnectionFactory redisConnectionFactory;

    public SplitZundokoFlowConfig(RedisConnectionFactory redisConnectionFactory) {
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
    public MessageSource<String[]> zundokoMessageSource() {
        return () -> MessageBuilder.withPayload(ZUNDOKO).build();
    }

    @Bean
    public IntegrationFlow zundokoFlow() {
        return IntegrationFlows.from(zundokoMessageSource()
                , e -> e.poller(Pollers.fixedDelay(POLLER_DELAY_PERIOD)))
                // Message<String[]> --> Message<String> x 5 に分割する
                .split()
                // ここから下はマルチスレッドで処理する
                .channel(c -> c.executor(Executors.newFixedThreadPool(5)))
                // 5秒以内(ランダムで決める)の間 delay する
                // group timeout した時にどのメッセージが原因だったのかが分かるようにするために
                // delay header を追加して値をセットする
                .enrichHeaders(h -> h.headerFunction("delay",
                        m -> String.valueOf(ThreadLocalRandom.current().nextInt(DELAY_MAX_VALUE))))
                .delay("ZUNDOKO_DELAYER",
                        (DelayerEndpointSpec e) -> e.delayExpression("headers.delay"))
                // kiyoshiFlow にメッセージを送信する
                .channel(kiyoshiFlow().getInputChannel())
                .get();
    }

    @Bean
    public IntegrationFlow kiyoshiFlow() {
        return f -> f
                .aggregate(a -> a
                        .messageStore(redisMessageStore())
                        .releaseStrategy(g -> g.getMessages().size() == g.getSequenceSize())
                        .expireGroupsUponCompletion(true)
                        // .groupTimeout(...) で指定した時間内に aggregator で処理されなかったメッセージは
                        // .discardChannel(...) で指定した channel に送信される
                        // ※既に MESSAGE_GROUP に蓄積されていたメッセージも discardChannel に送信される
                        .groupTimeout(MESSAGE_GROUP_TIMEOUT)
                        .discardChannel(discardFlow().getInputChannel()))
                // {"ずん", "ずん", "ずん", "ずん", "どこ"} と揃っていたら "きよし！" を出力する
                .log()
                .<List<String>>handle((p, h) -> {
                    if (p.size() == 5 && p.equals(Arrays.asList(ZUNDOKO))) {
                        log.error("きよし！");
                    }
                    return null;
                });
    }

    /**
     * MESSAGE_GROUP_TIMEOUT で指定された時間（３秒）以内に aggregator で処理されなかった MESSAGE GROUP
     * のメッセージが送信されてくる Flow
     *
     * @return {@link IntegrationFlow} object
     */
    @Bean
    public IntegrationFlow discardFlow() {
        return f -> f
                .log(LoggingHandler.Level.WARN)
                .nullChannel();
    }

}
