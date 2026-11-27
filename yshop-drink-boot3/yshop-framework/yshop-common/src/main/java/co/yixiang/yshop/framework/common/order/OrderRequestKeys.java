package co.yixiang.yshop.framework.common.order;

import java.util.regex.Pattern;

import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static co.yixiang.yshop.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 下单和支付通知用的稳定键。
 * 旧客户端可以不传下单键，请求仍能创建订单，那一次不算防重。
 * 空串、空白，以及支付通知上缺失的键，都拒绝。
 */
public final class OrderRequestKeys {

    public static final int ORDER_KEY_MAX = 64;
    public static final int NOTIFY_KEY_MAX = 128;
    private static final int MIN = 8;
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]+");

    private OrderRequestKeys() {
    }

    /**
     * @return null 表示字段没传。这不是防重通过，只是旧报文还能下单。
     */
    public static String legacy(String raw) {
        if (raw == null) {
            return null;
        }
        return normalize(raw, ORDER_KEY_MAX);
    }

    public static String require(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw exception(BAD_REQUEST);
        }
        return normalize(raw, ORDER_KEY_MAX);
    }

    public static String requireNotify(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw exception(BAD_REQUEST);
        }
        return normalize(raw, NOTIFY_KEY_MAX);
    }

    private static String normalize(String raw, int max) {
        String key = raw.trim();
        if (key.length() < MIN || key.length() > max || !KEY.matcher(key).matches()) {
            throw exception(BAD_REQUEST);
        }
        return key;
    }

}
