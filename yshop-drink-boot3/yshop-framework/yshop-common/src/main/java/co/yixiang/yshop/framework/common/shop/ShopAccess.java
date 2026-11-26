package co.yixiang.yshop.framework.common.shop;

import co.yixiang.yshop.framework.common.exception.ServiceException;

import java.math.BigDecimal;

import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static co.yixiang.yshop.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 门店隔离口径。权限失败用已有的 403，参数不合法用已有的 400。
 * 调用方必须把返回的 shopId 放进 SQL 条件，不能先查全表再在内存里丢掉他店数据。
 */
public final class ShopAccess {

    private ShopAccess() {
    }

    public static long requirePositiveShopId(Object raw) {
        Long shopId = parseShopId(raw);
        if (shopId == null || shopId <= 0) {
            throw exception(BAD_REQUEST);
        }
        return shopId;
    }

    /**
     * 店员只能操作登录态上的本店。未绑定门店、或请求里带了另一家店，都拒绝，且不会执行写入。
     */
    public static long staffShop(Long loginShopId, Object requestedShopId) {
        if (loginShopId == null || loginShopId <= 0) {
            throw exception(FORBIDDEN);
        }
        if (hasRequestedShop(requestedShopId)) {
            Long requested = parseShopId(requestedShopId);
            if (requested == null || requested <= 0) {
                throw exception(BAD_REQUEST);
            }
            if (!loginShopId.equals(requested)) {
                throw exception(FORBIDDEN);
            }
        }
        return loginShopId;
    }

    public static void staffWrite(Long loginShopId, Object requestedShopId, Long resourceShopId, Runnable write) {
        long shopId = staffShop(loginShopId, requestedShopId);
        assertSameShop(resourceShopId, shopId);
        write.run();
    }

    public static void assertSameShop(Long resourceShopId, long operationShopId) {
        if (resourceShopId == null || operationShopId <= 0) {
            throw exception(BAD_REQUEST);
        }
        if (resourceShopId.longValue() != operationShopId) {
            throw exception(FORBIDDEN);
        }
    }

    /**
     * 券必须精确属于这一家店。0、空、逗号分隔的多店都不算本店的券。
     */
    public static void assertCouponBelongs(String couponShopId, long operationShopId) {
        if (operationShopId <= 0) {
            throw exception(BAD_REQUEST);
        }
        Long couponShop;
        try {
            couponShop = parseShopId(couponShopId);
        } catch (ServiceException ex) {
            throw exception(FORBIDDEN);
        }
        if (couponShop == null || couponShop != operationShopId) {
            throw exception(FORBIDDEN);
        }
    }

    public static void useCoupon(String couponShopId, long operationShopId, Runnable redeem) {
        assertCouponBelongs(couponShopId, operationShopId);
        redeem.run();
    }

    public static String shopKey(long shopId) {
        return Long.toString(requirePositiveShopId(shopId));
    }

    public static int toIntShop(long shopId) {
        long normalized = requirePositiveShopId(shopId);
        if (normalized > Integer.MAX_VALUE) {
            throw exception(BAD_REQUEST);
        }
        return (int) normalized;
    }

    public static Long parseShopId(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return null;
        }
        if (!text.matches("-?\\d+")) {
            throw exception(BAD_REQUEST);
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            throw exception(BAD_REQUEST);
        }
    }

    public static void requireAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw exception(BAD_REQUEST);
        }
    }

    private static boolean hasRequestedShop(Object requestedShopId) {
        if (requestedShopId == null) {
            return false;
        }
        if (requestedShopId instanceof String text) {
            return !text.isBlank();
        }
        return true;
    }

}
