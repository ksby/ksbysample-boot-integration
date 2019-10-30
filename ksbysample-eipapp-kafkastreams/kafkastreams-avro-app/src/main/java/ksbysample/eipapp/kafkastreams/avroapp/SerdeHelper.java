package ksbysample.eipapp.kafkastreams.avroapp;

import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import ksbysample.eipapp.kafkastreams.avroapp.avro.Counter;
import ksbysample.eipapp.kafkastreams.avroapp.avro.InputData;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.kstream.TimeWindowedDeserializer;
import org.apache.kafka.streams.kstream.TimeWindowedSerializer;
import org.apache.kafka.streams.kstream.Windowed;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class SerdeHelper {

    public final Serde<InputData> inputDataKeyAvroSerde;

    public final Serde<InputData> inputDataValueAvroSerde;

    public final Serde<Counter> counterValueAvroSerde;

    public final Serde<Windowed<InputData>> windowedInputDataKeySerde;

    public SerdeHelper(KafkaProperties properties) {
        Map<String, String> serdeConfig = properties.getProperties();

        inputDataKeyAvroSerde = new SpecificAvroSerde<>();
        inputDataKeyAvroSerde.configure(serdeConfig, true);

        inputDataValueAvroSerde = new SpecificAvroSerde<>();
        inputDataValueAvroSerde.configure(serdeConfig, false);

        counterValueAvroSerde = new SpecificAvroSerde<>();
        counterValueAvroSerde.configure(serdeConfig, false);

        windowedInputDataKeySerde = Serdes.serdeFrom(
                new TimeWindowedSerializer<>(inputDataKeyAvroSerde.serializer()),
                new TimeWindowedDeserializer<>(inputDataKeyAvroSerde.deserializer()));
        windowedInputDataKeySerde.configure(serdeConfig, true);
    }

}
