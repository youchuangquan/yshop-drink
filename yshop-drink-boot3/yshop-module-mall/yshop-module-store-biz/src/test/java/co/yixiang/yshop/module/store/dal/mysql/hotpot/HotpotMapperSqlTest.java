package co.yixiang.yshop.module.store.dal.mysql.hotpot;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotpotMapperSqlTest {

    @Test
    void shopScopedSqlDoesNotScanEveryShop() throws Exception {
        assertShopSql(HotpotSlotMapper.class.getMethod("selectByShop", Long.class));
        assertShopSql(HotpotSlotMapper.class.getMethod("addOccupied", Long.class, Long.class, int.class));
        assertShopSql(HotpotTableMapper.class.getMethod("selectByShop", Long.class));
        assertShopSql(HotpotHoldMapper.class.getMethod("selectByShop", Long.class));
        assertShopSql(HotpotHoldMapper.class.getMethod("updateStatus", Long.class, Long.class, String.class));
        assertShopSql(HotpotSessionMapper.class.getMethod("selectByShop", Long.class));
        assertShopSql(HotpotSessionMapper.class.getMethod("updateSession", Long.class, Long.class, Long.class,
                Long.class, int.class, int.class, int.class, int.class, String.class));
        assertShopSql(HotpotSessionPotMapper.class.getMethod("selectByShop", Long.class));
        assertShopSql(HotpotSessionDishMapper.class.getMethod("selectByShop", Long.class));
        assertShopSql(HotpotSessionDishMapper.class.getMethod("updateQty", Long.class, Long.class, Long.class,
                int.class, int.class));
        assertShopSql(ShopPotMapper.class.getMethod("selectByShop", Long.class));
        assertShopSql(HotpotRequestMapper.class.getMethod("selectByShop", Long.class));
        assertShopSql(HotpotRequestMapper.class.getMethod("updateLinks", Long.class, Long.class, Long.class,
                Long.class, Long.class, String.class));

        String occupy = sql(HotpotSlotMapper.class.getMethod("addOccupied", Long.class, Long.class, int.class));
        assertTrue(occupy.contains("occupied + #{delta} <= capacity"));
        String slots = sql(HotpotSlotMapper.class.getMethod("selectByShop", Long.class));
        assertTrue(slots.contains("start_time"));
        assertTrue(slots.contains("end_time"));
        String menu = sql(HotpotMenuDishMapper.class.getMethod("selectByProductId", Long.class));
        assertTrue(menu.contains("shop_id"));
        assertTrue(menu.contains("store_name"));
        assertTrue(menu.contains("price"));
        assertTrue(menu.contains("is_show"));
        assertFalse(menu.contains("vip_price"));
        assertFalse(menu.contains("select *"));
        String dishes = sql(HotpotSessionDishMapper.class.getMethod("selectByShop", Long.class));
        assertTrue(dishes.contains("price_fen"));
        assertTrue(dishes.contains("product_id"));
        assertTrue(dishes.contains("served_qty"));
        assertTrue(dishes.contains("refunded_qty"));
        assertFalse(dishes.contains("paid"));
        String dishUpdate = sql(HotpotSessionDishMapper.class.getMethod("updateQty", Long.class, Long.class,
                Long.class, int.class, int.class));
        assertTrue(dishUpdate.contains("served_qty"));
        assertTrue(dishUpdate.contains("refunded_qty"));
        assertTrue(dishUpdate.contains("<= quantity"));
        assertFalse(dishUpdate.contains("set quantity"));
        String sessionUpdate = sql(HotpotSessionMapper.class.getMethod("updateSession", Long.class, Long.class,
                Long.class, Long.class, int.class, int.class, int.class, int.class, String.class));
        assertTrue(sessionUpdate.contains("fees_waived"));
        assertFalse(sessionUpdate.contains("paid"));
    }

    @Test
    void publicScanSelectsSignAndOccupiedOnly() throws Exception {
        String scan = sql(HotpotTableMapper.class.getMethod("selectPublic", Long.class, String.class));
        assertTrue(scan.contains("shop_id"));
        assertTrue(scan.contains("sign_code"));
        assertTrue(scan.contains("occupied"));
        assertFalse(scan.contains("bound_uid"));
        assertFalse(scan.contains("owner"));
        assertFalse(scan.contains("phone"));
        assertFalse(scan.contains("select *"));
    }

    private static void assertShopSql(Method method) {
        String text = sql(method);
        assertTrue(text.contains("shop_id"), method.getName());
        assertFalse(text.contains("select *"), method.getName());
    }

    private static String sql(Method method) {
        Select select = method.getAnnotation(Select.class);
        if (select != null) {
            return select.value()[0].toLowerCase();
        }
        Update update = method.getAnnotation(Update.class);
        return update.value()[0].toLowerCase();
    }

}
