package ksbysample.eipapp.kafkastreams.multistreamsapp;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;

public class TopicUtils {

    public static final String TOPIC1_NAME = "Topic1";
    public static final Consumed TOPIC1_CONSUMED = Consumed.with(Serdes.Integer(), Serdes.String());

    public static final String TOPIC2_NAME = "Topic2";
    public static final Produced TOPIC2_PRODUCED = Produced.with(Serdes.String(), Serdes.Integer());
    public static final Consumed TOPIC2_CONSUMED = Consumed.with(Serdes.String(), Serdes.Integer());

    public static final String TOPIC3_NAME = "Topic3";
    public static final Produced TOPIC3_PRODUCED = Produced.with(Serdes.String(), Serdes.Long());

    public static final String TOPIC4_NAME = "Topic4";
    public static final Produced TOPIC4_PRODUCED = Produced.with(Serdes.String(), Serdes.Long());

}
