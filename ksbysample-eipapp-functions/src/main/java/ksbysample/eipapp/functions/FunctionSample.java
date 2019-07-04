package ksbysample.eipapp.functions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.handler.GenericHandler;
import org.springframework.integration.handler.advice.RequestHandlerRetryAdvice;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.util.function.Function;

import static java.util.Collections.singletonMap;

@Slf4j
@Component
public class FunctionSample implements CommandLineRunner {

    // IntegrationFlow で定義した処理を呼び出すには、
    // * IntegrationFlows.from(Function.class)... と定義する
    // * Function<?, ?> 型の変数をフィールドに定義し
    //   @Autowired, @Qualifier("<IntegrationFlow名>.gateway"), @Lazy アノテーションを付与する
    @Autowired
    @Qualifier("throwExceptionWithRetryFlow.gateway")
    @Lazy
    private Function<String, String> throwExceptionWithRetryFlowFunc;

    @Override
    public void run(String... args) throws Exception {
        System.out.println(throwExceptionWithRetryFlowFunc.apply("success???"));
    }

    @Configuration
    static class FlowConfig {

        /**
         * リトライ回数は最大５回、リトライ時は２秒待機する RetryTemplate を生成する
         *
         * @return {@link RetryTemplate} object
         */
        @Bean
        public RetryTemplate retryTemplate() {
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
        public RequestHandlerRetryAdvice retryAdvice() {
            RequestHandlerRetryAdvice retryAdvice = new RequestHandlerRetryAdvice();
            retryAdvice.setRetryTemplate(retryTemplate());
            // リトライしても処理が成功しなかった場合には
            // MessageHandlingException#getCause#getMessage で取得したメッセージを payload
            // にセットした Message オブジェクトが次の処理に渡されるようにする
            retryAdvice.setRecoveryCallback(context -> {
                MessageHandlingException e = (MessageHandlingException) context.getLastThrowable();
                String errMsg = e.getCause().getMessage();
                log.error(errMsg);
                return errMsg;
            });
            return retryAdvice;
        }

        @Bean
        public IntegrationFlow throwExceptionWithRetryFlow() {

            return IntegrationFlows
                    .from(Function.class)
                    .handle((GenericHandler<Object>) (p, h) -> {
                        RetryContext retryContext = RetrySynchronizationManager.getContext();
                        log.warn("★★★ リトライ回数 = " + retryContext.getRetryCount());

                        // リトライ処理をさせたいので強制的に RuntimeException を throw する
                        if (true) {
                            throw new RuntimeException("error!!");
                        }
                        return p;
                    }, e -> e.advice(retryAdvice()))
                    .get();
        }

    }

}
