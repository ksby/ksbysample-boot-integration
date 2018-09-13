package ksbysample.eipapp.cloudaws.flow;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.file.FileReadingMessageSource;
import org.springframework.integration.file.filters.AcceptAllFileListFilter;
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

    private static final String EIPAPP_ROOT_DIR_PATH = "D:/eipapp/ksbysample-eipapp-cloudaws";
    private static final String UPLOAD_DIR_PATH = EIPAPP_ROOT_DIR_PATH + "/upload";
    private static final String UPLOADING_DIR_PATH = EIPAPP_ROOT_DIR_PATH + "/uploading";
    private static final String S3_BUCKET = "s3bucket-integration-test-ksby";

    // リージョンは環境変数 AWS_REGION に（東京リージョンなら ap-northeast-1）、
    // AccessKeyId, SecretAccessKey はそれぞれ環境変数 AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY にセットする
    @Bean
    public AmazonS3 amazonS3() {
        return AmazonS3ClientBuilder.standard().build();
    }

    @Bean
    public TransferManager transferManager() {
        return TransferManagerBuilder.standard().withS3Client(amazonS3()).build();
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
                .channel(c -> c.executor(Executors.newFixedThreadPool(2)))
                // 処理開始のログを出力し、S3 へアップロードする
                .<File>handle((p, h) -> {
                    log.warn(String.format("☆☆☆ %s を S3 にアップロードします", p.getName()));
                    try {
                        // .waitForUploadResult() も呼び出してアップロード完了を待たないとファイルはアップロードされない
                        transferManager()
                                .upload(S3_BUCKET, p.getName(), p)
                                .waitForUploadResult();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    return new GenericMessage<>(p, h);
                })
                // アップロードしたファイルを削除し、処理終了のログを出力する
                .<File>handle((p, h) -> {
                    p.delete();
                    log.warn(String.format("★★★ %s を S3 にアップロードしました", p.getName()));
                    return null;
                })
                .get();
    }

}
