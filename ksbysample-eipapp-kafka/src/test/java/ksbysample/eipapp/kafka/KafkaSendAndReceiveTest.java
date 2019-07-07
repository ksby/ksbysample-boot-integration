package ksbysample.eipapp.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;

@Slf4j
@SpringBootTest
public class KafkaSendAndReceiveTest {

    private static final String TOPIC_NAME = "Topic1";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void sendToKafkaTest() {
        kafkaTemplate.send(TOPIC_NAME, "test message");
        // @KafkaListener でメッセージを受信するまで少し時間がかかるので 5秒 sleep する
        SleepUtils.sleep(5_000);
    }

    @TestConfiguration
    static class TestConfig {

        @KafkaListener(topics = TOPIC_NAME)
        public void listenByConsumerRecord(ConsumerRecord<?, ?> cr) {
            log.warn(cr.toString());
        }

    }

    static class SleepUtils {

        public static void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

    }

}
