package ksbysample.eipapp.kafka;

import ksbysample.eipapp.kafka.avro.Counter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.handler.advice.ErrorMessageSendingRecoverer;
import org.springframework.integration.handler.advice.RequestHandlerRetryAdvice;
import org.springframework.integration.kafka.dsl.Kafka;
import org.springframework.integration.kafka.dsl.KafkaMessageDrivenChannelAdapterSpec;
import org.springframework.integration.kafka.dsl.KafkaProducerMessageHandlerSpec;
import org.springframework.integration.kafka.inbound.KafkaMessageDrivenChannelAdapter;
import org.springframework.integration.kafka.support.RawRecordHeaderErrorMessageStrategy;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.DefaultKafkaHeaderMapper;
import org.springframework.messaging.MessageChannel;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static java.util.Collections.singletonMap;

@Slf4j
@Configuration
public class KafkaDslSampleConfig {

    private static final String TOPIC_NAME = "Topic1";

    // kafkaProducerFactory bean, kafkaConsumerFactory bean は
    // org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
    // で生成されている
    //
    // 設定は application.properties の spring.kafka.producer.～ で行う
    private final ProducerFactory<Integer, Counter> kafkaProducerFactory;
    // 設定は application.properties の spring.kafka.consumer.～ で行う
    private final ConsumerFactory<Integer, Counter> kafkaConsumerFactory;

    private final MessageChannel errorChannel;

    private AtomicInteger count = new AtomicInteger(0);

    private static final String[] FULL_NAMES = new String[]{"田中　太郎", "鈴木　花子", "木村　さくら"};

    public KafkaDslSampleConfig(ProducerFactory<Integer, Counter> kafkaProducerFactory
            , ConsumerFactory<Integer, Counter> kafkaConsumerFactory
            , MessageChannel errorChannel) {
        this.kafkaProducerFactory = kafkaProducerFactory;
        this.kafkaConsumerFactory = kafkaConsumerFactory;
        this.errorChannel = errorChannel;
    }

    @Bean
    public Supplier<Integer> countSupplier() {
        return () -> this.count.addAndGet(1);
    }

    @Bean
    public IntegrationFlow topic1ProducerFlow() {
        return IntegrationFlows
                .from(countSupplier()
                        , e -> e.poller(Pollers.fixedDelay(1000)))
                // メッセージの kafka_topic ヘッダに topic 名をセットすると
                // kafkaMessageHandler メソッドの第２引数に指定した topic ではなく
                // kafka_topic ヘッダの topic に送信される
                // .enrichHeaders(h -> h.header(KafkaHeaders.TOPIC, TOPIC_NAME))
                .<Integer, Counter>transform(p -> Counter.newBuilder()
                        .setCount(p)
                        .setFullName(FULL_NAMES[ThreadLocalRandom.current().nextInt(3)])
                        .build())
                .log(LoggingHandler.Level.WARN)
                .handle(kafkaMessageHandler(kafkaProducerFactory, TOPIC_NAME)
                        , e -> e.advice(retryAdvice()))
                .get();
    }

    @Bean
    public DefaultKafkaHeaderMapper mapper() {
        return new DefaultKafkaHeaderMapper();
    }

    private KafkaProducerMessageHandlerSpec<Integer, Counter, ?> kafkaMessageHandler(
            ProducerFactory<Integer, Counter> producerFactory, String topic) {
        return Kafka
                .outboundChannelAdapter(producerFactory)
                .sync(true)
                // kafka_messageKey ヘッダにセットされている値を key にする場合には以下のように書く
                // .messageKey(m -> m
                //         .getHeaders()
                //         .get(KafkaHeaders.MESSAGE_KEY))
                .headerMapper(mapper())
                // メッセージの header に "kafka_topic" があれば、そこにセットされている topic へ、
                // なければ第２引数 topic で渡された topic へ送信する
                .topicExpression("headers[kafka_topic] ?: '" + topic + "'");
    }

    @Bean
    public IntegrationFlow topic1Consumer1Flow() {
        return IntegrationFlows
                .from(createKafkaMessageDrivenChannelAdapter())
                .<Counter>handle((p, h) -> {
                    log.error(String.format("★★★ partition = %s, count = %s, fullName = %s"
                            , h.get("kafka_receivedPartitionId"), p.getCount(), p.getFullName()));
                    return null;
                })
                .get();
    }

    @Bean
    public IntegrationFlow topic1Consumer2Flow() {
        return IntegrationFlows
                .from(createKafkaMessageDrivenChannelAdapter())
                .<Counter>handle((p, h) -> {
                    log.error(String.format("●●● partition = %s, value = %s, fullName = %s"
                            , h.get("kafka_receivedPartitionId"), p.getCount(), p.getFullName()));
                    return null;
                })
                .get();
    }

    @Bean
    public IntegrationFlow topic1Consumer3Flow() {
        return IntegrationFlows
                .from(createKafkaMessageDrivenChannelAdapter())
                .<Counter>handle((p, h) -> {
                    log.error(String.format("▲▲▲ partition = %s, value = %s, fullName = %s"
                            , h.get("kafka_receivedPartitionId"), p.getCount(), p.getFullName()));
                    return null;
                })
                .get();
    }

    private KafkaMessageDrivenChannelAdapterSpec.KafkaMessageDrivenChannelAdapterListenerContainerSpec<Integer, Counter>
    createKafkaMessageDrivenChannelAdapter() {
        return Kafka.messageDrivenChannelAdapter(kafkaConsumerFactory
                , KafkaMessageDrivenChannelAdapter.ListenerMode.record, TOPIC_NAME)
                .configureListenerContainer(c ->
                        c.ackMode(ContainerProperties.AckMode.RECORD)
                                .idleEventInterval(100L))
                .recoveryCallback(new ErrorMessageSendingRecoverer(errorChannel
                        , new RawRecordHeaderErrorMessageStrategy()))
                .retryTemplate(retryTemplate())
                .filterInRetry(true);
    }

    /**
     * リトライ回数は最大３回、リトライ前に 5秒待機する RetryTemplate
     */
    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(3, singletonMap(Exception.class, true)));
        FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
        fixedBackOffPolicy.setBackOffPeriod(5000);
        retryTemplate.setBackOffPolicy(fixedBackOffPolicy);
        return retryTemplate;
    }

    @Bean
    public RequestHandlerRetryAdvice retryAdvice() {
        RequestHandlerRetryAdvice retryAdvice = new RequestHandlerRetryAdvice();
        retryAdvice.setRetryTemplate(retryTemplate());
        return retryAdvice;
    }

}
