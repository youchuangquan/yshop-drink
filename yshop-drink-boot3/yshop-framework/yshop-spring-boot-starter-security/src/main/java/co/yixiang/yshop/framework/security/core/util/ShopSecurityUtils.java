package co.yixiang.yshop.framework.security.core.util;

import co.yixiang.yshop.framework.common.shop.ShopAccess;
import co.yixiang.yshop.framework.security.core.LoginUser;

/**
 * 从当前登录态取出店员所属门店。
 */
public final class ShopSecurityUtils {

    private ShopSecurityUtils() {
    }

    public static long currentStaffShop(Object requestedShopId) {
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        Long loginShopId = user == null ? null : user.getShopId();
        return ShopAccess.staffShop(loginShopId, requestedShopId);
    }

}
