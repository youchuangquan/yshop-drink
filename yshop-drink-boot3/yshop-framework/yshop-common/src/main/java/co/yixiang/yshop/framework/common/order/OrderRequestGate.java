package co.yixiang.yshop.framework.common.order;

import java.util.function.Function;

/**
 * 同一个用户、同一家店、同一个入口上的稳定键只创建一笔订单。
 * required 为 false 且键为 null 时直接创建：旧客户端兼容，不是防重。
 */
public final class OrderRequestGate {

    private OrderRequestGate() {
    }

    public static <T> T open(String rawKey, boolean required, Function<String, T> find, Function<String, T> create) {
        String key = required ? OrderRequestKeys.require(rawKey) : OrderRequestKeys.legacy(rawKey);
        if (key == null) {
            return create.apply(null);
        }
        T existing = find.apply(key);
        if (existing != null) {
            return existing;
        }
        try {
            return create.apply(key);
        } catch (RuntimeException ex) {
            if (!isDuplicate(ex)) {
                throw ex;
            }
            T winner = find.apply(key);
            if (winner == null) {
                throw ex;
            }
            return winner;
        }
    }

    public static boolean isDuplicate(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String name = current.getClass().getSimpleName();
            if ("DuplicateOrderRequestException".equals(name)
                    || "DuplicateKeyException".equals(name)
                    || "SQLIntegrityConstraintViolationException".equals(name)) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.contains("Duplicate entry")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

}
