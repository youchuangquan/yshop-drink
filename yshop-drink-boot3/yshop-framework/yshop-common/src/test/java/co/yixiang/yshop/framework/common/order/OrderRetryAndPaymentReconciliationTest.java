package co.yixiang.yshop.framework.common.order;

import co.yixiang.yshop.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderRetryAndPaymentReconciliationTest {

    @Test
    void sameRequestKeyDoesNotCreateASecondOrder() {
        Map<String, String> orders = new HashMap<>();
        AtomicInteger creates = new AtomicInteger();
        Function<String, String> place = key -> OrderRequestGate.open(key, true,
                orders::get,
                normalized -> {
                    if (orders.containsKey(normalized)) {
                        throw new DuplicateOrderRequestException();
                    }
                    creates.incrementAndGet();
                    orders.put(normalized, "S1");
                    return "S1";
                });

        assertEquals("S1", place.apply("order-key-01"));
        assertEquals("S1", place.apply("order-key-01"));
        assertEquals(1, creates.get());
        assertEquals(1, orders.size());
    }

    @Test
    void duplicateInsertReturnsTheFirstOrder() {
        Map<String, String> orders = new HashMap<>();
        orders.put("order-key-01", "S1");
        AtomicInteger finds = new AtomicInteger();
        String orderNo = OrderRequestGate.open("order-key-01", true,
                key -> finds.getAndIncrement() == 0 ? null : orders.get(key),
                key -> {
                    throw new DuplicateOrderRequestException();
                });
        assertEquals("S1", orderNo);
    }

    @Test
    void sameKeyAtAnotherShopIsADifferentOrder() {
        Map<String, String> orders = new HashMap<>();
        AtomicInteger creates = new AtomicInteger();
        String shopA = placeAtShop(orders, creates, 1L, "order-key-01");
        String shopB = placeAtShop(orders, creates, 2L, "order-key-01");
        assertEquals("shop-1", shopA);
        assertEquals("shop-2", shopB);
        assertEquals(shopA, placeAtShop(orders, creates, 1L, "order-key-01"));
        assertEquals(2, creates.get());
    }

    @Test
    void omittedKeyIsNotAntiDuplication() {
        AtomicInteger creates = new AtomicInteger();
        String first = OrderRequestGate.open(null, false, key -> null, key -> "N" + creates.incrementAndGet());
        String second = OrderRequestGate.open(null, false, key -> null, key -> "N" + creates.incrementAndGet());
        assertEquals("N1", first);
        assertEquals("N2", second);
        assertNull(OrderRequestKeys.legacy(null));
    }

    @Test
    void missingOrBlankKeysAreRejectedWhereRequired() {
        AtomicInteger creates = new AtomicInteger();
        ServiceException missing = assertThrows(ServiceException.class,
                () -> OrderRequestGate.open(null, true, key -> null, key -> {
                    creates.incrementAndGet();
                    return "x";
                }));
        ServiceException blank = assertThrows(ServiceException.class,
                () -> OrderRequestGate.open("   ", true, key -> null, key -> {
                    creates.incrementAndGet();
                    return "x";
                }));
        ServiceException legacyBlank = assertThrows(ServiceException.class,
                () -> OrderRequestGate.open("  ", false, key -> null, key -> {
                    creates.incrementAndGet();
                    return "x";
                }));
        assertEquals(BAD_REQUEST.getCode(), missing.getCode());
        assertEquals(BAD_REQUEST.getCode(), blank.getCode());
        assertEquals(BAD_REQUEST.getCode(), legacyBlank.getCode());
        assertEquals(0, creates.get());

        MemBook book = bookWith(new PaymentReconciliation.OrderState(true, false, false));
        assertThrows(ServiceException.class,
                () -> notify(book, null, "yue"));
        assertThrows(ServiceException.class,
                () -> notify(book, "   ", "yue"));
        assertEquals(0, book.paidCount.get());
        assertEquals(0, book.completeCount.get());
        assertFalse(book.orders.load("S1").paid);
    }

    @Test
    void replayedPaymentNoticeDoesNotPayOrCompleteTwice() {
        MemBook book = bookWith(new PaymentReconciliation.OrderState(true, false, false));
        PaymentReconciliation.Result first = notify(book, "notice-key-01", "yue");
        PaymentReconciliation.Result second = notify(book, "notice-key-01", "yue");

        assertEquals(PaymentReconciliation.Disposition.APPLIED, first.disposition());
        assertTrue(first.settled());
        assertEquals(PaymentReconciliation.Disposition.REPLAY, second.disposition());
        assertFalse(second.settled());
        assertEquals(1, book.paidCount.get());
        assertEquals(1, book.completeCount.get());
        assertTrue(book.orders.load("S1").paid);

        PaymentReconciliation.Result otherKey = notify(book, "notice-key-02", "cash");
        assertEquals(PaymentReconciliation.Disposition.REPLAY, otherKey.disposition());
        assertFalse(otherKey.settled());
        assertEquals(1, book.paidCount.get());
        assertEquals(1, book.completeCount.get());
    }

    @Test
    void unconnectedChannelNeverMarksPaid() {
        MemBook book = bookWith(new PaymentReconciliation.OrderState(true, false, false));
        PaymentReconciliation.Result weixin = notify(book, "4200000000000000000000000001", "weixin");
        PaymentReconciliation.Result again = notify(book, "4200000000000000000000000001", "weixin");
        PaymentReconciliation.Result alipay = notify(book, "2024112622001400000000000001", "alipay");

        assertEquals(PaymentReconciliation.Disposition.UNCONNECTED, weixin.disposition());
        assertEquals(PaymentReconciliation.Disposition.UNCONNECTED, again.disposition());
        assertEquals(PaymentReconciliation.Disposition.UNCONNECTED, alipay.disposition());
        assertFalse(weixin.settled());
        assertFalse(alipay.settled());
        assertEquals(0, book.paidCount.get());
        assertEquals(0, book.completeCount.get());
        assertFalse(book.orders.load("S1").paid);
        assertFalse(PayChannels.settles("weixin"));
        assertFalse(PayChannels.settles("alipay"));
        assertNull(PayChannels.localNotifyKey("weixin", "1887329197739737088"));
    }

    @Test
    void latePaymentIsHeldAndDoesNotComplete() {
        MemBook missing = new MemBook();
        PaymentReconciliation.Result held = notify(missing, "notice-key-09", "yue");
        assertEquals(PaymentReconciliation.Disposition.LATE_HELD, held.disposition());
        assertFalse(held.settled());
        assertEquals(1, missing.holds.get());
        assertEquals(0, missing.paidCount.get());
        assertEquals(0, missing.completeCount.get());

        PaymentReconciliation.Result replay = notify(missing, "notice-key-09", "yue");
        assertEquals(PaymentReconciliation.Disposition.LATE_HELD, replay.disposition());
        assertEquals(1, missing.holds.get());
        assertEquals(0, missing.completeCount.get());
    }

    private static String placeAtShop(Map<String, String> orders, AtomicInteger creates, long shopId, String key) {
        String scope = shopId + ":" + key;
        return OrderRequestGate.open(key, true,
                normalized -> orders.get(shopId + ":" + normalized),
                normalized -> {
                    creates.incrementAndGet();
                    String orderNo = "shop-" + shopId;
                    orders.put(scope, orderNo);
                    return orderNo;
                });
    }

    private static MemBook bookWith(PaymentReconciliation.OrderState state) {
        MemBook book = new MemBook();
        book.orders.rows.put("S1", state);
        return book;
    }

    private static PaymentReconciliation.Result notify(MemBook book, String notifyKey, String payType) {
        return PaymentReconciliation.apply(notifyKey, payType, "S1", book.orders, book.notices,
                (orderId, key, type) -> book.holds.incrementAndGet(),
                book.completeCount::incrementAndGet);
    }

    private static final class MemBook {
        private final AtomicInteger paidCount = new AtomicInteger();
        private final AtomicInteger completeCount = new AtomicInteger();
        private final AtomicInteger holds = new AtomicInteger();
        private final MemOrders orders = new MemOrders(paidCount);
        private final MemNotices notices = new MemNotices();
    }

    private static final class MemOrders implements PaymentReconciliation.Orders {
        private final Map<String, PaymentReconciliation.OrderState> rows = new HashMap<>();
        private final AtomicInteger paidCount;

        private MemOrders(AtomicInteger paidCount) {
            this.paidCount = paidCount;
        }

        @Override
        public PaymentReconciliation.OrderState load(String orderId) {
            return rows.get(orderId);
        }

        @Override
        public boolean markPaidIfUnpaid(String orderId, String payType) {
            PaymentReconciliation.OrderState state = rows.get(orderId);
            if (state == null || state.paid || state.completed || !state.payable) {
                return false;
            }
            rows.put(orderId, new PaymentReconciliation.OrderState(false, true, false));
            paidCount.incrementAndGet();
            return true;
        }
    }

    private static final class MemNotices implements PaymentReconciliation.Notices {
        private final Map<String, PaymentReconciliation.Disposition> rows = new HashMap<>();

        @Override
        public PaymentReconciliation.Disposition find(String notifyKey) {
            return rows.get(notifyKey);
        }

        @Override
        public boolean claim(String notifyKey, PaymentReconciliation.Disposition disposition) {
            return rows.putIfAbsent(notifyKey, disposition) == null;
        }

        @Override
        public void replace(String notifyKey, PaymentReconciliation.Disposition disposition) {
            rows.put(notifyKey, disposition);
        }
    }

}
