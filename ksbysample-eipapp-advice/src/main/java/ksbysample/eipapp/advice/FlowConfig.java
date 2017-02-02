package ksbysample.eipapp.advice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.channel.NullChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.core.Pollers;
import org.springframework.integration.dsl.support.GenericHandler;
import org.springframework.integration.handler.advice.RequestHandlerRetryAdvice;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.CompositeRetryPolicy;
import org.springframework.retry.policy.ExceptionClassifierRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.policy.TimeoutRetryPolicy;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.retry.support.RetryTemplate;

import java.io.File;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.singletonMap;

@Slf4j
@Configuration
public class FlowConfig {

    private final NullChannel nullChannel;

    public FlowConfig(NullChannel nullChannel) {
        this.nullChannel = nullChannel;
    }

    /**
     * 共通 retry 用 Flow
     * retryTemplate で設定されたリトライを実行する
     *
     * @param inDir         監視対象の in ディレクトリ
     * @param retryTemplate リトライ条件を設定した RetryTemplate クラスのインスタンス
     * @return
     */
    private IntegrationFlow retryFlow(File inDir, RetryTemplate retryTemplate) {
        RequestHandlerRetryAdvice retryAdvice = new RequestHandlerRetryAdvice();
        retryAdvice.setRetryTemplate(retryTemplate);
        retryAdvice.setRecoveryCallback(context -> {
            // リトライが全て失敗するとこの処理が実行される
            MessageHandlingException e = (MessageHandlingException) context.getLastThrowable();
            Message<?> message = ((MessageHandlingException) context.getLastThrowable()).getFailedMessage();
            File payload = (File) message.getPayload();
            log.error("●●● " + e.getRootCause().getClass().getName());
            log.error("●●● " + payload.getName());
            return null;
        });

        return IntegrationFlows
                .from(s -> s.file(inDir)
                        , e -> e.poller(Pollers.fixedDelay(1000)))
                .log()
                .handle((GenericHandler<Object>) (p, h) -> {
                    RetryContext retryContext = RetrySynchronizationManager.getContext();
                    log.info("★★★ リトライ回数 = " + retryContext.getRetryCount());

                    // 例外を throw して強制的にリトライさせる
                    if (true) {
                        throw new RuntimeException();
                    }
                    return p;
                }, e -> e.advice(retryAdvice))
                .log()
                .channel(nullChannel)
                .get();
    }

    /**
     * リトライ最大回数は５回、リトライ時は２秒待機する
     *
     * @return
     */
    @Bean
    public RetryTemplate simpleAndFixedRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // SimpleRetryPolicy
        retryTemplate.setRetryPolicy(
                new SimpleRetryPolicy(5, singletonMap(Exception.class, true)));

        // FixedBackOffPolicy
        FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
        fixedBackOffPolicy.setBackOffPeriod(2000);
        retryTemplate.setBackOffPolicy(fixedBackOffPolicy);

