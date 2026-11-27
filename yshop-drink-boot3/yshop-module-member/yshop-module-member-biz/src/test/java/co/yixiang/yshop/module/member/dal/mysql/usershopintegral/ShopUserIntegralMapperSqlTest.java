package co.yixiang.yshop.module.member.dal.mysql.usershopintegral;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopUserIntegralMapperSqlTest {

    @Test
    void balanceGrantAndDebitAreBoundToUidAndShop() throws Exception {
        String select = ShopUserIntegralMapper.class
                .getMethod("selectIntegral", Long.class, Long.class)
                .getAnnotation(Select.class).value()[0].toLowerCase();
        String add = ShopUserIntegralMapper.class
                .getMethod("add", Long.class, Long.class, BigDecimal.class)
                .getAnnotation(Update.class).value()[0].toLowerCase();
        String deduct = ShopUserIntegralMapper.class
                .getMethod("deduct", Long.class, Long.class, BigDecimal.class)
                .getAnnotation(Update.class).value()[0].toLowerCase();

        for (String sql : new String[]{select, add, deduct}) {
            assertTrue(sql.contains("uid"));
            assertTrue(sql.contains("shop_id"));
            assertFalse(sql.contains("select *"));
        }
        assertTrue(deduct.contains("integral >="));
    }

}
