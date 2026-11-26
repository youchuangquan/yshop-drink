package co.yixiang.yshop.framework.common.shop;

import co.yixiang.yshop.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopPointLedgerTest {

    @Test
    void pointsGrantedAtShopACannotPayShopB() {
        KeyedPointStore store = new KeyedPointStore();
        ShopPointLedger.credit(store, 8L, 1L, new BigDecimal("30"));
        ShopPointLedger.credit(store, 8L, 2L, new BigDecimal("5"));

        assertEquals(new BigDecimal("30"), ShopPointLedger.balance(store, 8L, 1L));
        assertEquals(new BigDecimal("5"), ShopPointLedger.balance(store, 8L, 2L));
        assertFalse(ShopPointLedger.tryDebit(store, 8L, 2L, new BigDecimal("10")));
        assertEquals(new BigDecimal("30"), ShopPointLedger.balance(store, 8L, 1L));
        assertEquals(new BigDecimal("5"), ShopPointLedger.balance(store, 8L, 2L));

        assertTrue(ShopPointLedger.tryDebit(store, 8L, 1L, new BigDecimal("10")));
        assertEquals(new BigDecimal("20"), ShopPointLedger.balance(store, 8L, 1L));
        assertEquals(new BigDecimal("5"), ShopPointLedger.balance(store, 8L, 2L));
        assertEquals(0, store.fullScans);
    }

    @Test
    void missingShopIsRejected() {
        KeyedPointStore store = new KeyedPointStore();
        ServiceException ex = assertThrows(ServiceException.class,
                () -> ShopPointLedger.credit(store, 8L, 0L, BigDecimal.ONE));
        assertEquals(BAD_REQUEST.getCode(), ex.getCode());
        assertTrue(store.rows.isEmpty());
    }

    /**
     * 只按 uid + shopId 读写。调用 findAll 会让测试失败。
     */
    private static final class KeyedPointStore implements ShopPointLedger.Store {
        private final Map<String, BigDecimal> rows = new HashMap<>();
        private int fullScans;

        @Override
        public BigDecimal find(long uid, long shopId) {
            return rows.get(key(uid, shopId));
        }

        @Override
        public int add(long uid, long shopId, BigDecimal amount) {
            String key = key(uid, shopId);
            BigDecimal current = rows.get(key);
            if (current == null) {
                return 0;
            }
            rows.put(key, current.add(amount));
            return 1;
        }

        @Override
        public void insert(long uid, long shopId, BigDecimal amount) {
            rows.put(key(uid, shopId), amount);
        }

        @Override
        public int deduct(long uid, long shopId, BigDecimal amount) {
            String key = key(uid, shopId);
            BigDecimal current = rows.get(key);
            if (current == null || current.compareTo(amount) < 0) {
                return 0;
            }
            rows.put(key, current.subtract(amount));
            return 1;
        }

        private String key(long uid, long shopId) {
            return uid + ":" + shopId;
        }
    }

}