        return retryTemplate;
    }

    @Bean
    public IntegrationFlow retry01Flow() {
        return retryFlow(new File("C:/eipapp/ksbysample-eipapp-advice/in01")
                , simpleAndFixedRetryTemplate());
    }

    /**
     * リトライは最大４５秒、リトライ時は初期値２秒、最大１０秒、倍数２(2,4,8,10,10,...)待機する
     *
     * @return
     */
    @Bean
    public RetryTemplate timeoutAndExponentialRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // TimeoutRetryPolicy
        TimeoutRetryPolicy timeoutRetryPolicy = new TimeoutRetryPolicy();
        timeoutRetryPolicy.setTimeout(45000);
        retryTemplate.setRetryPolicy(timeoutRetryPolicy);

        // ExponentialBackOffPolicy
        ExponentialBackOffPolicy exponentialBackOffPolicy = new ExponentialBackOffPolicy();
        exponentialBackOffPolicy.setInitialInterval(2000);
        exponentialBackOffPolicy.setMaxInterval(10000);
        exponentialBackOffPolicy.setMultiplier(2.0);
        retryTemplate.setBackOffPolicy(exponentialBackOffPolicy);

        return retryTemplate;
    }

    @Bean
    public IntegrationFlow retry02Flow() {
        return retryFlow(new File("C:/eipapp/ksbysample-eipapp-advice/in02")
                , timeoutAndExponentialRetryTemplate());
    }

    /**
     * ３回リトライするか、10秒を越えるまでリトライする
     * FixedBackOffPolicy はリトライ時の待機時間を１秒にしているので、
     * ３回リトライで終了する
     *
     * @return
     */
    @Bean
    public RetryTemplate compositeRetryTemplate01() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // CompositeRetryPolicy
        CompositeRetryPolicy compositeRetryPolicy = new CompositeRetryPolicy();
        RetryPolicy[] retryPolicies = new RetryPolicy[2];
        retryPolicies[0] = new SimpleRetryPolicy(3, singletonMap(Exception.class, true));
        TimeoutRetryPolicy timeoutRetryPolicy = new TimeoutRetryPolicy();
        timeoutRetryPolicy.setTimeout(10000);
        retryPolicies[1] = timeoutRetryPolicy;
        compositeRetryPolicy.setPolicies(retryPolicies);
        retryTemplate.setRetryPolicy(compositeRetryPolicy);

        // FixedBackOffPolicy
        FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
        fixedBackOffPolicy.setBackOffPeriod(1000);
        retryTemplate.setBackOffPolicy(fixedBackOffPolicy);

        return retryTemplate;
    }

    @Bean
    public IntegrationFlow retry03Flow() {
        return retryFlow(new File("C:/eipapp/ksbysample-eipapp-advice/in03")
                , compositeRetryTemplate01());
    }

    /**
     * ３回リトライするか、10秒を越えるまでリトライする
     * FixedBackOffPolicy はリトライ時の待機時間を５秒にしているので、
     * １０秒リトライで終了する
     *
     * @return
     */
    @Bean
    public RetryTemplate compositeRetryTemplate02() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // CompositeRetryPolicy
        CompositeRetryPolicy compositeRetryPolicy = new CompositeRetryPolicy();
        RetryPolicy[] retryPolicies = new RetryPolicy[2];
        retryPolicies[0] = new SimpleRetryPolicy(3, singletonMap(Exception.class, true));
        TimeoutRetryPolicy timeoutRetryPolicy = new TimeoutRetryPolicy();
        timeoutRetryPolicy.setTimeout(10000);
        retryPolicies[1] = timeoutRetryPolicy;
        compositeRetryPolicy.setPolicies(retryPolicies);
        retryTemplate.setRetryPolicy(compositeRetryPolicy);

        // FixedBackOffPolicy
        FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
        fixedBackOffPolicy.setBackOffPeriod(5000);
        retryTemplate.setBackOffPolicy(fixedBackOffPolicy);

        return retryTemplate;
    }

    @Bean
    public IntegrationFlow retry04Flow() {
        return retryFlow(new File("C:/eipapp/ksbysample-eipapp-advice/in04")
                , compositeRetryTemplate02());
    }

    /**
     * ここから下は RequestHandlerRetryAdvice は使用せず RetryTemplate を直接使用して
     * ExceptionClassifierRetryPolicy でリトライした例である
     */

    @Autowired
    private RetryTemplateMessageHandler retryTemplateMessageHandler;

    @Bean
    public IntegrationFlow retry05Flow() {
        return IntegrationFlows
                .from(s -> s.file(new File("C:/eipapp/ksbysample-eipapp-advice/in05"))
                        , e -> e.poller(Pollers.fixedDelay(1000)))
                .log()
                .handle(this.retryTemplateMessageHandler)
                .log()
                .channel(nullChannel)
                .get();
    }

    @Configuration
    public static class RetryTemplateConfig {

        @Bean
        public RetryTemplate exceptionClassifierRetryTemplate() {
            RetryTemplate retryTemplate = new RetryTemplate();

            // ExceptionClassifierRetryPolicy
            ExceptionClassifierRetryPolicy retryPolicy = new ExceptionClassifierRetryPolicy();
            Map<Class<? extends Throwable>, RetryPolicy> policyMap = new HashMap<>();
            //  FileSystemAlreadyExistsException なら TimeoutRetryPolicy で最大１分間リトライ
            TimeoutRetryPolicy timeoutRetryPolicy = new TimeoutRetryPolicy();
            timeoutRetryPolicy.setTimeout(60000);
            policyMap.put(FileSystemAlreadyExistsException.class, timeoutRetryPolicy);
            //  FileSystemNotFoundException なら SimpleRetryPolicy で最大５回リトライ
            policyMap.put(FileSystemNotFoundException.class
                    , new SimpleRetryPolicy(5
                            , singletonMap(FileSystemNotFoundException.class, true)));
            retryPolicy.setPolicyMap(policyMap);
            retryTemplate.setRetryPolicy(retryPolicy);

            // ExponentialBackOffPolicy で初期の待機時間２秒、最大待機時間１０秒、倍数２を指定
            ExponentialBackOffPolicy exponentialBackOffPolicy = new ExponentialBackOffPolicy();
            exponentialBackOffPolicy.setInitialInterval(2000);
            exponentialBackOffPolicy.setMaxInterval(10000);
            exponentialBackOffPolicy.setMultiplier(2.0);
            retryTemplate.setBackOffPolicy(exponentialBackOffPolicy);

            return retryTemplate;
        }

    }

    @MessageEndpoint
    public static class RetryTemplateMessageHandler implements GenericHandler<File> {

        private final RetryTemplate exceptionClassifierRetryTemplate;

        public RetryTemplateMessageHandler(
                @Qualifier("exceptionClassifierRetryTemplate") RetryTemplate exceptionClassifierRetryTemplate) {
            this.exceptionClassifierRetryTemplate = exceptionClassifierRetryTemplate;
        }

        @Override
        public Object handle(File payload, Map<String, Object> headers) {
            Object result = this.exceptionClassifierRetryTemplate.execute(
                    context -> {
                        log.info("★★★ リトライ回数 = " + context.getRetryCount());

                        if (true) {
//                            throw new FileSystemAlreadyExistsException();
                            throw new FileSystemNotFoundException();
                        }
                        return payload;
                    }, context -> {
                        Exception e = (Exception) context.getLastThrowable();
                        log.error("●●● " + e.getClass().getName());
                        log.error("●●● " + payload.getName());
                        return payload;
                    });

            return result;
        }

    }

}
