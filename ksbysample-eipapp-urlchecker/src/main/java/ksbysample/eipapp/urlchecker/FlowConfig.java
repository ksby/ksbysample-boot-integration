package ksbysample.eipapp.urlchecker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.core.Pollers;
import org.springframework.integration.file.splitter.FileSplitter;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Slf4j
@Configuration
public class FlowConfig {

    private final static String URLLISTFILE_IN_DIR = "C:/eipapp/ksbysample-eipapp-urlchecker/in";
    private final static String URLLISTFILE_EXT_PATTERN = "*.txt";
    private final static String RESULTFILE_OUT_DIR = "C:/eipapp/ksbysample-eipapp-urlchecker/out";

    private final static String MESSAGE_HEADER_LINES_SIZE = "lines.size";
    private final static String MESSAGE_HEADER_SEQUENCE_SIZE = "sequenceSize";
    private final static String MESSAGE_HEADER_HTTP_STATUS = "httpStatus";
    private final static String MESSAGE_HEADER_FILE_NAME = "file_name";
    private final static String MESSAGE_HEADER_FILE_ORIGINALFILE = "file_originalFile";

    @Bean
    public MessageChannel urlCheckChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel writeFileChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel deleteFileChannel() {
        return new DirectChannel();
    }

    @Bean
    public Executor taskExecutor() {
        return Executors.newCachedThreadPool();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
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
                // スレッドを生成して、以降の処理を別のスレッドで並行処理する
                .channel(c -> c.executor(taskExecutor()))
                // Message の payload に格納された URL にアクセスし、HTTP ステータスコードを取得する
                // 取得した HTTP ステータスコードは Message の header に "httpStatus" というキーでセットする
                .handle((p, h) -> {
                    String statusCode;
                    try {
                        ResponseEntity<String> response = restTemplate().getForEntity(p.toString(), String.class);
                        statusCode = response.getStatusCode().toString();
                    } catch (HttpClientErrorException e) {
                        statusCode = e.getStatusCode().toString();
                    } catch (Exception e) {
                        statusCode = e.getMessage();
                    }
                    log.info(statusCode + " : " + p.toString());
                    return MessageBuilder.withPayload(p)
                            .setHeader(MESSAGE_HEADER_HTTP_STATUS, statusCode)
                            .build();
                })
                // writeFileChannel へ Message を送信する
                .channel(writeFileChannel())
                .get();
    }

    @Bean
    public IntegrationFlow writeFileFlow() {
        return IntegrationFlows.from(writeFileChannel())
                // Message の payload のデータを URL だけから URL,HTTPステータスコード に変更する
                .handle((p, h) -> MessageBuilder.withPayload(p + "," + h.get(MESSAGE_HEADER_HTTP_STATUS)).build())
                // Message が流れる順番をファイルに書かれている順番にする
                // スレッドを生成して並行処理させていたため、.resequence() を呼ぶ前は順不同になっている
                .resequence()
                .log()
                // 1つの URL につき 1つの Message 、かつ複数 Message になっているのを、
                // 1つの List に集約して 1 Message に変更する
                .aggregate()
                .log()
                // out ディレクトリに結果ファイルを出力する
                // 結果ファイルには URL と HTTP ステータスコード を出力する
                .handle((p, h) -> {
                    Path outPath = Paths.get(RESULTFILE_OUT_DIR, h.get(MESSAGE_HEADER_FILE_NAME).toString());
                    @SuppressWarnings("unchecked")
                    List<String> lines = (List<String>) p;
                    try {
                        Files.write(outPath, lines, StandardCharsets.UTF_8
                                , StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return p;
                })
                // deleteFileChannel へ Message を送信する
                .channel(deleteFileChannel())
                .get();
    }

    @Bean
    public IntegrationFlow deleteFileFlow() {
        return IntegrationFlows.from(deleteFileChannel())
                // in ディレクトリのファイルを削除する
                .handle((p, h) -> {
                    try {
                        Files.delete(Paths.get(h.get(MESSAGE_HEADER_FILE_ORIGINALFILE).toString()));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    // ここで処理が終了するので null を返す
                    return null;
                })
                .get();
    }

}
