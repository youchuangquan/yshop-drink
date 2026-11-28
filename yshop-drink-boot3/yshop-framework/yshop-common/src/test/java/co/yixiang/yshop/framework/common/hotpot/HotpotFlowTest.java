package co.yixiang.yshop.framework.common.hotpot;

import co.yixiang.yshop.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotpotFlowTest {

    private static final long SHOP = 1L;
    private static final long OTHER_SHOP = 2L;
    private static final long GUEST = 42L;
    private static final long OTHER_GUEST = 99L;
    private static final long STAFF = 7L;
    private static final long BEEF = 501L;
    private static final long TOFU = 502L;
    private static final int POT_FEN = 3577;

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
    void staffSettlesACustomerOwnedZeroBillInThisShop() {
        HotpotFlow.Board board = board();
        HotpotFlow.Slot slot = slot(board, SHOP, 1, "stf-slot-zero");
        HotpotFlow.Table table = table(board, SHOP, "Z1", "stf-table-zero");
        HotpotFlow.reserve(board, SHOP, slot.id, GUEST, "stf-hold-zero");
        HotpotFlow.Session session = HotpotFlow.open(board, SHOP, slot.id, "Z1", GUEST, false, "stf-hold-zero");
        assertEquals(GUEST, session.ownerUid);
        assertEquals(0, session.payableFen);

        ServiceException otherShop = assertThrows(ServiceException.class,
                () -> HotpotFlow.beginSettlement(board, STAFF, session.id, session.version, true, OTHER_SHOP));
        assertEquals(FORBIDDEN.getCode(), otherShop.getCode());
        assertEquals(HotpotFlow.SessionStatus.OPEN, session.status);
        assertFalse(session.paid);
        assertFalse(session.zeroPayReleased);
        assertEquals(1, slot.occupied);
        assertTrue(table.occupied);

        ServiceException noShop = assertThrows(ServiceException.class,
                () -> HotpotFlow.beginSettlement(board, STAFF, session.id, session.version, true));
        assertEquals(FORBIDDEN.getCode(), noShop.getCode());
        assertEquals(HotpotFlow.SessionStatus.OPEN, session.status);
        assertFalse(session.paid);

        ServiceException stranger = assertThrows(ServiceException.class,
                () -> HotpotFlow.beginSettlement(board, OTHER_GUEST, session.id, session.version, false));
        assertEquals(FORBIDDEN.getCode(), stranger.getCode());
        assertEquals(HotpotFlow.SessionStatus.OPEN, session.status);

        ServiceException missing = assertThrows(ServiceException.class,
                () -> HotpotFlow.beginSettlement(board, STAFF, 99999L, session.version, true, SHOP));
        assertEquals(NOT_FOUND.getCode(), missing.getCode());

        HotpotFlow.Settlement settlement = HotpotFlow.beginSettlement(board, STAFF, session.id, session.version, true, SHOP);
        assertFalse(settlement.paymentSuccess);
        assertTrue(settlement.zeroPayReleased);
        assertEquals(HotpotFlow.DISPOSITION_ZERO_PAY, settlement.disposition);
        assertFalse(session.paid);
        assertEquals(HotpotFlow.SessionStatus.RELEASED, session.status);
        assertEquals(0, slot.occupied);
        assertFalse(table.occupied);

        HotpotFlow.Settlement replay = HotpotFlow.beginSettlement(board, STAFF, session.id, session.version, true, SHOP);
        assertFalse(replay.paymentSuccess);
        assertTrue(replay.zeroPayReleased);
        assertEquals(HotpotFlow.DISPOSITION_ZERO_PAY, replay.disposition);
        assertFalse(session.paid);
        assertEquals(0, slot.occupied);
    }

    @Test
    void staffSettlementFailsClosedWhenPayableIsPositive() {
        HotpotFlow.Board board = board();
        HotpotFlow.Slot slot = slot(board, SHOP, 1, "stf-slot-pay");
        HotpotFlow.reserve(board, SHOP, slot.id, GUEST, "stf-hold-pay");
        HotpotFlow.Session session = HotpotFlow.open(board, SHOP, slot.id, null, GUEST, false, "stf-hold-pay");
        HotpotFlow.Pot pot = HotpotFlow.addPotConfig(board, SHOP, "红锅", POT_FEN, STAFF, "stf-pot-pay");
        HotpotFlow.addPot(board, SHOP, session.id, pot.id, session.version, GUEST, false, "stf-pot-add");
        assertTrue(session.payableFen > 0);

        ServiceException blocked = assertThrows(ServiceException.class,
                () -> HotpotFlow.beginSettlement(board, STAFF, session.id, session.version, true, SHOP));
        assertEquals(BAD_REQUEST.getCode(), blocked.getCode());
        assertEquals("收款渠道未接通", blocked.getMessage());
        assertFalse(session.paid);
        assertFalse(session.zeroPayReleased);
        assertEquals(HotpotFlow.SessionStatus.OPEN, session.status);
        assertEquals(1, slot.occupied);
        assertEquals(POT_FEN, session.payableFen);
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

    @Test
    void staffCanRegisterServedQtyWithoutPassingTheOriginal() {
        HotpotFlow.Board board = board();
        HotpotFlow.Slot slot = slot(board, SHOP, 2, "srv-slot-cap1");
        table(board, SHOP, "S1", "srv-table-cap");
        menu(board, SHOP, BEEF, "鲜牛肉", 1800);
        HotpotFlow.Session session = HotpotFlow.orderDish(board, SHOP, slot.id, "S1", GUEST, BEEF, 2, null,
                "srv-dish-cap1");
        HotpotFlow.SessionDish line = session.dishes.get(0);
        assertEquals(3600, session.payableFen);

        HotpotFlow.serve(board, SHOP, session.id, session.version, true, "srv-serve-cap1",
                List.of(new HotpotFlow.LineQty(line.id, 2)));
        assertEquals(2, line.quantity);
        assertEquals(2, line.servedQty);
        assertEquals(0, line.refundedQty);
        assertEquals(0, line.refundableQty());
        assertEquals(3600, session.payableFen);
        assertEquals(2, session.version);

        int version = session.version;
        ServiceException over = assertThrows(ServiceException.class,
                () -> HotpotFlow.serve(board, SHOP, session.id, version, true, "srv-serve-over",
                        List.of(new HotpotFlow.LineQty(line.id, 1))));
        assertEquals(BAD_REQUEST.getCode(), over.getCode());
        assertEquals(2, line.servedQty);
        assertEquals(2, line.quantity);
        assertEquals(version, session.version);

        ServiceException stale = assertThrows(ServiceException.class,
                () -> HotpotFlow.serve(board, SHOP, session.id, version + 3, true, "srv-serve-stale",
                        List.of(new HotpotFlow.LineQty(line.id, 1))));
        assertEquals(BAD_REQUEST.getCode(), stale.getCode());
        assertEquals(2, line.servedQty);
    }

    @Test
    void onlyTheUnservedQtyCanBeRefunded() {
        HotpotFlow.Board board = board();
        HotpotFlow.Slot slot = slot(board, SHOP, 2, "srv-slot-part");
        HotpotFlow.Table table = table(board, SHOP, "S2", "srv-table-part");
        menu(board, SHOP, BEEF, "鲜牛肉", 1000);
        HotpotFlow.Session session = HotpotFlow.orderDish(board, SHOP, slot.id, "S2", GUEST, BEEF, 4, null,
                "srv-dish-part");
        HotpotFlow.Pot pot = HotpotFlow.addPotConfig(board, SHOP, "红锅", POT_FEN, 7L, "srv-pot-part1");
        HotpotFlow.addPot(board, SHOP, session.id, pot.id, session.version, GUEST, false, "srv-pot-part2");
        HotpotFlow.SessionDish line = session.dishes.get(0);
        assertEquals(4000 + POT_FEN, session.payableFen);

        HotpotFlow.serve(board, SHOP, session.id, session.version, true, "srv-serve-part",
                List.of(new HotpotFlow.LineQty(line.id, 1)));
        assertEquals(1, line.servedQty);
        assertEquals(3, line.refundableQty());
        assertEquals(4000 + POT_FEN, session.payableFen);
        assertEquals(4, line.quantity);

        int version = session.version;
        ServiceException tooMuch = assertThrows(ServiceException.class,
                () -> HotpotFlow.refund(board, SHOP, session.id, version, true, "srv-refund-too",
                        List.of(new HotpotFlow.LineQty(line.id, 4))));
        assertEquals(BAD_REQUEST.getCode(), tooMuch.getCode());
        assertEquals(0, line.refundedQty);
        assertEquals(1, line.servedQty);
        assertEquals(version, session.version);
        assertFalse(session.feesWaived);

        HotpotFlow.Settlement kept = HotpotFlow.refund(board, SHOP, session.id, version, true, "srv-refund-rest",
                List.of(new HotpotFlow.LineQty(line.id, 3)));
        assertEquals(3, line.refundedQty);
        assertEquals(1, line.servedQty);
        assertEquals(0, line.refundableQty());
        assertEquals(4, line.quantity);
        assertEquals(1000 + POT_FEN, session.payableFen);
        assertEquals(POT_FEN, session.pots.get(0).priceFen);
        assertFalse(session.feesWaived);
        assertFalse(session.zeroPayReleased);
        assertFalse(session.paid);
        assertFalse(kept.paymentSuccess);
        assertNull(kept.disposition);
        assertEquals(HotpotFlow.SessionStatus.OPEN, session.status);
        assertTrue(table.occupied);
        assertEquals(1, slot.occupied);
    }

    @Test
    void customersCannotServeOrRefund() {
        HotpotFlow.Board board = board();
        HotpotFlow.Slot slot = slot(board, SHOP, 1, "srv-slot-cust");
        table(board, SHOP, "S3", "srv-table-cust");
        menu(board, SHOP, BEEF, "鲜牛肉", 1800);
        HotpotFlow.Session session = HotpotFlow.orderDish(board, SHOP, slot.id, "S3", GUEST, BEEF, 1, null,
                "srv-dish-cust");
        HotpotFlow.SessionDish line = session.dishes.get(0);
        int requests = board.requests.size();

        ServiceException serve = assertThrows(ServiceException.class,
                () -> HotpotFlow.serve(board, SHOP, session.id, session.version, false, "srv-serve-cust",
                        List.of(new HotpotFlow.LineQty(line.id, 1))));
        assertEquals(FORBIDDEN.getCode(), serve.getCode());
        ServiceException refund = assertThrows(ServiceException.class,
                () -> HotpotFlow.refund(board, SHOP, session.id, session.version, false, "srv-refund-cus",
                        List.of(new HotpotFlow.LineQty(line.id, 1))));
        assertEquals(FORBIDDEN.getCode(), refund.getCode());
        assertEquals(0, line.servedQty);
        assertEquals(0, line.refundedQty);
        assertEquals(1, line.quantity);
        assertEquals(requests, board.requests.size());
        assertEquals(HotpotFlow.SessionStatus.OPEN, session.status);
    }

    @Test
    void refundingEveryLineWaivesThePotAndReleasesWithoutAPayment() {
        HotpotFlow.Board board = board();
        HotpotFlow.Slot slot = slot(board, SHOP, 2, "srv-slot-zero");
        HotpotFlow.Table table = table(board, SHOP, "S4", "srv-table-zero");
        menu(board, SHOP, BEEF, "鲜牛肉", 1800);
        menu(board, SHOP, TOFU, "豆腐", 600);
        HotpotFlow.Session session = HotpotFlow.orderDish(board, SHOP, slot.id, "S4", GUEST, BEEF, 1, null,
                "srv-dish-zero1");
        HotpotFlow.Hold hold = board.holds.get(session.holdId);
        HotpotFlow.orderDish(board, SHOP, slot.id, "S4", GUEST, TOFU, 2, session.version, "srv-dish-zero2");
        HotpotFlow.Pot pot = HotpotFlow.addPotConfig(board, SHOP, "红锅", POT_FEN, 7L, "srv-pot-zero1");
        HotpotFlow.addPot(board, SHOP, session.id, pot.id, session.version, GUEST, false, "srv-pot-zero2");
        assertEquals(1800 + 1200 + POT_FEN, session.payableFen);
        HotpotFlow.SessionDish beef = session.dishes.get(0);
        HotpotFlow.SessionDish tofu = session.dishes.get(1);
        List<HotpotFlow.LineQty> forward = List.of(new HotpotFlow.LineQty(beef.id, 1),
                new HotpotFlow.LineQty(tofu.id, 2));
        List<HotpotFlow.LineQty> reversed = List.of(new HotpotFlow.LineQty(tofu.id, 2),
                new HotpotFlow.LineQty(beef.id, 1));

        HotpotFlow.Settlement cleared = HotpotFlow.refund(board, SHOP, session.id, session.version, true,
                "srv-refund-all", forward);
        assertEquals(1, beef.quantity);
        assertEquals(1, beef.refundedQty);
        assertEquals(0, beef.servedQty);
        assertEquals(2, tofu.refundedQty);
        assertEquals(0, tofu.servedQty);
        assertTrue(session.feesWaived);
        assertEquals(1, session.pots.size());
        assertEquals(POT_FEN, session.pots.get(0).priceFen);
        assertEquals(pot.id, session.pots.get(0).potId);
        assertEquals(0, session.payableFen);
        assertFalse(session.paid);
        assertFalse(cleared.paymentSuccess);
        assertTrue(cleared.zeroPayReleased);
        assertEquals(HotpotFlow.DISPOSITION_ZERO_PAY, cleared.disposition);
        assertEquals(HotpotFlow.SessionStatus.RELEASED, session.status);
        assertEquals(HotpotFlow.HoldStatus.RELEASED, hold.status);
        assertEquals(0, slot.occupied);
        assertFalse(table.occupied);
        assertNull(table.boundUid);
        assertNull(table.sessionId);
        assertEquals(0, session.companionFeeFen);

        int version = session.version;
        int requests = board.requests.size();
        HotpotFlow.Settlement replay = HotpotFlow.refund(board, SHOP, session.id, version, true, "srv-refund-all",
                reversed);
        assertFalse(replay.paymentSuccess);
        assertTrue(replay.zeroPayReleased);
        assertEquals(HotpotFlow.DISPOSITION_ZERO_PAY, replay.disposition);
        assertEquals(version, session.version);
        assertEquals(0, slot.occupied);
        assertEquals(requests, board.requests.size());
        assertFalse(session.paid);

        ServiceException again = assertThrows(ServiceException.class,
                () -> HotpotFlow.serve(board, SHOP, session.id, version, true, "srv-serve-after",
                        List.of(new HotpotFlow.LineQty(beef.id, 1))));
        assertEquals(BAD_REQUEST.getCode(), again.getCode());
        assertEquals(0, beef.servedQty);
        assertEquals(0, slot.occupied);
    }

    @Test
    void aPositiveCompanionSnapshotIsWaivedWhenEveryDishIsRefunded() {
        HotpotFlow.Board board = board();
        HotpotFlow.Slot slot = slot(board, SHOP, 1, "cmp-slot-zero");
        HotpotFlow.Table table = table(board, SHOP, "Z2", "cmp-table-zero");
        menu(board, SHOP, BEEF, "鲜牛肉", 1800);
        HotpotFlow.Session session = HotpotFlow.orderDish(board, SHOP, slot.id, "Z2", GUEST, BEEF, 1, null,
                "cmp-dish-zero");
        assertEquals(0, session.companionFeeFen);
        // 测试直接写下快照。这不是按人配置的陪客费，也不代表产品已经接受了那一项。
        session.companionFeeFen = 640;
        HotpotFlow.Pot pot = HotpotFlow.addPotConfig(board, SHOP, "红锅", POT_FEN, STAFF, "cmp-pot-zero");
        HotpotFlow.addPot(board, SHOP, session.id, pot.id, session.version, GUEST, false, "cmp-pot-add");
        assertEquals(1800 + POT_FEN + 640, session.payableFen);
        assertFalse(session.feesWaived);
        HotpotFlow.SessionDish line = session.dishes.get(0);

        HotpotFlow.Settlement cleared = HotpotFlow.refund(board, SHOP, session.id, session.version, true,
                "cmp-refund-all", List.of(new HotpotFlow.LineQty(line.id, 1)));
        assertEquals(1, line.refundedQty);
        assertEquals(0, line.servedQty);
        assertTrue(session.feesWaived);
        assertEquals(640, session.companionFeeFen);
        assertEquals(POT_FEN, session.pots.get(0).priceFen);
        assertEquals(0, session.payableFen);
        assertFalse(session.paid);
        assertFalse(cleared.paymentSuccess);
        assertTrue(cleared.zeroPayReleased);
        assertEquals(HotpotFlow.DISPOSITION_ZERO_PAY, cleared.disposition);
        assertEquals(HotpotFlow.SessionStatus.RELEASED, session.status);
        assertEquals(0, slot.occupied);
        assertFalse(table.occupied);
    }

    @Test
    void aServedLineBlocksEmptyingTheTable() {
        HotpotFlow.Board board = board();
        HotpotFlow.Slot slot = slot(board, SHOP, 2, "srv-slot-block");
        HotpotFlow.Table table = table(board, SHOP, "S5", "srv-table-block");
        menu(board, SHOP, BEEF, "鲜牛肉", 1000);
        menu(board, SHOP, TOFU, "豆腐", 500);
        HotpotFlow.Session session = HotpotFlow.orderDish(board, SHOP, slot.id, "S5", GUEST, BEEF, 2, null,
                "srv-dish-block1");
        HotpotFlow.orderDish(board, SHOP, slot.id, "S5", GUEST, TOFU, 1, session.version, "srv-dish-block2");
        HotpotFlow.Pot pot = HotpotFlow.addPotConfig(board, SHOP, "红锅", POT_FEN, 7L, "srv-pot-block1");
        HotpotFlow.addPot(board, SHOP, session.id, pot.id, session.version, GUEST, false, "srv-pot-block2");
        HotpotFlow.SessionDish beef = session.dishes.get(0);
        HotpotFlow.SessionDish tofu = session.dishes.get(1);

        HotpotFlow.serve(board, SHOP, session.id, session.version, true, "srv-serve-block",
                List.of(new HotpotFlow.LineQty(beef.id, 2)));
        HotpotFlow.Settlement kept = HotpotFlow.refund(board, SHOP, session.id, session.version, true,
                "srv-refund-tofu", List.of(new HotpotFlow.LineQty(tofu.id, 1)));
        assertEquals(2, beef.servedQty);
        assertEquals(0, beef.refundedQty);
        assertEquals(1, tofu.refundedQty);
        assertEquals(0, tofu.servedQty);
        assertEquals(2000 + POT_FEN, session.payableFen);
        assertFalse(session.feesWaived);
        assertFalse(session.zeroPayReleased);
        assertFalse(session.paid);
        assertFalse(kept.paymentSuccess);
        assertNull(kept.disposition);
        assertEquals(HotpotFlow.SessionStatus.OPEN, session.status);
        assertTrue(table.occupied);
        assertEquals(1, slot.occupied);
        assertEquals(1, session.pots.size());

        ServiceException empty = assertThrows(ServiceException.class,
                () -> HotpotFlow.refund(board, SHOP, session.id, session.version, true, "srv-refund-beef",
                        List.of(new HotpotFlow.LineQty(beef.id, 2))));
        assertEquals(BAD_REQUEST.getCode(), empty.getCode());
        assertEquals(0, beef.refundedQty);
        assertTrue(table.occupied);
        assertFalse(session.feesWaived);
    }

    @Test
    void theSameRequestKeyAppliesOnceAndADifferentStampIsRejected() {
        HotpotFlow.Board board = board();
        HotpotFlow.Slot slot = slot(board, SHOP, 2, "srv-slot-idem");
        table(board, SHOP, "S6", "srv-table-idem");
        menu(board, SHOP, BEEF, "鲜牛肉", 1000);
        menu(board, SHOP, TOFU, "豆腐", 500);
        HotpotFlow.Session session = HotpotFlow.orderDish(board, SHOP, slot.id, "S6", GUEST, BEEF, 2, null,
                "srv-dish-idem1");
        HotpotFlow.orderDish(board, SHOP, slot.id, "S6", GUEST, TOFU, 2, session.version, "srv-dish-idem2");
        HotpotFlow.SessionDish beef = session.dishes.get(0);
        HotpotFlow.SessionDish tofu = session.dishes.get(1);
        List<HotpotFlow.LineQty> forward = List.of(new HotpotFlow.LineQty(beef.id, 1),
                new HotpotFlow.LineQty(tofu.id, 1));
        List<HotpotFlow.LineQty> reversed = List.of(new HotpotFlow.LineQty(tofu.id, 1),
                new HotpotFlow.LineQty(beef.id, 1));

        HotpotFlow.serve(board, SHOP, session.id, session.version, true, "srv-serve-idem", forward);
        int version = session.version;
        int requests = board.requests.size();
        HotpotFlow.serve(board, SHOP, session.id, 1, true, "srv-serve-idem", reversed);
        assertEquals(1, beef.servedQty);
        assertEquals(1, tofu.servedQty);
        assertEquals(version, session.version);
        assertEquals(requests, board.requests.size());

        ServiceException changed = assertThrows(ServiceException.class,
                () -> HotpotFlow.serve(board, SHOP, session.id, version, true, "srv-serve-idem",
                        List.of(new HotpotFlow.LineQty(beef.id, 2))));
        assertEquals(BAD_REQUEST.getCode(), changed.getCode());
        assertEquals(1, beef.servedQty);

        ServiceException reused = assertThrows(ServiceException.class,
                () -> HotpotFlow.refund(board, SHOP, session.id, version, true, "srv-serve-idem",
                        List.of(new HotpotFlow.LineQty(beef.id, 1))));
        assertEquals(BAD_REQUEST.getCode(), reused.getCode());
        assertEquals(0, beef.refundedQty);
        assertEquals(requests, board.requests.size());

        HotpotFlow.refund(board, SHOP, session.id, version, true, "srv-refund-idem",
                List.of(new HotpotFlow.LineQty(beef.id, 1)));
        int refundedVersion = session.version;
        int refundedRequests = board.requests.size();
        HotpotFlow.refund(board, SHOP, session.id, refundedVersion, true, "srv-refund-idem",
                List.of(new HotpotFlow.LineQty(beef.id, 1)));
        assertEquals(1, beef.refundedQty);
        assertEquals(1, beef.servedQty);
        assertEquals(refundedVersion, session.version);
        assertEquals(refundedRequests, board.requests.size());
    }

    @Test
    void anotherShopsStaffCannotServeOrRefund() {
        HotpotFlow.Board board = board();
        HotpotFlow.Slot slot = slot(board, SHOP, 1, "srv-slot-shop");
        table(board, SHOP, "S7", "srv-table-shop");
        menu(board, SHOP, BEEF, "鲜牛肉", 1800);
        HotpotFlow.Session session = HotpotFlow.orderDish(board, SHOP, slot.id, "S7", GUEST, BEEF, 1, null,
                "srv-dish-shop");
        HotpotFlow.SessionDish line = session.dishes.get(0);

        ServiceException serve = assertThrows(ServiceException.class,
                () -> HotpotFlow.serve(board, OTHER_SHOP, session.id, session.version, true, "srv-serve-shop",
                        List.of(new HotpotFlow.LineQty(line.id, 1))));
        assertEquals(FORBIDDEN.getCode(), serve.getCode());
        ServiceException refund = assertThrows(ServiceException.class,
                () -> HotpotFlow.refund(board, OTHER_SHOP, session.id, session.version, true, "srv-refund-shop",
                        List.of(new HotpotFlow.LineQty(line.id, 1))));
        assertEquals(FORBIDDEN.getCode(), refund.getCode());
        assertEquals(0, line.servedQty);
        assertEquals(0, line.refundedQty);
        assertEquals(HotpotFlow.SessionStatus.OPEN, session.status);
    }

    @Test
    void paidOrReleasedSessionsRejectServeAndRefund() {
        HotpotFlow.Board board = board();
        HotpotFlow.Slot slot = slot(board, SHOP, 1, "srv-slot-paid");
        table(board, SHOP, "S8", "srv-table-paid");
        menu(board, SHOP, BEEF, "鲜牛肉", 1800);
        HotpotFlow.Session session = HotpotFlow.orderDish(board, SHOP, slot.id, "S8", GUEST, BEEF, 1, null,
                "srv-dish-paid");
        HotpotFlow.SessionDish line = session.dishes.get(0);
        session.paid = true;

        ServiceException paidServe = assertThrows(ServiceException.class,
                () -> HotpotFlow.serve(board, SHOP, session.id, session.version, true, "srv-serve-paid",
                        List.of(new HotpotFlow.LineQty(line.id, 1))));
        assertEquals(BAD_REQUEST.getCode(), paidServe.getCode());
        ServiceException paidRefund = assertThrows(ServiceException.class,
                () -> HotpotFlow.refund(board, SHOP, session.id, session.version, true, "srv-refund-paid",
                        List.of(new HotpotFlow.LineQty(line.id, 1))));
        assertEquals(BAD_REQUEST.getCode(), paidRefund.getCode());
        assertEquals(0, line.servedQty);
        assertEquals(0, line.refundedQty);
        assertTrue(session.paid);
        assertEquals(HotpotFlow.SessionStatus.OPEN, session.status);

        session.paid = false;
        session.zeroPayReleased = true;
        session.status = HotpotFlow.SessionStatus.RELEASED;
        ServiceException released = assertThrows(ServiceException.class,
                () -> HotpotFlow.serve(board, SHOP, session.id, session.version, true, "srv-serve-rel",
                        List.of(new HotpotFlow.LineQty(line.id, 1))));
        assertEquals(BAD_REQUEST.getCode(), released.getCode());
        assertEquals(0, line.servedQty);
        assertEquals(HotpotFlow.SessionStatus.RELEASED, session.status);
    }

    @Test
    void aSessionTakesOnlyOnePotFromTheShopConfig() {
        HotpotFlow.Board board = board();
        HotpotFlow.Slot slot = slot(board, SHOP, 1, "srv-slot-onepot");
        HotpotFlow.reserve(board, SHOP, slot.id, GUEST, "srv-hold-onepot");
        HotpotFlow.Session session = HotpotFlow.open(board, SHOP, slot.id, null, GUEST, false, "srv-open-onepot");
        HotpotFlow.Pot first = HotpotFlow.addPotConfig(board, SHOP, "红锅", POT_FEN, 7L, "srv-pot-one-a");
        HotpotFlow.Pot second = HotpotFlow.addPotConfig(board, SHOP, "清汤", 2600, 7L, "srv-pot-one-b");

        HotpotFlow.addPot(board, SHOP, session.id, first.id, session.version, GUEST, false, "srv-pot-add-a");
        assertEquals(1, session.pots.size());
        assertEquals(POT_FEN, session.payableFen);
        assertEquals(POT_FEN, session.pots.get(0).priceFen);
        int version = session.version;
        HotpotFlow.addPot(board, SHOP, session.id, first.id, version, GUEST, false, "srv-pot-add-a");
        assertEquals(1, session.pots.size());
        assertEquals(version, session.version);

        ServiceException extra = assertThrows(ServiceException.class,
                () -> HotpotFlow.addPot(board, SHOP, session.id, second.id, version, GUEST, false, "srv-pot-add-b"));
        assertEquals(BAD_REQUEST.getCode(), extra.getCode());
        assertEquals(1, session.pots.size());
        assertEquals(POT_FEN, session.pots.get(0).priceFen);
        assertEquals(POT_FEN, session.payableFen);
    }

    @Test
    void openSessionsAreThisShopsInProgressLines() {
        HotpotFlow.Board board = board();
        HotpotFlow.Slot slot = slot(board, SHOP, 2, "srv-slot-list");
        HotpotFlow.Table table = table(board, SHOP, "S9", "srv-table-list");
        table(board, SHOP, "S8", "srv-table-list3");
        HotpotFlow.Slot otherSlot = slot(board, OTHER_SHOP, 1, "srv-slot-list2");
        table(board, OTHER_SHOP, "S9", "srv-table-list2");
        menu(board, SHOP, BEEF, "鲜牛肉", 1000);
        menu(board, OTHER_SHOP, BEEF, "别店牛肉", 900);
        HotpotFlow.Session session = HotpotFlow.orderDish(board, SHOP, slot.id, "S9", GUEST, BEEF, 3, null,
                "srv-dish-list1");
        HotpotFlow.SessionDish line = session.dishes.get(0);
        HotpotFlow.serve(board, SHOP, session.id, session.version, true, "srv-serve-list",
                List.of(new HotpotFlow.LineQty(line.id, 1)));
        HotpotFlow.orderDish(board, OTHER_SHOP, otherSlot.id, "S9", OTHER_GUEST, BEEF, 1, null, "srv-dish-list2");
        HotpotFlow.Session paid = HotpotFlow.orderDish(board, SHOP, slot.id, "S8", OTHER_GUEST, BEEF, 1,
                null, "srv-dish-list3");
        paid.paid = true;

        List<HotpotFlow.Session> rows = HotpotFlow.openSessions(board, SHOP);
        assertEquals(1, rows.size());
        assertEquals(session.id, rows.get(0).id);
        assertEquals(table.id, rows.get(0).tableId);
        assertEquals("S9", table.signCode);
        HotpotFlow.SessionDish shown = rows.get(0).dishes.get(0);
        assertEquals(line.id, shown.id);
        assertEquals(3, shown.quantity);
        assertEquals(1, shown.servedQty);
        assertEquals(0, shown.refundedQty);
        assertEquals(2, shown.refundableQty());

        HotpotFlow.refund(board, SHOP, session.id, session.version, true, "srv-refund-list",
                List.of(new HotpotFlow.LineQty(line.id, 2)));
        assertEquals(1, HotpotFlow.openSessions(board, SHOP).size());
        assertEquals(1, line.servedQty);
        assertEquals(2, line.refundedQty);
        assertFalse(session.feesWaived);
        assertEquals(HotpotFlow.SessionStatus.OPEN, session.status);
        assertTrue(table.occupied);
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

    private static void menu(HotpotFlow.Board board, long shopId, long productId, String name, int priceFen) {
        HotpotFlow.MenuDish dish = new HotpotFlow.MenuDish();
        dish.id = productId;
        dish.shopId = shopId;
        dish.name = name;
        dish.priceFen = priceFen;
        board.menu.add(dish);
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
