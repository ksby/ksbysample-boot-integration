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
public class DelOrdersMessageHandler implements GenericHandler<List<OrdersDto>> {

    private final DataSource dataSource;

    public DelOrdersMessageHandler(@Qualifier("dataSourceWorld") DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    @Transactional(transactionManager = "transactionManagerWorld")
    public Object handle(List<OrdersDto> payload, Map<String, Object> headers) {
        JdbcMessageHandler deleteHandler
                = new JdbcMessageHandler(this.dataSource
                , "delete from orders where order_id = :payload.orderId and status = '01'");
        deleteHandler.afterPropertiesSet();
        payload.stream()
                .forEach(dto -> {
                    Message<OrdersDto> orderxMessage = MessageBuilder.withPayload(dto).build();
                    deleteHandler.handleMessage(orderxMessage);
                });
        return null;
    }
}
