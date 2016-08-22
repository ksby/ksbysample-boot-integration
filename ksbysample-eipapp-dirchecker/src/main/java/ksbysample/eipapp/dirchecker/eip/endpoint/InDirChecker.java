package ksbysample.eipapp.dirchecker.eip.endpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.integration.annotation.InboundChannelAdapter;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.Poller;
import org.springframework.integration.channel.NullChannel;
import org.springframework.integration.file.FileReadingMessageSource;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.integration.transaction.DefaultTransactionSynchronizationFactory;
import org.springframework.integration.transaction.ExpressionEvaluatingTransactionSynchronizationProcessor;
import org.springframework.integration.transaction.PseudoTransactionManager;
import org.springframework.integration.transaction.TransactionSynchronizationFactory;
import org.springframework.messaging.Message;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.MatchAlwaysTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.io.File;
import java.nio.file.Paths;
import java.util.Collections;

@MessageEndpoint
public class InDirChecker {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final String DATA_DIR_PATH = "C:\\eipapp\\ksbysample-eipapp-dirchecker\\data";
    private static final String IN_DIR_PATH = DATA_DIR_PATH + "\\in";
    private static final String ERROR_DIR_PATH = DATA_DIR_PATH + "\\error";

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private NullChannel nullChannel;

    @Autowired
    private PseudoTransactionManager pseudoTransactionManager;

    @Autowired
    private FileReadingMessageSource inDirFileReadingMessageSource;

    @Bean
    public TransactionSynchronizationFactory checkFilePollerSyncFactory() {
        ExpressionParser parser = new SpelExpressionParser();
        ExpressionEvaluatingTransactionSynchronizationProcessor syncProcessor
                = new ExpressionEvaluatingTransactionSynchronizationProcessor();
        syncProcessor.setBeanFactory(applicationContext.getAutowireCapableBeanFactory());
        syncProcessor.setAfterCommitExpression(parser.parseExpression("payload.delete()"));
        syncProcessor.setAfterCommitChannel(nullChannel);
        syncProcessor.setAfterRollbackExpression(
                parser.parseExpression("payload.renameTo('" + ERROR_DIR_PATH + "\\' + payload.name)"));
        syncProcessor.setBeforeCommitChannel(nullChannel);
        return new DefaultTransactionSynchronizationFactory(syncProcessor);
    }

    @Bean
    public FileReadingMessageSource inDirFileReadingMessageSource() {
        FileReadingMessageSource source = new FileReadingMessageSource();
        source.setDirectory(Paths.get(IN_DIR_PATH).toFile());
        return source;
    }

    @Bean
    public PollerMetadata checkFilePoller() {
        PeriodicTrigger trigger = new PeriodicTrigger(1000);
        trigger.setFixedRate(true);
        PollerMetadata poller = new PollerMetadata();
        poller.setTrigger(trigger);
        poller.setTransactionSynchronizationFactory(checkFilePollerSyncFactory());

        MatchAlwaysTransactionAttributeSource matchAlwaysTransactionAttributeSource = new MatchAlwaysTransactionAttributeSource();
        matchAlwaysTransactionAttributeSource.setTransactionAttribute(new DefaultTransactionAttribute());
        TransactionInterceptor txAdvice =
                new TransactionInterceptor(pseudoTransactionManager, matchAlwaysTransactionAttributeSource);
        poller.setAdviceChain(Collections.singletonList(txAdvice));

        return poller;
    }

    @InboundChannelAdapter(value = "inChannel", poller = @Poller("checkFilePoller"))
    public Message<File> checkFile() {
        return inDirFileReadingMessageSource.receive();
    }

}
