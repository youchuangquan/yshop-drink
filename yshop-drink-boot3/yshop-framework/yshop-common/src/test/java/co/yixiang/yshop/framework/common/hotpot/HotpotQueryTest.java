package co.yixiang.yshop.framework.common.hotpot;

import co.yixiang.yshop.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotpotQueryTest {

    private static final long SHOP = 1L;
    private static final long OTHER_SHOP = 2L;
    private static final long GUEST = 42L;
    private static final long OTHER_GUEST = 99L;

    @Test
    void customerSlotListReturnsOnlyTheRequestedShop() {
        HotpotFlow.Board board = new HotpotFlow.Board();
        HotpotFlow.Slot mine = HotpotFlow.addSlot(board, SHOP, 4, 7L, "slot-query-01");
        HotpotFlow.addSlot(board, OTHER_SHOP, 2, 7L, "slot-query-02");

        List<HotpotFlow.Slot> rows = HotpotFlow.slotsOf(board, SHOP);

        assertEquals(1, rows.size());
        assertEquals(mine.id, rows.get(0).id);
        assertEquals(SHOP, rows.get(0).shopId);
        assertEquals(4, rows.get(0).capacity);
        for (Field field : HotpotFlow.Slot.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            assertFalse(name.contains("tabletype"));
            assertFalse(name.contains("desktype"));
        }
    }

    @Test
    void staffHoldListShowsCurrentHoldsWithoutExtraIdentity() {
        HotpotFlow.Board board = new HotpotFlow.Board();
        HotpotFlow.Slot slot = HotpotFlow.addSlot(board, SHOP, 3, 7L, "hold-query-s1");
        HotpotFlow.Slot otherSlot = HotpotFlow.addSlot(board, OTHER_SHOP, 2, 7L, "hold-query-s2");
        HotpotFlow.addTable(board, SHOP, "E5", 7L, "hold-query-t1");

        HotpotFlow.Hold waiting = HotpotFlow.reserve(board, SHOP, slot.id, GUEST, "hold-query-01");
        HotpotFlow.Hold seated = HotpotFlow.reserve(board, SHOP, slot.id, OTHER_GUEST, "hold-query-02");
        HotpotFlow.seat(board, SHOP, seated.id, slot.id, OTHER_GUEST, false, "hold-query-02");
        HotpotFlow.Session session = HotpotFlow.open(board, SHOP, slot.id, "E5", OTHER_GUEST, false, "hold-query-02");
        HotpotFlow.Hold cancelled = HotpotFlow.reserve(board, SHOP, slot.id, GUEST, "hold-query-03");
        HotpotFlow.cancel(board, GUEST, cancelled.id);
        HotpotFlow.reserve(board, OTHER_SHOP, otherSlot.id, GUEST, "hold-query-04");

        List<HotpotFlow.StaffHold> rows = HotpotFlow.currentHolds(board, SHOP);

        assertEquals(List.of("id", "slotId", "shopId", "uid", "status", "sessionId"),
                names(HotpotFlow.StaffHold.class));
        assertEquals(2, rows.size());
        HotpotFlow.StaffHold first = rows.get(0);
        assertEquals(waiting.id, first.id());
        assertEquals(slot.id, first.slotId());
        assertEquals(SHOP, first.shopId());
        assertEquals(GUEST, first.uid());
        assertEquals(HotpotFlow.HoldStatus.HELD, first.status());
        assertNull(first.sessionId());
        HotpotFlow.StaffHold second = rows.get(1);
        assertEquals(seated.id, second.id());
        assertEquals(OTHER_GUEST, second.uid());
        assertEquals(HotpotFlow.HoldStatus.SEATED, second.status());
        assertEquals(session.id, second.sessionId());
        assertFalse(names(HotpotFlow.StaffHold.class).contains("requestKey"));
    }

    @Test
    void boundBillReturnsOnlyTheCallerSessionAndDoesNotLeakIdentity() {
        HotpotFlow.Board board = new HotpotFlow.Board();
        HotpotFlow.Slot slot = HotpotFlow.addSlot(board, SHOP, 1, 7L, "bill-query-s1");
        HotpotFlow.Table table = HotpotFlow.addTable(board, SHOP, "F6", 7L, "bill-query-t1");
        HotpotFlow.reserve(board, SHOP, slot.id, GUEST, "bill-query-01");
        HotpotFlow.Session session = HotpotFlow.open(board, SHOP, slot.id, "F6", GUEST, false, "bill-query-01");

        HotpotFlow.BoundBill bill = HotpotFlow.boundBill(board, SHOP, "F6", GUEST);
        assertEquals(List.of("sessionId", "version", "payableFen", "status", "paid", "zeroPayReleased",
                "feesWaived", "pots", "dishes"),
                names(HotpotFlow.BoundBill.class));
        assertEquals(session.id, bill.sessionId());
        assertEquals(session.version, bill.version());
        assertEquals(session.payableFen, bill.payableFen());
        assertEquals("OPEN", bill.status());
        assertFalse(bill.paid());
        assertFalse(bill.zeroPayReleased());
        assertFalse(bill.feesWaived());
        session.feesWaived = true;
        assertTrue(HotpotFlow.boundBill(board, SHOP, "F6", GUEST).feesWaived());
        assertTrue(bill.pots().isEmpty());
        assertTrue(bill.dishes().isEmpty());

        ServiceException stranger = assertThrows(ServiceException.class,
                () -> HotpotFlow.boundBill(board, SHOP, "F6", OTHER_GUEST));
        assertEquals(NOT_FOUND.getCode(), stranger.getCode());
        assertFalse(stranger.getMessage().contains(Long.toString(GUEST)));
        assertFalse(stranger.getMessage().contains(Long.toString(OTHER_GUEST)));

        HotpotFlow.PublicScan scan = HotpotFlow.scan(board, SHOP, "F6");
        assertEquals(List.of("signCode", "occupied"), names(HotpotFlow.PublicScan.class));
        assertEquals("F6", scan.signCode());
        assertTrue(scan.occupied());

        int occupied = slot.occupied;
        ServiceException claim = assertThrows(ServiceException.class,
                () -> HotpotFlow.walkIn(board, SHOP, slot.id, "F6", OTHER_GUEST, false, "bill-query-02"));
        assertEquals(FORBIDDEN.getCode(), claim.getCode());
        assertEquals(GUEST, table.boundUid);
        assertEquals(occupied, slot.occupied);

        ServiceException otherShop = assertThrows(ServiceException.class,
                () -> HotpotFlow.boundBill(board, OTHER_SHOP, "F6", GUEST));
        assertEquals(NOT_FOUND.getCode(), otherShop.getCode());
        assertFalse(otherShop.getMessage().contains(Long.toString(GUEST)));
    }

    private static List<String> names(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName).toList();
    }

}
