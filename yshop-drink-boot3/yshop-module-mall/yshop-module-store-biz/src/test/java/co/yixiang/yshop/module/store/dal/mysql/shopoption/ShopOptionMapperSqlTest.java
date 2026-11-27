package co.yixiang.yshop.module.store.dal.mysql.shopoption;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopOptionMapperSqlTest {

    @Test
    void flavorAndAddonRowsAreReadAndClearedByShop() throws Exception {
        String select = ShopOptionMapper.class.getMethod("selectByShop", Long.class)
                .getAnnotation(Select.class).value()[0].toLowerCase();
        String delete = ShopOptionMapper.class.getMethod("deleteByShop", Long.class)
                .getAnnotation(Update.class).value()[0].toLowerCase();
        for (String sql : new String[]{select, delete}) {
            assertTrue(sql.contains("shop_id"));
            assertFalse(sql.contains("select *"));
        }
        assertTrue(select.contains("price"));
        assertTrue(select.contains("kind"));
    }

}
