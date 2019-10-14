package ksbysample.eipapp.kafkastreams.multistreamsapp;

import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;

import java.util.Map;

@Configuration
public class LengthAppKafkaStreamsConfig extends BaseKafkaStreamsConfig {

    private static final String APPLICATION_ID = "length-streams-app";
    private static final String BEAN_NAME_PREFIX = "lengthApp";
    private static final String KAFKA_STREAMS_CONFIGURATION_BEAN_NAME = BEAN_NAME_PREFIX + "KafkaStreamsConfiguration";
    private static final String STREAMS_BUILDER_FACTORY_BEAN_NAME = BEAN_NAME_PREFIX + "StreamsBuilderFactoryBean";
    private static final String KSTREAMS_APP_BEAN_NAME = BEAN_NAME_PREFIX + "KafkaStreamsApp";

    public LengthAppKafkaStreamsConfig(KafkaProperties properties) {
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
        KStream<Integer, String> stream = builder.stream(TopicUtils.TOPIC1_NAME, TopicUtils.TOPIC1_CONSUMED);

        stream
                .map((key, value) -> new KeyValue<>(value, value.length()))
                .to(TopicUtils.TOPIC2_NAME, TopicUtils.TOPIC2_PRODUCED);

        return builder.build();
    }

}
