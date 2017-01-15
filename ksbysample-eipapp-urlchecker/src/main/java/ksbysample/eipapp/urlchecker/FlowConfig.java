package ksbysample.eipapp.urlchecker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.NullChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.core.Pollers;
import org.springframework.integration.file.splitter.FileSplitter;
import org.springframework.messaging.MessageChannel;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Slf4j
@Configuration
public class FlowConfig {

    private final static String URLLISTFILE_IN_DIR = "C:/eipapp/ksbysample-eipapp-urlchecker/in";
    private final static String URLLISTFILE_EXT_PATTERN = "*.txt";

    private final static String MESSAGE_HEADER_LINES_SIZE = "lines.size";
    private final static String MESSAGE_HEADER_SEQUENCE_SIZE = "sequenceSize";

    private final NullChannel nullChannel;

    public FlowConfig(NullChannel nullChannel) {
        this.nullChannel = nullChannel;
    }

    @Bean
    public MessageChannel urlCheckChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel writeFileChannel() {
        return new DirectChannel();
    }

    @Bean
    public Executor taskExecutor() {
        return Executors.newCachedThreadPool();
    }

    @Bean
    public IntegrationFlow urlListFilePollerFlow() {
        return IntegrationFlows
                // in ディレクトリに拡張子が .txt のファイルが存在するか 1秒間隔でチェックする
                .from(s -> s.file(new File(URLLISTFILE_IN_DIR)).patternFilter(URLLISTFILE_EXT_PATTERN)
                        , c -> c.poller(Pollers.fixedDelay(1000)))
                // スレッドを生成して .enrichHeaders 以降の処理を .from のファイルチェック処理とは別のスレッドで実行する
                .channel(c -> c.executor(taskExecutor()))
                // 見つかったファイルの行数をカウントし、Message の header に "lines.size" というキーでセットする
                // この .enrichHeaders から .enrichHeaders までの処理は writeFileFlow の .resequence 及び .aggregate
                // の処理のために "sequenceSize" header に正しい行数をセットするためのものである
                .enrichHeaders(h -> h.headerFunction(MESSAGE_HEADER_LINES_SIZE
                        , m -> {
                            List<String> lines;
                            try {
                                lines = Files.readAllLines(Paths.get(m.getPayload().toString()), StandardCharsets.UTF_8);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            return lines.size();
                        }))
                // FileSplitter クラスを利用して、ファイルを行毎に分解する
                .split(new FileSplitter())
                // スレッドを生成して .headerFilter 以降の処理を更に別のスレッドで並行処理する
                .channel(c -> c.executor(taskExecutor()))
                // Message の header から "sequenceSize" というキーの header を削除する
                .headerFilter(MESSAGE_HEADER_SEQUENCE_SIZE, false)
                // Message の header に "sequenceSize" というキーの header を追加し、"originalSequenceSize"
                // というキーの header の値をセットする
                .enrichHeaders(h -> h.headerFunction(MESSAGE_HEADER_SEQUENCE_SIZE
                        , m -> m.getHeaders().get(MESSAGE_HEADER_LINES_SIZE)))
                // Message の内容をログに出力する
                .log()
                // Message の payload に格納された URL 文字列の値をチェックし、"http://" から始まる場合には urlCheckChannel へ、
                // そうでない場合には writeFileChannel へ Message を送信する
                .<String, Boolean>route(p -> p.startsWith("http://")
                        , r -> r.subFlowMapping(true, sf -> sf.channel(urlCheckChannel()))
                                .subFlowMapping(false, sf -> sf.channel(writeFileChannel())))
                .get();
    }

    @Bean
    public IntegrationFlow urlCheckFlow() {
        return IntegrationFlows.from(urlCheckChannel())
                .channel(nullChannel)
                .get();
    }

    @Bean
    public IntegrationFlow writeFileFlow() {
        return IntegrationFlows.from(writeFileChannel())
                .channel(nullChannel)
                .get();
    }

}
