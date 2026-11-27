package co.yixiang.yshop.framework.common.shop;

import java.math.BigDecimal;

import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static co.yixiang.yshop.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 按 (uid, shopId) 记账。实现方的 find/add/deduct 必须带这两个条件，这里不提供全表读取。
 */
public final class ShopPointLedger {

    private ShopPointLedger() {
    }

    public interface Store {
        BigDecimal find(long uid, long shopId);

        int add(long uid, long shopId, BigDecimal amount);

        void insert(long uid, long shopId, BigDecimal amount);

        int deduct(long uid, long shopId, BigDecimal amount);
    }

    public static BigDecimal balance(Store store, long uid, long shopId) {
        long shop = ShopAccess.requirePositiveShopId(shopId);
        requireUid(uid);
        BigDecimal value = store.find(uid, shop);
        return value == null ? BigDecimal.ZERO : value;
    }

    public static BigDecimal credit(Store store, long uid, long shopId, BigDecimal amount) {
        long shop = ShopAccess.requirePositiveShopId(shopId);
        requireUid(uid);
        ShopAccess.requireAmount(amount);
        int updated = store.add(uid, shop, amount);
        if (updated == 0) {
            store.insert(uid, shop, amount);
        }
        return balance(store, uid, shop);
    }

    public static boolean tryDebit(Store store, long uid, long shopId, BigDecimal amount) {
        long shop = ShopAccess.requirePositiveShopId(shopId);
        requireUid(uid);
        ShopAccess.requireAmount(amount);
        return store.deduct(uid, shop, amount) > 0;
    }

    private static void requireUid(long uid) {
        if (uid <= 0) {
            throw exception(BAD_REQUEST);
        }
    }

}
