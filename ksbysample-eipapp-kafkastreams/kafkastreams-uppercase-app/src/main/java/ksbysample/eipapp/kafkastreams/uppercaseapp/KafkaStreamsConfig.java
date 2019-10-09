package ksbysample.eipapp.kafkastreams.uppercaseapp;

import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;

@Configuration
@EnableKafkaStreams
public class KafkaStreamsConfig {

    private final StreamsBuilder builder;

    public KafkaStreamsConfig(StreamsBuilder builder) {
        this.builder = builder;
    }

    /**
     * Topic1 から取得したメッセージ内の英小文字を英大文字に変換して Topic2 に送信する
     * Kafka Streams アプリケーション
     */
    @Bean
    public KStream<Integer, String> kStream() {
        KStream<Integer, String> stream = builder.stream("Topic1");
        stream
                .mapValues(s -> s.toUpperCase())
                .to("Topic2");
        return stream;
    }

}
