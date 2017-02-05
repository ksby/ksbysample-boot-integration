package ksbysample.eipapp.advice;

import lombok.extern.slf4j.Slf4j;
import org.aopalliance.aop.Advice;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.core.Pollers;
import org.springframework.integration.handler.advice.ExpressionEvaluatingRequestHandlerAdvice;
import org.springframework.integration.handler.advice.RequestHandlerRetryAdvice;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.retry.RetryContext;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.retry.support.RetryTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Slf4j
@Configuration
public class SuccessOrFailureFlowConfig {

    private final String EIPAPP_ADVICE_ROOT_DIR = "C:/eipapp/ksbysample-eipapp-advice";


    // setOnSuccessExpressionString, setOnFailureExpressionString だけ指定するサンプル
    //  ・成功時にはファイルを削除する。
    //  ・失敗時にはファイルを error ディレクトリへ移動する。
    //  ・削除、移動の処理は SpEL で記述する。

    @Bean
    public Advice deleteOrMoveAdvice() {
        ExpressionEvaluatingRequestHandlerAdvice advice
                = new ExpressionEvaluatingRequestHandlerAdvice();
        advice.setOnSuccessExpressionString("payload.delete()");
        advice.setOnFailureExpressionString(
                "payload.renameTo(new java.io.File('" + EIPAPP_ADVICE_ROOT_DIR + "/error/' + payload.name))");
        // setTrapException(true) を指定すると throw された例外が再 throw されず、
        // ログに出力されない
        advice.setTrapException(true);
        return advice;
    }

    @Bean
    public IntegrationFlow in06Flow() {
        return IntegrationFlows
                .from(s -> s.file(new File(EIPAPP_ADVICE_ROOT_DIR + "/in06"))
                        , e -> e.poller(Pollers.fixedDelay(1000)))
                .<File>handle((p, h) -> {
                    log.info("★★★ " + p.getAbsolutePath());
//                    if (true) {
//                        throw new RuntimeException("エラーです");
//                    }
                    return null;
                }, e -> e.advice(deleteOrMoveAdvice()))
                .get();
    }


    // setOnSuccessExpressionString, setOnFailureExpressionString＋setSuccessChannelName, setFailureChannelName
    // の組み合わせで指定するサンプル
    //  ・成功時には successFlow.input へ Message を送信する。
    //    successFlow ではファイルを削除する。
    //  ・失敗時には failureFlow.input へ Message を送信する。
    //    failureFlow ではファイルを error ディレクトリへ移動する。

    @Bean
    public Advice successOrFailureChannelAdvice() {
        ExpressionEvaluatingRequestHandlerAdvice advice
                = new ExpressionEvaluatingRequestHandlerAdvice();
        advice.setOnSuccessExpressionString("payload");
        advice.setSuccessChannelName("successFlow.input");
        // setOnFailureExpressionString に "payload" と記述しても Failure 用の MessageChannel には
        // File クラスではなく MessageHandlingExpressionEvaluatingAdviceException クラスの payload が渡される
        advice.setOnFailureExpressionString("payload");
        advice.setFailureChannelName("failureFlow.input");
        advice.setTrapException(true);
        return advice;
    }

    @Bean
    public IntegrationFlow in07Flow() {
        return IntegrationFlows
                .from(s -> s.file(new File(EIPAPP_ADVICE_ROOT_DIR + "/in07"))
                        , e -> e.poller(Pollers.fixedDelay(1000)))
                .<File>handle((p, h) -> {
                    log.info("★★★ " + p.getAbsolutePath());
//                    if (true) {
//                        throw new RuntimeException("エラーです");
//                    }
                    return null;
                }, e -> e.advice(successOrFailureChannelAdvice()))
                .get();
    }

