package ksbysample.eipapp.integrationaws.flow;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.aws.outbound.S3MessageHandler;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.file.FileReadingMessageSource;
import org.springframework.integration.file.filters.AcceptAllFileListFilter;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.GenericMessage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Executors;

@Slf4j
@Configuration
public class FlowConfig {

    private static final String EIPAPP_ROOT_DIR_PATH = "D:/eipapp/ksbysample-eipapp-integrationaws";
    private static final String UPLOAD_DIR_PATH = EIPAPP_ROOT_DIR_PATH + "/upload";
    private static final String UPLOADING_DIR_PATH = EIPAPP_ROOT_DIR_PATH + "/uploading";
    private static final String S3_BUCKET = "s3bucket-integration-test-ksby";

    // リージョンは環境変数 AWS_REGION に（東京リージョンなら ap-northeast-1）、
    // AccessKeyId, SecretAccessKey はそれぞれ環境変数 AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY にセットする
    @Bean
    public AmazonS3 amazonS3() {
        return AmazonS3ClientBuilder.standard().build();
    }

    /********************************************************
     * upload ディレクトリ --> S3 ファイルアップロード処理        *
     ********************************************************/

    @Bean
    public FileReadingMessageSource uploadFileMessageSource() {
        FileReadingMessageSource source = new FileReadingMessageSource();
        source.setDirectory(new File(UPLOAD_DIR_PATH));
        source.setFilter(new AcceptAllFileListFilter<>());
        return source;
    }

    @Bean
    public MessageHandler uploadToS3MessageHandler() {
        return new S3MessageHandler(amazonS3(), S3_BUCKET);
    }

    @Bean
    public IntegrationFlow uploadToS3Flow() {
        return IntegrationFlows.from(
                // 200ミリ秒毎に upload ディレクトリを監視し、ファイルがあれば処理を進める
                uploadFileMessageSource(), c -> c.poller(Pollers.fixedDelay(200)))
                // ファイルを uploading ディレクトリへ移動する
                .<File>handle((p, h) -> {
                    try {
                        Path movedFilePath = Files.move(p.toPath(), Paths.get(UPLOADING_DIR_PATH, p.getName())
                                , StandardCopyOption.REPLACE_EXISTING);
                        return new GenericMessage<>(movedFilePath.toFile(), h);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                // ここから下はマルチスレッドで並列処理する
                .channel(c -> c.executor(Executors.newFixedThreadPool(5)))
                // 処理開始のログを出力する
                // 上の .channel(...) の直後に .log(...) を書くと並列処理されないため、.handle(...) を書いてその中でログに出力する
                .<File>handle((p, h) -> {
                    log.warn(String.format("☆☆☆ %s を S3 にアップロードします", p.getName()));
                    return new GenericMessage<>(p, h);
                })
                // S3 へアップロードする
                // S3MessageHandler は Outbound Channel Adapter で .handle(...) メソッドに渡しただけでは次の処理に行かないので、
                // .wireTap(...) で呼び出す
                .wireTap(sf -> sf
                        .handle(uploadToS3MessageHandler()))
                // アップロードしたファイルを削除し、処理終了のログを出力する
                .<File>handle((p, h) -> {
                    p.delete();
                    log.warn(String.format("★★★ %s を S3 にアップロードしました", p.getName()));
                    return null;
                })
                .get();
    }

}
