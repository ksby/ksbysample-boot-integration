package ksbysample.eipapp.datacopy.dto;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class OrdersDtoRowMapper implements RowMapper<OrdersDto> {

    @Override
    public OrdersDto mapRow(ResultSet rs, int rowNum) throws SQLException {
        OrdersDto ordersDto = new OrdersDto();
        ordersDto.setOrderId(rs.getString("order_id"));
        ordersDto.setStatus(rs.getString("status"));
        return ordersDto;
    }

}
