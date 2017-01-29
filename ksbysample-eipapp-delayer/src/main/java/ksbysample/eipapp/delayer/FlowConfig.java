package ksbysample.eipapp.delayer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.NullChannel;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.dsl.DelayerEndpointSpec;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.core.Pollers;
import org.springframework.integration.dsl.support.Transformers;
import org.springframework.integration.handler.advice.ExpressionEvaluatingRequestHandlerAdvice;
import org.springframework.integration.store.MessageGroupStore;
import org.springframework.integration.store.SimpleMessageStore;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

import java.io.File;
import java.util.Collection;
import java.util.Map;

@Slf4j
@Configuration
public class FlowConfig {

    private static final String GROUP_ID_DELAY = "DELAYFLOW_DELAY";

    private final NullChannel nullChannel;

    public FlowConfig(NullChannel nullChannel) {
        this.nullChannel = nullChannel;
    }

    /**
     * 正常処理時に payload にセットされた File クラスのファイルを削除する RequestHandlerAdvice
     *
     * @return
     */
    @Bean
    public ExpressionEvaluatingRequestHandlerAdvice fileDeleteAdvice() {
        ExpressionEvaluatingRequestHandlerAdvice requestHandlerAdvice
                = new ExpressionEvaluatingRequestHandlerAdvice();
        requestHandlerAdvice.setOnSuccessExpression("payload.delete()");
        return requestHandlerAdvice;
    }

    @Bean
    public IntegrationFlow delayFlow() {
        return IntegrationFlows
                .from(s -> s.file(new File("C:/eipapp/ksbysample-eipapp-delayer/in01"))
                        , e -> e.poller(Pollers.fixedDelay(1000)))
                .log()
                // ３秒待機する
                .delay(GROUP_ID_DELAY
                        , (DelayerEndpointSpec e) -> e.defaultDelay(3000))
                .log()
                // ６秒待機する
                .delay(GROUP_ID_DELAY, "6000")
                .log()
                // ファイルを削除する
                .bridge(e -> e.advice(fileDeleteAdvice()))
                .channel(nullChannel)
                .get();
    }

    @Bean
    public IntegrationFlow delayByMessageFlow() {
        return IntegrationFlows
                // C:/eipapp/ksbysample-eipapp-delayer/in02 には待機秒数を記述したファイルを置く
                .from(s -> s.file(new File("C:/eipapp/ksbysample-eipapp-delayer/in02"))
                        , e -> e.poller(Pollers.fixedDelay(1000)))
                // payload にセットされた File クラスを headers.originalPayload へセットする
                .enrichHeaders(h -> h.headerExpression("originalPayload", "payload"))
                // ファイルの内容を読み込んで payload にセットする
                // ※payload のクラスが File クラス --> String クラスに変わる
                .transform(Transformers.fileToString())
                .log()
                // payload にセットされた秒数待機する
                .delay(GROUP_ID_DELAY, "payload", (DelayerEndpointSpec e) -> e.defaultDelay(8000))
                .log()
                // headers.delay にセットされた秒数待機する
                .enrichHeaders(h -> h.headerExpression("delay", "payload"))
                .delay(GROUP_ID_DELAY, "headers.delay", (DelayerEndpointSpec e) -> e.defaultDelay(2000))
                .log()
                // headers.originalPayload にセットされている File クラスを payload へ戻す
                .transform("headers.originalPayload")
                // ファイルを削除する
                .bridge(e -> e.advice(fileDeleteAdvice()))
                .channel(nullChannel)
                .get();
    }

    @Bean
    public IntegrationFlow delayDynamicFlow() {
        return IntegrationFlows
                .from(s -> s.file(new File("C:/eipapp/ksbysample-eipapp-delayer/in03"))
                        , e -> e.poller(Pollers.fixedDelay(1000)))
                // 待機秒数を計算するための delayInit, delayCount の初期値を header にセットする
                .enrichHeaders(h -> h
                        .header("delayInit", 3000)
                        .header("delayCount", 0))
                .log()
                // delayCount が 3 になるまで処理をループさせたいので、別の Flow にする
                // return f -> f.～ で書いた場合、Bean名 + ".input" という MessageChannel
                // が自動生成されて、ここに Message を送信すると処理が開始される
                .channel("loopCountFlow.input")
                .get();
    }

    @Bean
    public IntegrationFlow loopCountFlow() {
        return f -> f
                // delayInit + delayCount * 1000 のミリ秒数待機する
                .delay(GROUP_ID_DELAY, m -> {
                    return ((int) m.getHeaders().get("delayInit"))
                            + (((int) m.getHeaders().get("delayCount")) * 1000);
                })
                .log()
                // headers.delayCount を +1 する
                .enrichHeaders(h -> h.headerFunction("delayCount"
                        , m -> ((int) m.getHeaders().get("delayCount")) + 1
                        , true))
                // delayCount が３未満なら loopCountFlow の最初に戻る
                // そうでなければ次の処理へ
                .routeToRecipients(r -> r
                        .recipientFlow("headers.delayCount < 3"
                                , sf -> sf.channel("loopCountFlow.input"))
                        .defaultOutputToParentFlow())
                // ファイルを削除する
                .bridge(e -> e.advice(fileDeleteAdvice()))
                .handle((p, h) -> {
                    System.out.println("ファイルを削除しました");
                    return null;
                });
    }

    @Bean
    public MessageGroupStore simpleMessageStore() {
        return new SimpleMessageStore();
    }

    /**
     * "" の String の Message を返す MessageSource
     *
     * @return
     */
    @Bean
    public MessageSource<String> stringMessageSource() {
        return () -> MessageBuilder.withPayload("").build();
    }

    @Bean
    public IntegrationFlow printMessageStoreFlow() {
        return IntegrationFlows
                // １秒毎に処理を実行したいので、１秒毎に Message を送信させる
                .from(stringMessageSource()
                        , e -> e.poller(Pollers.fixedDelay(1000)))
                // MessageStore に格納された Message の headers, payload を出力する
                .handle((p, h) -> {
                    Collection<Message<?>> delayMessagesList
                            = simpleMessageStore().getMessagesForGroup(GROUP_ID_DELAY);
                    delayMessagesList.forEach(m -> {
                        m.getHeaders().entrySet().forEach(entry -> {
                            System.out.println("[header ] " + entry.getKey() + " = " + entry.getValue());
                        });
                        System.out.println("[payload] " + m.getPayload());
                    });
                    return null;
                })
                .get();
    }

    @Bean
    public IntegrationFlow checkMessageStoreFlow() {
        return IntegrationFlows
                .from(s -> s.file(new File("C:/eipapp/ksbysample-eipapp-delayer/in04"))
                        , e -> e.poller(Pollers.fixedDelay(1000)))
                .log()
                // ３秒待機する
                .delay(GROUP_ID_DELAY
                        , (DelayerEndpointSpec e) -> e.defaultDelay(3000)
                                .messageStore(simpleMessageStore()))
                .log()
                // ファイルを削除する
                .bridge(e -> e.advice(fileDeleteAdvice()))
                .channel(nullChannel)
                .get();
    }

}
