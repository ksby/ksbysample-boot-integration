package ksbysample.eipapp.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.ExecutionException;

@Disabled
@Slf4j
@SpringBootTest
public class KafkaSendAndReceiveTest {

    private static final String TOPIC_NAME = "Topic1";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void sendToKafkaTest() throws ExecutionException, InterruptedException {
        // @KafkaListener の consumer が登録されるまで少し時間がかかるので 15秒 sleep する
        SleepUtils.sleep(15_000);

//        kafkaTemplate.send(TOPIC_NAME, "test message");
        for (int i = 1; i <= 10; i++) {
            kafkaTemplate.send(TOPIC_NAME, String.valueOf(i)).get();
        }
        // @KafkaListener でメッセージを受信するまで少し時間がかかるので 5秒 sleep する
        SleepUtils.sleep(5_000);
    }

    @TestConfiguration
    static class TestConfig {

        @KafkaListener(topics = TOPIC_NAME)
        public void listenByConsumerRecord1(ConsumerRecord<?, ?> cr) {
            log.warn(String.format("partition = %d, message = %s", cr.partition(), cr.value()));
        }

        @KafkaListener(topics = TOPIC_NAME)
        public void listenByConsumerRecord2(ConsumerRecord<?, ?> cr) {
            log.error(String.format("partition = %d, message = %s", cr.partition(), cr.value()));
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
