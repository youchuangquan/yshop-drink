package co.yixiang.yshop.framework.common.hotpot;

import co.yixiang.yshop.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotpotFlowTest {

    private static final long SHOP = 1L;
    private static final long OTHER_SHOP = 2L;
    private static final long GUEST = 42L;
    private static final long OTHER_GUEST = 99L;

    @Test
    void reservationOccupiesSlotCapacity() {
        HotpotFlow.Board board = board();
        HotpotFlow.Slot slot = slot(board, SHOP, 1, "slot-capacity-1");

        HotpotFlow.Hold first = HotpotFlow.reserve(board, SHOP, slot.id, GUEST, "reserve-key-1");
        assertEquals(HotpotFlow.HoldStatus.HELD, first.status);
        assertEquals(1, slot.occupied);
        assertFalse(hasTableNumber(first));

        HotpotFlow.Hold replay = HotpotFlow.reserve(board, SHOP, slot.id, GUEST, "reserve-key-1");
        assertEquals(first.id, replay.id);
        assertEquals(1, slot.occupied);

        ServiceException full = assertThrows(ServiceException.class,
                () -> HotpotFlow.reserve(board, SHOP, slot.id, OTHER_GUEST, "reserve-key-2"));
        assertEquals(BAD_REQUEST.getCode(), full.getCode());
        assertEquals(1, slot.occupied);
        assertEquals(1, board.holds.size());

        ServiceException stranger = assertThrows(ServiceException.class,
                () -> HotpotFlow.cancel(board, OTHER_GUEST, first.id));
        assertEquals(FORBIDDEN.getCode(), stranger.getCode());
        assertEquals(HotpotFlow.HoldStatus.HELD, first.status);
        assertEquals(1, slot.occupied);

        HotpotFlow.cancel(board, GUEST, first.id);
        assertEquals(HotpotFlow.HoldStatus.CANCELLED, first.status);
        assertEquals(0, slot.occupied);
        HotpotFlow.cancel(board, GUEST, first.id);
        assertEquals(0, slot.occupied);

        HotpotFlow.Hold again = HotpotFlow.reserve(board, SHOP, slot.id, OTHER_GUEST, "reserve-key-2");
        assertEquals(HotpotFlow.HoldStatus.HELD, again.status);
        assertEquals(1, slot.occupied);
    }

    @Test
    void seatingAndOpeningDoNotDoubleCount() {
        HotpotFlow.Board board = board();
        HotpotFlow.Slot slot = slot(board, SHOP, 1, "slot-seat-1");
        HotpotFlow.Table table = table(board, SHOP, "A1", "table-seat-1");
        HotpotFlow.Hold hold = HotpotFlow.reserve(board, SHOP, slot.id, GUEST, "seat-key-1");

        HotpotFlow.Hold seated = HotpotFlow.seat(board, SHOP, hold.id, slot.id, GUEST, false, "seat-key-1");
        assertEquals(HotpotFlow.HoldStatus.SEATED, seated.status);
        assertEquals(1, slot.occupied);

        HotpotFlow.Session session = HotpotFlow.open(board, SHOP, slot.id, "A1", GUEST, false, "seat-key-1");
        assertEquals(slot.id, session.slotId);
        assertEquals(1, slot.occupied);
        assertTrue(table.occupied);
        assertEquals(GUEST, table.boundUid);

        ServiceException full = assertThrows(ServiceException.class,
                () -> HotpotFlow.reserve(board, SHOP, slot.id, OTHER_GUEST, "seat-key-2"));
        assertEquals(BAD_REQUEST.getCode(), full.getCode());
        assertEquals(1, slot.occupied);

        HotpotFlow.Session again = HotpotFlow.open(board, SHOP, slot.id, "A1", GUEST, false, "seat-key-1");
        assertEquals(session.id, again.id);
        assertEquals(1, slot.occupied);
    }

    @Test
    void walkInThenOpenDoesNotDoubleCount() {
        HotpotFlow.Board board = board();
        HotpotFlow.Slot slot = slot(board, SHOP, 1, "slot-walk-1");
        HotpotFlow.Table table = table(board, SHOP, "B2", "table-walk-1");

        HotpotFlow.Hold walked = HotpotFlow.walkIn(board, SHOP, slot.id, "B2", GUEST, false, "walk-key-1");
        assertEquals(HotpotFlow.HoldStatus.SEATED, walked.status);
        assertEquals(1, slot.occupied);
        assertEquals(GUEST, table.boundUid);

        HotpotFlow.Hold replay = HotpotFlow.walkIn(board, SHOP, slot.id, "B2", GUEST, false, "walk-key-1");
        assertEquals(walked.id, replay.id);
        assertEquals(1, slot.occupied);

        HotpotFlow.Session session = HotpotFlow.open(board, SHOP, slot.id, "B2", GUEST, false, "walk-open-1");
        assertEquals(slot.id, session.slotId);
        assertEquals(1, slot.occupied);
    }

    @Test
    void requestKeyIsRequiredAndStuckToOneSlot() {
        HotpotFlow.Board board = board();
        HotpotFlow.Slot first = slot(board, SHOP, 2, "slot-key-a");
        HotpotFlow.Slot second = slot(board, SHOP, 2, "slot-key-b");
        HotpotFlow.Slot otherShopSlot = slot(board, OTHER_SHOP, 2, "slot-key-c");

        for (String missing : new String[]{null, "", "   "}) {
            ServiceException rejected = assertThrows(ServiceException.class,
                    () -> HotpotFlow.reserve(board, SHOP, first.id, GUEST, missing));
            assertEquals(BAD_REQUEST.getCode(), rejected.getCode());
        }
        assertEquals(0, board.holds.size());
        assertEquals(0, first.occupied);

        ServiceException seatBlank = assertThrows(ServiceException.class,
                () -> HotpotFlow.seat(board, SHOP, 1L, first.id, GUEST, false, " "));
        assertEquals(BAD_REQUEST.getCode(), seatBlank.getCode());
        ServiceException walkBlank = assertThrows(ServiceException.class,
                () -> HotpotFlow.walkIn(board, SHOP, first.id, null, GUEST, false, ""));
        assertEquals(BAD_REQUEST.getCode(), walkBlank.getCode());
        assertEquals(0, first.occupied);

        HotpotFlow.Hold hold = HotpotFlow.reserve(board, SHOP, first.id, GUEST, "same-key-01");
        ServiceException otherSlot = assertThrows(ServiceException.class,
                () -> HotpotFlow.reserve(board, SHOP, second.id, GUEST, "same-key-01"));
        assertEquals(BAD_REQUEST.getCode(), otherSlot.getCode());
        assertEquals(1, first.occupied);
        assertEquals(0, second.occupied);
        assertEquals(hold.id, HotpotFlow.reserve(board, SHOP, first.id, GUEST, "same-key-01").id);

        ServiceException seatElsewhere = assertThrows(ServiceException.class,
                () -> HotpotFlow.seat(board, SHOP, hold.id, second.id, GUEST, false, "same-key-01"));
        assertEquals(BAD_REQUEST.getCode(), seatElsewhere.getCode());
        assertEquals(HotpotFlow.HoldStatus.HELD, hold.status);

        ServiceException walkElsewhere = assertThrows(ServiceException.class,
                () -> HotpotFlow.walkIn(board, SHOP, second.id, null, GUEST, false, "same-key-01"));
        assertEquals(BAD_REQUEST.getCode(), walkElsewhere.getCode());
        assertEquals(0, second.occupied);

        HotpotFlow.Hold otherShop = HotpotFlow.reserve(board, OTHER_SHOP, otherShopSlot.id, GUEST, "same-key-01");
        assertEquals(1, otherShopSlot.occupied);
        assertFalse(otherShop.id == hold.id);
        assertEquals(1, first.occupied);
    }

    @Test
    void publicScanExposesOnlySignAndOccupied() {
        HotpotFlow.Board board = board();
        HotpotFlow.Slot slot = slot(board, SHOP, 1, "slot-scan-1");
        HotpotFlow.Table table = table(board, SHOP, "C3", "table-scan-1");
        HotpotFlow.reserve(board, SHOP, slot.id, GUEST, "scan-key-01");
        HotpotFlow.open(board, SHOP, slot.id, "C3", GUEST, false, "scan-key-01");

        HotpotFlow.PublicScan scan = HotpotFlow.scan(board, SHOP, "C3");
        assertEquals("C3", scan.signCode());
        assertTrue(scan.occupied());
        Field[] fields = HotpotFlow.PublicScan.class.getDeclaredFields();
        assertEquals(2, fields.length);
        assertEquals("signCode", fields[0].getName());
        assertEquals("occupied", fields[1].getName());

        int occupied = slot.occupied;
        ServiceException claim = assertThrows(ServiceException.class,
                () -> HotpotFlow.walkIn(board, SHOP, slot.id, "C3", OTHER_GUEST, false, "scan-key-02"));
        assertEquals(FORBIDDEN.getCode(), claim.getCode());
        assertEquals(GUEST, table.boundUid);
        assertEquals(occupied, slot.occupied);
        assertTrue(table.occupied);
    }

    @Test
    void beginSettlementFailsClosedWithoutAChannel() {
        HotpotFlow.Board board = board();
        HotpotFlow.Slot slot = slot(board, SHOP, 1, "slot-pay-1");
        HotpotFlow.Pot pot = HotpotFlow.addPotConfig(board, SHOP, "红锅", 8800, 7L, "pot-key-pay");
        HotpotFlow.reserve(board, SHOP, slot.id, GUEST, "pay-key-001");
        HotpotFlow.Session session = HotpotFlow.open(board, SHOP, slot.id, null, GUEST, false, "pay-key-001");
        HotpotFlow.addPot(board, SHOP, session.id, pot.id, session.version, GUEST, false, "pay-pot-001");
        assertEquals(8800, session.payableFen);
        assertFalse(session.paid);

        ServiceException blocked = assertThrows(ServiceException.class,
                () -> HotpotFlow.beginSettlement(board, GUEST, session.id, session.version, false));
        assertEquals(BAD_REQUEST.getCode(), blocked.getCode());
        assertEquals(HotpotFlow.SessionStatus.OPEN, session.status);
        assertFalse(session.paid);
        assertFalse(session.zeroPayReleased);
        assertEquals(1, slot.occupied);

        for (String channel : new String[]{"weixin", "alipay", "yue", "cash"}) {
            HotpotFlow.Settlement result = HotpotFlow.completeFromTrustedChannel(
                    board, session.id, channel, "notice-" + channel + "-1");
            assertEquals(HotpotFlow.DISPOSITION_UNCONNECTED, result.disposition);
            assertFalse(result.paymentSuccess);
            assertFalse(session.paid);
            assertEquals(HotpotFlow.SessionStatus.OPEN, session.status);
        }
        assertEquals(1, slot.occupied);
    }

    @Test
    void zeroPayReleasesTheSlotPinnedAtOpen() {
        HotpotFlow.Board board = board();
        HotpotFlow.Slot pinned = slot(board, SHOP, 2, "slot-zero-a");
        HotpotFlow.Slot other = slot(board, SHOP, 4, "slot-zero-b");
        other.occupied = 3;
        HotpotFlow.Table table = table(board, SHOP, "D4", "table-zero-1");
        HotpotFlow.Hold hold = HotpotFlow.reserve(board, SHOP, pinned.id, GUEST, "zero-key-01");
        HotpotFlow.Session session = HotpotFlow.open(board, SHOP, pinned.id, "D4", GUEST, false, "zero-key-01");
        assertEquals(0, session.payableFen);
        assertEquals(pinned.id, session.slotId);
        hold.slotId = other.id;

        HotpotFlow.Settlement settlement = HotpotFlow.beginSettlement(board, GUEST, session.id, session.version, false);
        assertFalse(settlement.paymentSuccess);
        assertTrue(settlement.zeroPayReleased);
        assertEquals(HotpotFlow.DISPOSITION_ZERO_PAY, settlement.disposition);
        assertFalse(session.paid);
        assertEquals(HotpotFlow.SessionStatus.RELEASED, session.status);
        assertEquals(0, pinned.occupied);
        assertEquals(3, other.occupied);
        assertFalse(table.occupied);
        assertEquals(null, table.boundUid);

        HotpotFlow.Settlement replay = HotpotFlow.beginSettlement(board, GUEST, session.id, session.version, false);
        assertFalse(replay.paymentSuccess);
        assertEquals(0, pinned.occupied);
        assertEquals(3, other.occupied);
    }

    @Test
    void openingWithoutASlotDoesNotInventOneAndFullSlotStaysFull() {
        HotpotFlow.Board board = board();
        int before = board.slots.size();
        ServiceException missing = assertThrows(ServiceException.class,
                () -> HotpotFlow.open(board, SHOP, 999L, null, GUEST, false, "open-missing1"));
        assertEquals(BAD_REQUEST.getCode(), missing.getCode());
        assertEquals(before, board.slots.size());

        HotpotFlow.Slot slot = slot(board, SHOP, 1, "slot-full-01");
        HotpotFlow.reserve(board, SHOP, slot.id, GUEST, "full-key-001");
        ServiceException full = assertThrows(ServiceException.class,
                () -> HotpotFlow.open(board, SHOP, slot.id, null, OTHER_GUEST, false, "full-key-002"));
        assertEquals(BAD_REQUEST.getCode(), full.getCode());
        assertEquals(1, slot.occupied);
        assertEquals(1, board.holds.size());
    }

    @Test
    void emptyPotListDoesNotInventAPrice() {
        HotpotFlow.Board board = board();
        HotpotFlow.Slot slot = slot(board, SHOP, 1, "slot-pot-01");
        HotpotFlow.reserve(board, SHOP, slot.id, GUEST, "pot-key-hold");
        HotpotFlow.Session session = HotpotFlow.open(board, SHOP, slot.id, null, GUEST, false, "pot-key-hold");

        ServiceException missing = assertThrows(ServiceException.class,
                () -> HotpotFlow.addPot(board, SHOP, session.id, 5L, session.version, GUEST, false, "pot-key-add1"));
        assertEquals(BAD_REQUEST.getCode(), missing.getCode());
        assertTrue(session.pots.isEmpty());
        assertEquals(0, session.payableFen);

        ServiceException negative = assertThrows(ServiceException.class,
                () -> HotpotFlow.addPotConfig(board, SHOP, "清汤锅", -1, 7L, "pot-key-neg1"));
        assertEquals(BAD_REQUEST.getCode(), negative.getCode());
        assertTrue(board.pots.isEmpty());

        HotpotFlow.Pot pot = HotpotFlow.addPotConfig(board, SHOP, "清汤锅", 0, 7L, "pot-key-zero");
        HotpotFlow.addPot(board, SHOP, session.id, pot.id, session.version, GUEST, false, "pot-key-add2");
        assertEquals(0, session.payableFen);
        assertEquals(0, session.pots.get(0).priceFen);

        HotpotFlow.Pot foreign = HotpotFlow.addPotConfig(board, OTHER_SHOP, "别店锅", 100, 7L, "pot-key-fore");
        ServiceException cross = assertThrows(ServiceException.class,
                () -> HotpotFlow.addPot(board, SHOP, session.id, foreign.id, session.version, GUEST, false, "pot-key-add3"));
        assertEquals(FORBIDDEN.getCode(), cross.getCode());
        assertEquals(1, session.pots.size());
    }

    @Test
    void lateAutoCancelStaysOffAndThereIsNoSlotCampaign() {
        HotpotFlow.Board board = board();
        HotpotFlow.Slot slot = slot(board, SHOP, 1, "slot-late-01");
        HotpotFlow.Hold hold = HotpotFlow.reserve(board, SHOP, slot.id, GUEST, "late-key-001");
        assertFalse(HotpotFlow.lateAutoCancels());
        assertEquals(0, HotpotFlow.sweep(board));
        assertEquals(HotpotFlow.HoldStatus.HELD, hold.status);
        assertEquals(1, slot.occupied);

        for (Method method : HotpotFlow.class.getDeclaredMethods()) {
            assertFalse(method.getName().toLowerCase().contains("replaceslot"));
        }
        for (Class<?> type : new Class<?>[]{
                HotpotFlow.Slot.class, HotpotFlow.Hold.class, HotpotFlow.Table.class, HotpotFlow.Session.class}) {
            for (Field field : type.getDeclaredFields()) {
                String name = field.getName().toLowerCase();
                assertFalse(name.contains("duration"));
                assertFalse(name.contains("stay"));
                assertFalse(name.contains("tabletype"));
                assertFalse(name.contains("desktype"));
            }
        }
    }

    @Test
    void versionMismatchDoesNotReleaseAndOtherShopSlotIsRejected() {
        HotpotFlow.Board board = board();
        HotpotFlow.Slot slot = slot(board, SHOP, 1, "slot-ver-001");
        HotpotFlow.Slot foreign = slot(board, OTHER_SHOP, 1, "slot-ver-002");
        HotpotFlow.reserve(board, SHOP, slot.id, GUEST, "ver-key-0001");
        HotpotFlow.Session session = HotpotFlow.open(board, SHOP, slot.id, null, GUEST, false, "ver-key-0001");

        ServiceException stale = assertThrows(ServiceException.class,
                () -> HotpotFlow.beginSettlement(board, GUEST, session.id, session.version + 1, false));
        assertEquals(BAD_REQUEST.getCode(), stale.getCode());
        assertEquals(HotpotFlow.SessionStatus.OPEN, session.status);
        assertEquals(1, slot.occupied);

        ServiceException cross = assertThrows(ServiceException.class,
                () -> HotpotFlow.reserve(board, SHOP, foreign.id, GUEST, "ver-key-0002"));
        assertEquals(FORBIDDEN.getCode(), cross.getCode());
        assertEquals(0, foreign.occupied);
    }

    private static HotpotFlow.Board board() {
        return new HotpotFlow.Board();
    }

    private static HotpotFlow.Slot slot(HotpotFlow.Board board, long shopId, int capacity, String key) {
        return HotpotFlow.addSlot(board, shopId, capacity, 7L, key);
    }

    private static HotpotFlow.Table table(HotpotFlow.Board board, long shopId, String sign, String key) {
        return HotpotFlow.addTable(board, shopId, sign, 7L, key);
    }

    private static boolean hasTableNumber(HotpotFlow.Hold hold) {
        for (Field field : hold.getClass().getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            if (name.contains("table") || name.contains("desk")) {
                return true;
            }
        }
        return false;
    }

}
