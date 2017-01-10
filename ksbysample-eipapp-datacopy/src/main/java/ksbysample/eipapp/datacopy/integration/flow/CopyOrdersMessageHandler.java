package ksbysample.eipapp.datacopy.integration.flow;

import ksbysample.eipapp.datacopy.dto.OrdersDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.dsl.support.GenericHandler;
import org.springframework.integration.jdbc.JdbcMessageHandler;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

@MessageEndpoint
public class CopyOrdersMessageHandler implements GenericHandler<List<OrdersDto>> {

    private final DataSource dataSource;

    public CopyOrdersMessageHandler(@Qualifier("dataSourceKsbylending") DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    @Transactional(transactionManager = "transactionManagerKsbylending")
    public Object handle(List<OrdersDto> payload, Map<String, Object> headers) {
        JdbcMessageHandler insertHandler
                = new JdbcMessageHandler(this.dataSource, "insert into orders (order_id) values (:payload.orderId)");
        insertHandler.afterPropertiesSet();
        payload.stream()
                .forEach(dto -> {
                    Message<OrdersDto> orderxMessage = MessageBuilder.withPayload(dto).build();
                    insertHandler.handleMessage(orderxMessage);
                });
        return payload;
    }
}
