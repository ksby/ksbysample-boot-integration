package ksbysample.eipapp.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.handler.advice.ErrorMessageSendingRecoverer;
import org.springframework.integration.kafka.dsl.Kafka;
import org.springframework.integration.kafka.dsl.KafkaProducerMessageHandlerSpec;
import org.springframework.integration.kafka.inbound.KafkaMessageDrivenChannelAdapter;
import org.springframework.integration.kafka.support.RawRecordHeaderErrorMessageStrategy;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.DefaultKafkaHeaderMapper;
import org.springframework.messaging.MessageChannel;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Slf4j
@Configuration
public class KafkaDslSampleConfig {

    private static final String TOPIC_NAME = "Topic1";

    // kafkaProducerFactory bean, kafkaConsumerFactory bean は
    // org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
    // で生成されている
    //
    // 設定は application.properties の spring.kafka.producer.～ で行う
    private final ProducerFactory<Integer, String> kafkaProducerFactory;
    // 設定は application.properties の spring.kafka.consumer.～ で行う
    private final ConsumerFactory<Integer, String> kafkaConsumerFactory;

    private final MessageChannel errorChannel;

    private AtomicInteger count = new AtomicInteger(0);

    public KafkaDslSampleConfig(ProducerFactory<Integer, String> kafkaProducerFactory
            , ConsumerFactory<Integer, String> kafkaConsumerFactory
            , MessageChannel errorChannel) {
        this.kafkaProducerFactory = kafkaProducerFactory;
        this.kafkaConsumerFactory = kafkaConsumerFactory;
        this.errorChannel = errorChannel;
    }

    @Bean
    public Supplier<String> countSupplier() {
        return () -> String.valueOf(this.count.addAndGet(1));
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
                .log(LoggingHandler.Level.WARN)
                .handle(kafkaMessageHandler(kafkaProducerFactory, TOPIC_NAME))
                .get();
    }

    @Bean
    public DefaultKafkaHeaderMapper mapper() {
        return new DefaultKafkaHeaderMapper();
    }

    private KafkaProducerMessageHandlerSpec<Integer, String, ?> kafkaMessageHandler(
            ProducerFactory<Integer, String> producerFactory, String topic) {
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
    public IntegrationFlow topic1ConsumerFlow() {
        return IntegrationFlows
                .from(Kafka.messageDrivenChannelAdapter(kafkaConsumerFactory
                        , KafkaMessageDrivenChannelAdapter.ListenerMode.record, TOPIC_NAME)
                        .configureListenerContainer(c ->
                                c.ackMode(ContainerProperties.AckMode.RECORD)
                                        .idleEventInterval(100L)
                                        .id("topic1ConsumerContainer"))
                        .recoveryCallback(new ErrorMessageSendingRecoverer(errorChannel
                                , new RawRecordHeaderErrorMessageStrategy()))
                        .retryTemplate(new RetryTemplate())
                        .filterInRetry(true))
                .handle((p, h) -> {
                    log.error("★★★ " + p);
                    return null;
                })
                .get();
    }

}
