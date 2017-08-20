package ksbysample.eipapp.batchexecutor;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.channel.MessageChannels;
import org.springframework.integration.dsl.core.Pollers;
import org.springframework.integration.dsl.http.Http;
import org.springframework.integration.jdbc.store.JdbcChannelMessageStore;
import org.springframework.integration.jdbc.store.channel.H2ChannelMessageStoreQueryProvider;
import org.springframework.integration.store.PriorityCapableChannelMessageStore;
import org.springframework.messaging.MessageChannel;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;

/**
 * バッチ起動リクエスト受信＋バッチ起動 Flow 設定用 JavaConfig クラス
 */
@Slf4j
@Configuration
public class FlowConfig {

    private static final String WORKDIR_SLEEP_BAT = "C:/eipapp/ksbysample-eipapp-batchexecutor/bat";
    private static final String COMMAND_SLEEP_BAT = WORKDIR_SLEEP_BAT + "/sleep.bat";

    private final DataSource dataSource;

    /**
     * コンストラクタ

     * @param dataSource {@link DataSource} オブジェクト
     */
    public FlowConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * {@link FlowConfig#executeBatchQueueChannel()} の ChannelMessageStore を H2 Database
     * にするための {@link JdbcChannelMessageStore}
     *
     * @return {@link PriorityCapableChannelMessageStore} オブジェクト
     */
    @Bean
    public PriorityCapableChannelMessageStore jdbcChannelMessageStore() {
        JdbcChannelMessageStore messageStore = new JdbcChannelMessageStore(this.dataSource);
        messageStore.setChannelMessageStoreQueryProvider(new H2ChannelMessageStoreQueryProvider());
        messageStore.setPriorityEnabled(true);
        return messageStore;
    }

    /**
     * bat ファイル実行指示用の QueueChannel
     *
     * @return {@link MessageChannel} オブジェクト
     */
    @Bean
    public MessageChannel executeBatchQueueChannel() {
        return MessageChannels.queue(jdbcChannelMessageStore(), "EXECUTE_BATCH")
                .get();
    }

    /**
     * http://localhost:8080/batch?sleep=... でリクエストを受信して、sleep パラメータの値を
     * payload にセットした Message を bat ファイル実行指示用の QueueChannel に送信した後、
     * sleep パラメータの文字列をそのままテキストとしてレスポンスとして返す
     *
     * @return {@link IntegrationFlow} オブジェクト
     */
    @Bean
    public IntegrationFlow httpBatchFlow() {
        return IntegrationFlows.from(
                // http://localhost:8080/batch?sleep=... でリクエストを受信して
                // sleep の値を payload にセットした Message を生成する
                Http.inboundGateway("/batch")
                        .requestMapping(r -> r
                                .methods(HttpMethod.GET)
                                .params("sleep"))
                        .payloadExpression("#requestParams.sleep[0]"))
                .handle((p, h) -> {
                    log.info("★★★ リクエストを受信しました ( sleep = {} )", p);
                    return p;
                })
                // message を bat ファイル実行指示用の QueueChannel に送信する
                // .wireTap(...) を利用することで、処理をここで中断せず次の .handle(...) が実行されるようにする
                .wireTap(f -> f.channel(executeBatchQueueChannel()))
                // http のレスポンスには sleep パラメータの文字列をそのままテキストで返す
                .handle((p, h) -> p)
                .get();
    }

    /**
     * bat ファイル実行指示用の QueueChannel に Message が送信されているか１秒毎にチェックし、
     * 送信されている場合には受信して sleep.bat を実行する。連続して Message を送信しても
     * 多重化していないので前のバッチが終了しないと次のバッチは実行されない。
     *
     * @return {@link IntegrationFlow} オブジェクト
     */
    @Bean
    public IntegrationFlow executeBatchFlow() {
        return IntegrationFlows.from(executeBatchQueueChannel())
                // QueueChannel に Message が送信されているか１秒毎にチェックする
                .bridge(e -> e.poller(Pollers.fixedDelay(1000)))
                // バッチを最大３スレッドで起動する
                // 最大数の制限を設けないのであれば Executors.newCachedThreadPool() にする
                // .channel(c -> c.executor(Executors.newFixedThreadPool(3)))
                // sleep.bat を実行する
                .handle((p, h) -> {
                    log.info("●●● sleep.bat {} を実行します", p);
                    ProcessBuilder builder = new ProcessBuilder();
                    builder.command(COMMAND_SLEEP_BAT, (String) p, ">", "NUL")
                            .directory(new File(WORKDIR_SLEEP_BAT))
                            .redirectErrorStream(true);

                    Process process = null;
                    try {
                        process = builder.start();
                        int exitCode = process.waitFor();
                        log.info("●●● sleep.bat {} が終了しました ( exitCode = {} )", p, exitCode);
                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException(e);
                    } finally {
                        if (process != null) {
                            IOUtils.closeQuietly(process.getInputStream());
                            IOUtils.closeQuietly(process.getOutputStream());
                            IOUtils.closeQuietly(process.getErrorStream());
                            process.destroy();
                        }
                    }

                    return null;
                })
                .get();
    }

}
