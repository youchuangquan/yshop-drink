package co.yixiang.yshop.module.order.dal.mysql;

import co.yixiang.yshop.module.order.dal.mysql.ordernumber.OrderNumberMapper;
import co.yixiang.yshop.module.order.dal.mysql.storeorderoption.StoreOrderOptionMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MalatangOrderMapperSqlTest {

    @Test
    void queueNumbersAndOrderOptionsAreReadByShop() throws Exception {
        String numbers = OrderNumberMapper.class.getMethod("selectByShop", Long.class)
                .getAnnotation(Select.class).value()[0].toLowerCase();
        String options = StoreOrderOptionMapper.class
                .getMethod("selectByOrderAndShop", String.class, Long.class)
                .getAnnotation(Select.class).value()[0].toLowerCase();
        for (String sql : new String[]{numbers, options}) {
            assertTrue(sql.contains("shop_id"));
            assertFalse(sql.contains("select *"));
        }
        assertTrue(numbers.contains("number_no"));
        assertTrue(options.contains("order_id"));
        assertTrue(options.contains("unit_price"));
    }

}
