package ksbysample.eipapp.kafkastreams.windowapp;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;

import java.time.Duration;
import java.util.Map;

import static org.apache.kafka.streams.kstream.Suppressed.BufferConfig.unbounded;

@Configuration
public class SessionWindowKafkaStreamsConfig extends BaseKafkaStreamsConfig {

    private static final String APPLICATION_ID = "sessionwindow-streams-app";
    private static final String BEAN_NAME_PREFIX = "sessionWindow";
    private static final String KAFKA_STREAMS_CONFIGURATION_BEAN_NAME = BEAN_NAME_PREFIX + "KafkaStreamsConfiguration";
    private static final String STREAMS_BUILDER_FACTORY_BEAN_NAME = BEAN_NAME_PREFIX + "StreamsBuilderFactoryBean";
    private static final String KSTREAMS_APP_BEAN_NAME = BEAN_NAME_PREFIX + "KafkaStreamsApp";

    public SessionWindowKafkaStreamsConfig(KafkaProperties properties) {
        super(properties);
    }

    @Bean(KAFKA_STREAMS_CONFIGURATION_BEAN_NAME)
    public KafkaStreamsConfiguration kafkaStreamsConfiguration() {
        Map<String, Object> streamsProperties = this.properties.buildStreamsProperties();
        streamsProperties.put(StreamsConfig.APPLICATION_ID_CONFIG, APPLICATION_ID);
        return new KafkaStreamsConfiguration(streamsProperties);
    }

    @Bean(STREAMS_BUILDER_FACTORY_BEAN_NAME)
    public StreamsBuilderFactoryBean streamsBuilderFactoryBean() {
        return new StreamsBuilderFactoryBean(kafkaStreamsConfiguration());
    }

    @Bean(KSTREAMS_APP_BEAN_NAME)
    public Topology kafkaStreamsApp() throws Exception {
        StreamsBuilder builder = streamsBuilderFactoryBean().getObject();
        KStream<String, String> stream =
                builder.stream("Topic1", Consumed.with(Serdes.String(), Serdes.String()));

        KTable<Windowed<String>, Long> count = stream
                .groupBy((key, value) -> value)
                .windowedBy(SessionWindows.with(Duration.ofSeconds(5)).grace(Duration.ZERO))
                .count(Materialized.with(Serdes.String(), Serdes.Long()))
                // .suppress(Suppressed.untilWindowCloses(unbounded())) が記述されると
                // 例えば 15秒以内に a, a, a と入力しただけでは Topic4 にはメッセージは送信されず、
                // 15秒経過した後に同じキーである a のメッセージが来ると１つ前の SessionWindow がクローズされて Topic4 にメッセージが送信される
                .suppress(Suppressed.untilWindowCloses(unbounded()));

        count.toStream()
                .map((key, value) ->
                        new KeyValue<>(key.key() + "@" + key.window().startTime() + "->" + key.window().endTime()
                                , value))
                .to("Topic4", Produced.with(Serdes.String(), Serdes.Long()));

        return builder.build();
    }

}
