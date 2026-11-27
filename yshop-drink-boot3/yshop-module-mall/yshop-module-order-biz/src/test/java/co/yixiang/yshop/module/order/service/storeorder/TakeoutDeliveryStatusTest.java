package co.yixiang.yshop.module.order.service.storeorder;

import co.yixiang.yshop.framework.common.enums.OrderInfoEnum;
import co.yixiang.yshop.framework.common.exception.ServiceException;
import co.yixiang.yshop.module.order.dal.dataobject.storeorder.StoreOrderDO;
import org.junit.jupiter.api.Test;

import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static co.yixiang.yshop.module.order.enums.ErrorCodeConstants.ORDER_STATUS_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TakeoutDeliveryStatusTest {

    private static final long SHOP = 1L;
    private static final String KEY = "delivery-key-01";

    @Test
    void forwardMovesAreOnlyZeroToOneAndOneToTwo() {
        TakeoutDeliveryStatus.Move first = TakeoutDeliveryStatus.decide(snap(0), SHOP, 1, KEY, null);
        assertEquals(TakeoutDeliveryStatus.Effect.APPLY, first.effect());
        assertEquals(0, first.from());
        assertEquals(1, first.to());
        assertEquals(KEY, first.requestKey());

        TakeoutDeliveryStatus.Move second = TakeoutDeliveryStatus.decide(snap(1), SHOP, 2, "delivery-key-02", null);
        assertEquals(TakeoutDeliveryStatus.Effect.APPLY, second.effect());
        assertEquals(1, second.from());
        assertEquals(2, second.to());
    }

    @Test
    void sameKeySameTargetIsReplayAndDoesNotApplyAgain() {
        TakeoutDeliveryStatus.Stamp stamp = new TakeoutDeliveryStatus.Stamp(10L, 1, KEY);
        int writes = 0;
        TakeoutDeliveryStatus.Move replay = TakeoutDeliveryStatus.decide(snap(1), SHOP, 1, KEY, stamp);
        if (replay.effect() == TakeoutDeliveryStatus.Effect.APPLY) {
            writes++;
        }
        assertEquals(TakeoutDeliveryStatus.Effect.REPLAY, replay.effect());
        assertEquals(0, writes);

        ServiceException otherTarget = assertThrows(ServiceException.class,
                () -> TakeoutDeliveryStatus.decide(snap(1), SHOP, 2, KEY, stamp));
        assertEquals(ORDER_STATUS_ERROR.getCode(), otherTarget.getCode());
    }

    @Test
    void missingKeyStillReplaysTheSameTarget() {
        TakeoutDeliveryStatus.Move first = TakeoutDeliveryStatus.decide(snap(0), SHOP, 1, null, null);
        assertEquals(TakeoutDeliveryStatus.Effect.APPLY, first.effect());
        assertNull(first.requestKey());

        int writes = 0;
        TakeoutDeliveryStatus.Move again = TakeoutDeliveryStatus.decide(snap(1), SHOP, 1, null, null);
        TakeoutDeliveryStatus.Move done = TakeoutDeliveryStatus.decide(snap(2), SHOP, 2, null, null);
        if (again.effect() == TakeoutDeliveryStatus.Effect.APPLY) {
            writes++;
        }
        if (done.effect() == TakeoutDeliveryStatus.Effect.APPLY) {
            writes++;
        }
        assertEquals(TakeoutDeliveryStatus.Effect.REPLAY, again.effect());
        assertEquals(TakeoutDeliveryStatus.Effect.REPLAY, done.effect());
        assertEquals(0, writes);
    }

    @Test
    void unpaidNonTakeoutAndIllegalMovesFailClosed() {
        assertStatus(snap(0, 0, "takeout", 0));
        assertStatus(snap(0, null, "takeout", 0));
        assertStatus(new TakeoutDeliveryStatus.Snapshot(10L, SHOP, 1, "takein", 0, 0), 1);
        assertStatus(new TakeoutDeliveryStatus.Snapshot(10L, SHOP, 1, "desk", 0, 0), 1);
        assertStatus(new TakeoutDeliveryStatus.Snapshot(10L, SHOP, 1, null, 0, 0), 1);
        assertStatus(snap(0), 2);
        assertStatus(snap(1), 0);
        assertStatus(snap(2), 1);
        assertStatus(snap(2), 3);
        assertStatus(snap(3), 2);
        assertEquals(1, OrderInfoEnum.REFUND_STATUS_1.getValue());
        assertEquals(2, OrderInfoEnum.REFUND_STATUS_2.getValue());
        assertStatus(new TakeoutDeliveryStatus.Snapshot(10L, SHOP, 1, "takeout", 0, OrderInfoEnum.REFUND_STATUS_1.getValue()), 1);
        assertStatus(new TakeoutDeliveryStatus.Snapshot(10L, SHOP, 1, "takeout", 1, OrderInfoEnum.REFUND_STATUS_2.getValue()), 2);
        assertStatus(new TakeoutDeliveryStatus.Snapshot(10L, SHOP, 1, "takeout", 0, null), 1);
    }

    @Test
    void otherShopIsForbiddenBeforeAnyMove() {
        TakeoutDeliveryStatus.Snapshot other = new TakeoutDeliveryStatus.Snapshot(10L, 2L, 1, "takeout", 0, 0);
        ServiceException denied = assertThrows(ServiceException.class,
                () -> TakeoutDeliveryStatus.decide(other, SHOP, 1, KEY, null));
        assertEquals(FORBIDDEN.getCode(), denied.getCode());
        assertEquals(0, other.status);
    }

    @Test
    void blankOrShortKeyIsRejected() {
        ServiceException blank = assertThrows(ServiceException.class,
                () -> TakeoutDeliveryStatus.decide(snap(0), SHOP, 1, "   ", null));
        ServiceException shortKey = assertThrows(ServiceException.class,
                () -> TakeoutDeliveryStatus.decide(snap(0), SHOP, 1, "short", null));
        assertEquals(BAD_REQUEST.getCode(), blank.getCode());
        assertEquals(BAD_REQUEST.getCode(), shortKey.getCode());
    }

    @Test
    void operationStampUsesCanonicalStatusText() {
        String preparing = TakeoutDeliveryStatus.operationMessage(1, KEY);
        String received = TakeoutDeliveryStatus.operationMessage(2, "delivery-key-02");
        assertTrue(preparing.startsWith(OrderInfoEnum.STATUS_1.getDesc()));
        assertTrue(received.startsWith(OrderInfoEnum.STATUS_2.getDesc()));
        assertEquals(KEY, TakeoutDeliveryStatus.keyOf(preparing));
        assertEquals(Integer.valueOf(2), TakeoutDeliveryStatus.targetOf(received));
        assertEquals("已收货", OrderInfoEnum.STATUS_2.getDesc());
    }

    @Test
    void legacyWritersCannotAdvanceTakeout() {
        assertTrue(TakeoutDeliveryStatus.legacyWriteBlocked("takeout"));
        assertEquals(false, TakeoutDeliveryStatus.legacyWriteBlocked("takein"));
        assertEquals(false, TakeoutDeliveryStatus.legacyWriteBlocked("desk"));
    }

    @Test
    void createOrderDefenseSetsUnpaidAndDefaultStatus() {
        StoreOrderDO created = new StoreOrderDO();
        created.setPaid(OrderInfoEnum.PAY_STATUS_1.getValue());
        created.setStatus(OrderInfoEnum.STATUS_2.getValue());
        AppStoreOrderServiceImpl.initializeFulfillment(created);
        assertEquals(OrderInfoEnum.PAY_STATUS_0.getValue(), created.getPaid());
        assertEquals(OrderInfoEnum.STATUS_0.getValue(), created.getStatus());
    }

    private static void assertStatus(TakeoutDeliveryStatus.Snapshot order) {
        assertStatus(order, 1);
    }

    private static void assertStatus(TakeoutDeliveryStatus.Snapshot order, int target) {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> TakeoutDeliveryStatus.decide(order, SHOP, target, KEY, null));
        assertEquals(ORDER_STATUS_ERROR.getCode(), ex.getCode());
    }

    private static TakeoutDeliveryStatus.Snapshot snap(int status) {
        return snap(status, 1, "takeout", 0);
    }

    private static TakeoutDeliveryStatus.Snapshot snap(int status, Integer paid, String orderType, int refundStatus) {
        return new TakeoutDeliveryStatus.Snapshot(10L, SHOP, paid, orderType, status, refundStatus);
    }

}
