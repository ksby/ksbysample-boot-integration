package ksbysample.eipapp.kafkastreams.avroapp;

import ksbysample.eipapp.kafkastreams.avroapp.avro.Counter;
import ksbysample.eipapp.kafkastreams.avroapp.avro.InputData;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.processor.LogAndSkipOnInvalidTimestamp;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;

import java.util.Map;

@Configuration
public class WinPrintKafkaStreamsConfig extends BaseKafkaStreamsConfig {

    private static final String APPLICATION_ID = "winprint-streams-app";
    private static final String BEAN_NAME_PREFIX = "winprint";
    private static final String KAFKA_STREAMS_CONFIGURATION_BEAN_NAME = BEAN_NAME_PREFIX + "KafkaStreamsConfiguration";
    private static final String STREAMS_BUILDER_FACTORY_BEAN_NAME = BEAN_NAME_PREFIX + "StreamsBuilderFactoryBean";
    private static final String KSTREAMS_APP_BEAN_NAME = BEAN_NAME_PREFIX + "KafkaStreamsApp";

    private final SerdeHelper serdeHelper;

    public WinPrintKafkaStreamsConfig(KafkaProperties properties
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
        KStream<Windowed<InputData>, Counter> stream =
                builder.stream("Topic3"
                        , Consumed.with(serdeHelper.windowedInputDataKeySerde, serdeHelper.counterValueAvroSerde));

        stream.foreach((key, value) -> {
            System.out.println("★★★" + key.key() + "@" + key.window().startTime() + "->" + key.window().endTime()
                    + ":" + value.getCount());
        });

        return builder.build();
    }

}
