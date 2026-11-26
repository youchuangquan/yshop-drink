package co.yixiang.yshop.module.order.dal.mysql;

import co.yixiang.yshop.module.order.dal.mysql.storeorder.StoreOrderMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreOrderRequestKeySqlTest {

    @Test
    void requestKeyLookupSeesCancelledOrders() throws Exception {
        String sql = StoreOrderMapper.class
                .getMethod("selectByRequestKeyAnyState", Long.class, Long.class, String.class)
                .getAnnotation(Select.class).value()[0].toLowerCase();
        assertTrue(sql.contains("request_key"));
        assertTrue(sql.contains("uid"));
        assertTrue(sql.contains("shop_id"));
        assertTrue(sql.contains("order by id"));
        assertFalse(sql.contains("deleted"));
        assertFalse(sql.contains("select *"));
    }

}
