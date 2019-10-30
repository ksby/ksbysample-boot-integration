package ksbysample.eipapp.kafkastreams.avroapp;

import ksbysample.eipapp.kafkastreams.avroapp.avro.Counter;
import ksbysample.eipapp.kafkastreams.avroapp.avro.InputData;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.processor.LogAndSkipOnInvalidTimestamp;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;

import java.time.Duration;
import java.util.Map;

@Configuration
public class CounterKafkaStreamsConfig extends BaseKafkaStreamsConfig {

    private static final String APPLICATION_ID = "counter-streams-app";
    private static final String BEAN_NAME_PREFIX = "counter";
    private static final String KAFKA_STREAMS_CONFIGURATION_BEAN_NAME = BEAN_NAME_PREFIX + "KafkaStreamsConfiguration";
    private static final String STREAMS_BUILDER_FACTORY_BEAN_NAME = BEAN_NAME_PREFIX + "StreamsBuilderFactoryBean";
    private static final String KSTREAMS_APP_BEAN_NAME = BEAN_NAME_PREFIX + "KafkaStreamsApp";

    private final SerdeHelper serdeHelper;

    public CounterKafkaStreamsConfig(KafkaProperties properties
            , SerdeHelper serdeHelper) {
        super(properties);
        this.serdeHelper = serdeHelper;
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
        KStream<String, InputData> stream =
                builder.stream("Topic2", Consumed.with(Serdes.String(), serdeHelper.inputDataValueAvroSerde));

        KTable<Windowed<InputData>, Counter> count = stream
                .groupBy((key, value) -> value
                        , Grouped.with(serdeHelper.inputDataKeyAvroSerde, serdeHelper.inputDataValueAvroSerde))
                .windowedBy(TimeWindows.of(Duration.ofSeconds(5)))
                .aggregate(
                        () -> new Counter(0L)
                        , (aggKey, newValue, aggValue) -> new Counter(aggValue.getCount() + 1)
                        , Materialized.with(serdeHelper.inputDataKeyAvroSerde, serdeHelper.counterValueAvroSerde));

        count.toStream()
                .peek((key, value) -> {
                    System.out.println("☆☆☆" + key.key() + "@" + key.window().startTime() + "->" + key.window().endTime());
                })
                .to("Topic3"
                        , Produced.with(serdeHelper.windowedInputDataKeySerde, serdeHelper.counterValueAvroSerde));

        return builder.build();
    }

}
