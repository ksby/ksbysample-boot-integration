package ksbysample.eipapp.kafkastreams.windowapp;

import org.springframework.boot.autoconfigure.kafka.KafkaProperties;

public class BaseKafkaStreamsConfig {

    protected final KafkaProperties properties;

    public BaseKafkaStreamsConfig(KafkaProperties properties) {
        this.properties = properties;
    }

}
