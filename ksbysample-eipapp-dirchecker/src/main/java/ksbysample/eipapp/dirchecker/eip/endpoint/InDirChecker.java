package ksbysample.eipapp.dirchecker.eip.endpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.InboundChannelAdapter;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.Poller;
import org.springframework.integration.file.FileReadingMessageSource;
import org.springframework.integration.file.filters.AcceptOnceFileListFilter;
import org.springframework.integration.file.filters.CompositeFileListFilter;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.messaging.Message;
import org.springframework.scheduling.support.PeriodicTrigger;

import java.io.File;
import java.nio.file.Paths;

@MessageEndpoint
public class InDirChecker {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final String IN_DIR_PATH = "C:\\eipapp\\ksbysample-eipapp-dirchecker\\data\\in";

    @Autowired
    private FileReadingMessageSource inDirFileReadingMessageSource;

    @Bean
    public FileReadingMessageSource inDirFileReadingMessageSource() {
        FileReadingMessageSource source = new FileReadingMessageSource();
        source.setDirectory(Paths.get(IN_DIR_PATH).toFile());

        // 動作確認用に一時的に AcceptOnceFileListFilter をセットする
        // ※AcceptOnceFileListFilter は同一のファイル名、タイムスタンプのファイルを
        //   １度のみ処理するための Filter である
        CompositeFileListFilter<File> filter = new CompositeFileListFilter<>();
        filter.addFilter(new AcceptOnceFileListFilter<File>());
        source.setFilter(filter);

        return source;
    }

    @Bean
    public PollerMetadata checkFilePoller() {
        PeriodicTrigger trigger = new PeriodicTrigger(1000);
        trigger.setFixedRate(true);
        PollerMetadata poller = new PollerMetadata();
        poller.setTrigger(trigger);
        return poller;
    }

    @InboundChannelAdapter(value = "inChannel", poller = @Poller("checkFilePoller"))
    public Message<File> checkFile() {
        return inDirFileReadingMessageSource.receive();
    }

}
