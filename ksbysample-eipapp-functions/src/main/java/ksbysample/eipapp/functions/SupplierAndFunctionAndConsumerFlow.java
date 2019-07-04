package ksbysample.eipapp.functions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.Pollers;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
//@Configuration
public class SupplierAndFunctionAndConsumerFlow {

    private AtomicInteger count = new AtomicInteger(0);

    // Supplier<?> は ? 型のオブジェクトが payload にセットされた Message を返す
    // MessageSource として利用できる
    @Bean
    public Supplier<Integer> countSupplier() {
        return () -> count.addAndGet(1);
    }

    @Bean
    public IntegrationFlow countDisplayFlow() {
        return IntegrationFlows
                .from(countSupplier()
                        , e -> e.poller(Pollers.fixedDelay(1000)))
                .transform(DoubleAndToStrFunc())
                .handle(addDotFunc())
                .handle(printFunc())
                .get();
    }

    // Function<?, ?> は .transform(...) で利用したり、
    public Function<Integer, String> DoubleAndToStrFunc() {
        return v -> String.valueOf(v * 2);
    }

    // .handle(...) で利用できる
    public Function<String, String> addDotFunc() {
        return s -> s + "...";
    }

    // Consumer<?> は .handle(...) で利用できる
    public Consumer<String> printFunc() {
        return s -> log.warn(s);
    }

}