    @Bean
    public IntegrationFlow successFlow() {
        return f -> f
                .<File>handle((p, h) -> {
                    // ファイルを削除する
                    try {
                        Files.delete(Paths.get(p.getAbsolutePath()));
                        log.info("ファイルを削除しました ( {} )", p.getAbsolutePath());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
    }

    @Bean
    public IntegrationFlow failureFlow() {
        return f -> f
                .<ExpressionEvaluatingRequestHandlerAdvice.MessageHandlingExpressionEvaluatingAdviceException>
                        handle((p, h) -> {
                    // MessageHandlingExpressionEvaluatingAdviceException クラスの payload から
                    // Exception 発生前の File クラスの payload を取得する
                    File file = (File) p.getEvaluationResult();

                    // ファイルを error ディレクトリへ移動する
                    Path src = Paths.get(file.getAbsolutePath());
                    Path dst = Paths.get(EIPAPP_ADVICE_ROOT_DIR + "/error/" + file.getName());
                    try {
                        Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
                        log.info("ファイルを移動しました ( {} --> {} )"
                                , src.toAbsolutePath(), dst.toAbsolutePath());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
    }


    // setOnSuccessExpressionString, setOnFailureExpressionString で Success, Failure 用の
    // MessageChannel に渡す payload の型を変更するサンプル
    //  ・元の File クラスの payload から String クラスの payload の Message に変換して
    //    Success, Failure 用の MessageChannel に送信する

    @Bean
    public Advice convertFileToStringAdvice() {
        ExpressionEvaluatingRequestHandlerAdvice advice
                = new ExpressionEvaluatingRequestHandlerAdvice();
        advice.setOnSuccessExpressionString("payload + ' の処理に成功しました。'");
        advice.setSuccessChannelName("printFlow.input");
        advice.setOnFailureExpressionString(
                "payload + ' の処理に失敗しました ( ' + #exception.class.name + ', ' + #exception.cause.message + ' )'");
        advice.setFailureChannelName("printFlow.input");
        advice.setTrapException(true);
        return advice;
    }

    @Bean
    public IntegrationFlow in08Flow() {
        return IntegrationFlows
                .from(s -> s.file(new File(EIPAPP_ADVICE_ROOT_DIR + "/in08"))
                        , e -> e.poller(Pollers.fixedDelay(1000)))
                .<File>handle((p, h) -> {
                    log.info("★★★ " + p.getAbsolutePath());
//                    if (true) {
//                        throw new RuntimeException("エラーです");
//                    }
                    return null;
                }, e -> e.advice(convertFileToStringAdvice()))
                .get();
    }

    @Bean
    public IntegrationFlow printFlow() {
        return f -> f
                // setFailureChannelName(...) の指定で転送された Message は
                // MessageHandlingExpressionEvaluatingAdviceException クラスなので、
                // .transform(...) で SpEL を利用して元の String を取得する
                .transform("payload instanceof T(org.springframework.integration.handler.advice.ExpressionEvaluatingRequestHandlerAdvice$MessageHandlingExpressionEvaluatingAdviceException)"
                        + " ? payload.evaluationResult : payload")
                .handle((p, h) -> {
                    System.out.println("●●● " + p);
                    return null;
                });

    }


    // RequestHandlerRetryAdvice と ExpressionEvaluatingRequestHandlerAdvice を一緒に指定するサンプル
    //  ・RequestHandlerRetryAdvice に指定する RetryTemplate には FlowConfig.java に書いた
    //    simpleAndFixedRetryTemplate Bean を使用する

    @Autowired
    @Qualifier("simpleAndFixedRetryTemplate")
    private RetryTemplate simpleAndFixedRetryTemplate;

    @Bean
    public IntegrationFlow in09Flow() {
        RequestHandlerRetryAdvice retryAdvice = new RequestHandlerRetryAdvice();
        retryAdvice.setRetryTemplate(this.simpleAndFixedRetryTemplate);
        retryAdvice.setRecoveryCallback(context -> {
            // リトライが全て失敗するとこの処理が実行される
            MessageHandlingException e = (MessageHandlingException) context.getLastThrowable();
            Message<?> message = ((MessageHandlingException) context.getLastThrowable()).getFailedMessage();
            File payload = (File) message.getPayload();
            log.error("●●● " + e.getRootCause().getClass().getName());
            log.error("●●● " + payload.getName());
            // 例外を再 throw して　ExpressionEvaluatingRequestHandlerAdvice の失敗時の処理
            // が実行されるようにする
            throw e;
        });

        return IntegrationFlows
                .from(s -> s.file(new File(EIPAPP_ADVICE_ROOT_DIR + "/in09"))
                        , e -> e.poller(Pollers.fixedDelay(1000)))
                .<File>handle((p, h) -> {
                    RetryContext retryContext = RetrySynchronizationManager.getContext();
                    log.info("★★★ リトライ回数 = " + retryContext.getRetryCount());

                    // 例外を throw して必ずリトライさせる
                    if (true) {
                        throw new RuntimeException("エラーです");
                    }
                    return null;
                }, e -> e
                        // RequestHandlerRetryAdvice と ExpressionEvaluatingRequestHandlerAdvice
                        // を一緒に指定する場合には RequestHandlerRetryAdvice を後に書くこと。
                        // 最初に書くとリトライしてくれない。
                        .advice(successOrFailureChannelAdvice())
                        .advice(retryAdvice))
                .get();
    }

}
