package co.yixiang.yshop.framework.common.hotpot;

import co.yixiang.yshop.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;

import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 已经接上的顾客账单、扫码开桌和店员预约入座，缺的是服务端这一截。
 */
class HotpotPageGapTest {

    private static final long SHOP = 1L;
    private static final long OTHER_SHOP = 2L;
    private static final long GUEST = 42L;
    private static final long OTHER_GUEST = 99L;
    private static final long BEEF = 501L;
    private static final long TOFU = 502L;

    @Test
    void scanDoesNotOccupyAndFirstDishDoes() {
        HotpotFlow.Board board = new HotpotFlow.Board();
        HotpotFlow.Slot slot = window(board, SHOP, 2, "gap-slot-scan");
        HotpotFlow.Table table = HotpotFlow.addTable(board, SHOP, "A1", 7L, "gap-table-scan");
        menu(board, SHOP, BEEF, "鲜牛肉", 1800);

        HotpotFlow.PublicScan before = HotpotFlow.scan(board, SHOP, "A1");
        HotpotFlow.scan(board, SHOP, "A1");
        assertEquals("A1", before.signCode());
        assertFalse(before.occupied());
        assertFalse(table.occupied);
        assertEquals(0, board.sessions.size());
        assertEquals(0, board.holds.size());
        assertEquals(0, slot.occupied);

        HotpotFlow.Session session = HotpotFlow.orderDish(board, SHOP, slot.id, "A1", GUEST, BEEF, 2, null,
                "gap-dish-001");
        assertEquals(HotpotFlow.SessionStatus.OPEN, session.status);
        assertEquals(1, session.version);
        assertEquals(3600, session.payableFen);
        assertEquals(GUEST, session.ownerUid);
        assertFalse(session.paid);
        assertFalse(session.zeroPayReleased);
        assertEquals(1, session.dishes.size());
        assertEquals(BEEF, session.dishes.get(0).productId);
        assertEquals("鲜牛肉", session.dishes.get(0).name);
        assertEquals(1800, session.dishes.get(0).priceFen);
        assertEquals(2, session.dishes.get(0).quantity);
        assertTrue(table.occupied);
        assertEquals(GUEST, table.boundUid);
        assertEquals(session.id, table.sessionId);
        assertEquals(1, slot.occupied);

        HotpotFlow.Session replay = HotpotFlow.orderDish(board, SHOP, slot.id, "A1", GUEST, BEEF, 2, null,
                "gap-dish-001");
        assertEquals(session.id, replay.id);
        assertEquals(1, session.dishes.size());
        assertEquals(3600, session.payableFen);
        assertEquals(1, slot.occupied);
        assertTrue(HotpotFlow.scan(board, SHOP, "A1").occupied());
    }

    @Test
    void firstDishRefusesAMissingPriceAnotherShopAndAFullSlotWithoutOccupying() {
        HotpotFlow.Board board = new HotpotFlow.Board();
        HotpotFlow.Slot slot = window(board, SHOP, 1, "gap-slot-miss");
        HotpotFlow.Table table = HotpotFlow.addTable(board, SHOP, "B2", 7L, "gap-table-miss");
        HotpotFlow.reserve(board, SHOP, slot.id, OTHER_GUEST, "gap-full-hold");

        ServiceException missing = assertThrows(ServiceException.class,
                () -> HotpotFlow.orderDish(board, SHOP, slot.id, "B2", GUEST, BEEF, 1, null, "gap-dish-miss"));
        assertEquals(BAD_REQUEST.getCode(), missing.getCode());
        assertFalse(table.occupied);
        assertEquals(1, slot.occupied);
        assertEquals(0, board.sessions.size());

        menu(board, OTHER_SHOP, BEEF, "别店牛肉", 1800);
        ServiceException foreign = assertThrows(ServiceException.class,
                () -> HotpotFlow.orderDish(board, SHOP, slot.id, "B2", GUEST, BEEF, 1, null, "gap-dish-fore"));
        assertEquals(FORBIDDEN.getCode(), foreign.getCode());
        assertFalse(foreign.getMessage().contains(Long.toString(GUEST)));
        assertFalse(table.occupied);
        assertEquals(0, board.sessions.size());

        menu(board, SHOP, BEEF, "鲜牛肉", 1800);
        ServiceException full = assertThrows(ServiceException.class,
                () -> HotpotFlow.orderDish(board, SHOP, slot.id, "B2", GUEST, BEEF, 1, null, "gap-dish-full"));
        assertEquals(BAD_REQUEST.getCode(), full.getCode());
        assertFalse(table.occupied);
        assertEquals(1, slot.occupied);

        for (String blank : new String[]{null, "", "   ", "short"}) {
            ServiceException rejected = assertThrows(ServiceException.class,
                    () -> HotpotFlow.orderDish(board, SHOP, slot.id, "B2", GUEST, BEEF, 1, null, blank));
            assertEquals(BAD_REQUEST.getCode(), rejected.getCode());
        }
        assertFalse(table.occupied);
    }

    @Test
    void reservationIsReusedByTheFirstDishAndTheBillFeedsSettle() {
        HotpotFlow.Board board = new HotpotFlow.Board();
        HotpotFlow.Slot slot = window(board, SHOP, 1, "gap-slot-bill");
        HotpotFlow.Table table = HotpotFlow.addTable(board, SHOP, "C3", 7L, "gap-table-bill");
        HotpotFlow.Hold hold = HotpotFlow.reserve(board, SHOP, slot.id, GUEST, "gap-reserve-01");
        menu(board, SHOP, BEEF, "鲜牛肉", 1800);
        menu(board, SHOP, TOFU, "豆腐", 600);
        HotpotFlow.Pot pot = HotpotFlow.addPotConfig(board, SHOP, "红锅", 8800, 7L, "gap-pot-001");

        HotpotFlow.Session session = HotpotFlow.orderDish(board, SHOP, slot.id, "C3", GUEST, BEEF, 1, null,
                "gap-dish-bill");
        assertEquals(hold.id, session.holdId);
        assertEquals(GUEST, session.ownerUid);
        assertEquals(HotpotFlow.HoldStatus.SEATED, hold.status);
        assertEquals(1, slot.occupied);
        assertEquals(1800, session.payableFen);

        ServiceException stale = assertThrows(ServiceException.class,
                () -> HotpotFlow.orderDish(board, SHOP, slot.id, "C3", GUEST, TOFU, 1, session.version + 3,
                        "gap-dish-more"));
        assertEquals(BAD_REQUEST.getCode(), stale.getCode());
        assertEquals(1, session.dishes.size());

        HotpotFlow.orderDish(board, SHOP, slot.id, "C3", GUEST, TOFU, 1, session.version, "gap-dish-more");
        assertEquals(2, session.version);
        assertEquals(2400, session.payableFen);
        HotpotFlow.orderDish(board, SHOP, slot.id, "C3", GUEST, TOFU, 1, 1, "gap-dish-more");
        assertEquals(2, session.dishes.size());
        assertEquals(1, slot.occupied);

        HotpotFlow.addPot(board, SHOP, session.id, pot.id, session.version, GUEST, false, "gap-pot-add1");
        assertEquals(11200, session.payableFen);

        HotpotFlow.BoundBill bill = HotpotFlow.boundBill(board, SHOP, "C3", GUEST);
        assertEquals(session.id, bill.sessionId());
        assertEquals(session.version, bill.version());
        assertEquals(11200, bill.payableFen());
        assertEquals("OPEN", bill.status());
        assertEquals(1, bill.pots().size());
        assertEquals(8800, bill.pots().get(0).priceFen);
        assertEquals("红锅", bill.pots().get(0).name);
        assertEquals(2, bill.dishes().size());
        assertEquals(BEEF, bill.dishes().get(0).productId);
        assertEquals(TOFU, bill.dishes().get(1).productId);

        ServiceException blocked = assertThrows(ServiceException.class,
                () -> HotpotFlow.beginSettlement(board, GUEST, bill.sessionId(), bill.version(), false));
        assertEquals(BAD_REQUEST.getCode(), blocked.getCode());
        assertEquals("收款渠道未接通", blocked.getMessage());
        assertFalse(session.paid);
        assertFalse(session.zeroPayReleased);
        assertEquals(HotpotFlow.SessionStatus.OPEN, session.status);
        assertTrue(table.occupied);
        assertEquals(1, slot.occupied);

        for (String channel : new String[]{"weixin", "alipay", "yue", "cash"}) {
            HotpotFlow.Settlement result = HotpotFlow.completeFromTrustedChannel(
                    board, session.id, channel, "gap-notice-" + channel);
            assertFalse(result.paymentSuccess);
            assertEquals(HotpotFlow.DISPOSITION_UNCONNECTED, result.disposition);
            assertFalse(session.paid);
            assertEquals(HotpotFlow.SessionStatus.OPEN, session.status);
        }

        ServiceException stranger = assertThrows(ServiceException.class,
                () -> HotpotFlow.boundBill(board, SHOP, "C3", OTHER_GUEST));
        assertEquals(NOT_FOUND.getCode(), stranger.getCode());
        assertFalse(stranger.getMessage().contains(Long.toString(GUEST)));
        assertFalse(stranger.getMessage().contains(Long.toString(OTHER_GUEST)));
        ServiceException otherShop = assertThrows(ServiceException.class,
                () -> HotpotFlow.boundBill(board, OTHER_SHOP, "C3", GUEST));
        assertEquals(NOT_FOUND.getCode(), otherShop.getCode());
    }

    @Test
    void anOpenKeyCanStillPlaceTheFirstDishAndThenStaysPut() {
        HotpotFlow.Board board = new HotpotFlow.Board();
        HotpotFlow.Slot slot = window(board, SHOP, 1, "gap-slot-openkey");
        HotpotFlow.Table table = HotpotFlow.addTable(board, SHOP, "K1", 7L, "gap-table-openkey");
        menu(board, SHOP, BEEF, "鲜牛肉", 1800);
        HotpotFlow.Session opened = HotpotFlow.open(board, SHOP, slot.id, null, GUEST, false, "gap-open-key");
        assertFalse(table.occupied);
        assertEquals(0, opened.payableFen);

        ServiceException missingVersion = assertThrows(ServiceException.class,
                () -> HotpotFlow.orderDish(board, SHOP, slot.id, "K1", GUEST, BEEF, 1, null, "gap-open-key"));
        assertEquals(BAD_REQUEST.getCode(), missingVersion.getCode());
        assertFalse(table.occupied);
        assertTrue(opened.dishes.isEmpty());

        HotpotFlow.Session ordered = HotpotFlow.orderDish(board, SHOP, slot.id, "K1", GUEST, BEEF, 1, opened.version,
                "gap-open-key");
        assertEquals(opened.id, ordered.id);
        assertEquals(1800, ordered.payableFen);
        assertEquals(1, ordered.dishes.size());
        assertTrue(table.occupied);
        assertEquals(GUEST, table.boundUid);
        assertEquals(1, slot.occupied);

        HotpotFlow.orderDish(board, SHOP, slot.id, "K1", GUEST, BEEF, 1, null, "gap-open-key");
        assertEquals(1, ordered.dishes.size());
        assertEquals(1800, ordered.payableFen);
        assertEquals(1, slot.occupied);
    }

    @Test
    void theSameDishKeyInAnotherShopIsAnotherOrder() {
        HotpotFlow.Board board = new HotpotFlow.Board();
        HotpotFlow.Slot mine = window(board, SHOP, 1, "gap-slot-shop-a");
        HotpotFlow.Slot theirs = window(board, OTHER_SHOP, 1, "gap-slot-shop-b");
        HotpotFlow.addTable(board, SHOP, "D4", 7L, "gap-table-shop-a");
        HotpotFlow.addTable(board, OTHER_SHOP, "D4", 7L, "gap-table-shop-b");
        menu(board, SHOP, BEEF, "鲜牛肉", 1800);
        menu(board, OTHER_SHOP, BEEF, "别店牛肉", 900);

        HotpotFlow.Session first = HotpotFlow.orderDish(board, SHOP, mine.id, "D4", GUEST, BEEF, 1, null,
                "gap-same-key");
        HotpotFlow.Session second = HotpotFlow.orderDish(board, OTHER_SHOP, theirs.id, "D4", GUEST, BEEF, 1, null,
                "gap-same-key");
        assertFalse(first.id == second.id);
        assertEquals(1800, first.payableFen);
        assertEquals(900, second.payableFen);
        assertEquals(1, mine.occupied);
        assertEquals(1, theirs.occupied);
    }

    @Test
    void staffSeatsAReservationOntoAnIdleTableAsThatGuest() {
        HotpotFlow.Board board = new HotpotFlow.Board();
        HotpotFlow.Slot slot = window(board, SHOP, 2, "gap-slot-seat");
        HotpotFlow.Table table = HotpotFlow.addTable(board, SHOP, "E5", 7L, "gap-table-seat");
        HotpotFlow.Table other = HotpotFlow.addTable(board, SHOP, "E6", 7L, "gap-table-seat2");
        HotpotFlow.Hold hold = HotpotFlow.reserve(board, SHOP, slot.id, GUEST, "gap-seat-res");
        HotpotFlow.Hold second = HotpotFlow.reserve(board, SHOP, slot.id, OTHER_GUEST, "gap-seat-res2");

        HotpotFlow.Session session = HotpotFlow.seatReservation(board, SHOP, hold.id, slot.id, "E5", "gap-seat-key1");
        assertEquals(GUEST, session.ownerUid);
        assertFalse(session.ownerUid == 0L);
        assertEquals(hold.id, session.holdId);
        assertEquals(slot.id, session.slotId);
        assertEquals(table.id, session.tableId);
        assertEquals(HotpotFlow.SessionStatus.OPEN, session.status);
        assertEquals(1, session.version);
        assertEquals(0, session.payableFen);
        assertFalse(session.paid);
        assertFalse(session.zeroPayReleased);
        assertEquals(HotpotFlow.HoldStatus.SEATED, hold.status);
        assertEquals(GUEST, table.boundUid);
        assertEquals(session.id, table.sessionId);
        assertEquals(2, slot.occupied);

        HotpotFlow.Session replay = HotpotFlow.seatReservation(board, SHOP, hold.id, slot.id, "E5", "gap-seat-key1");
        assertEquals(session.id, replay.id);
        assertEquals(2, slot.occupied);
        assertEquals(1, openSessions(board));

        ServiceException moved = assertThrows(ServiceException.class,
                () -> HotpotFlow.seatReservation(board, SHOP, hold.id, slot.id, "E6", "gap-seat-key1"));
        assertEquals(BAD_REQUEST.getCode(), moved.getCode());
        assertFalse(other.occupied);
        assertEquals(session.id, table.sessionId);
        assertEquals(2, slot.occupied);

        ServiceException taken = assertThrows(ServiceException.class,
                () -> HotpotFlow.seatReservation(board, SHOP, second.id, slot.id, "E5", "gap-seat-key2"));
        assertEquals(FORBIDDEN.getCode(), taken.getCode());
        assertEquals(HotpotFlow.HoldStatus.HELD, second.status);
        assertEquals(GUEST, table.boundUid);
        assertEquals(2, slot.occupied);
    }

    @Test
    void seatingAfterTheOldStatusChangeStillOpensTheGuestSessionOnce() {
        HotpotFlow.Board board = new HotpotFlow.Board();
        HotpotFlow.Slot slot = window(board, SHOP, 1, "gap-slot-oldseat");
        HotpotFlow.Table table = HotpotFlow.addTable(board, SHOP, "F7", 7L, "gap-table-oldseat");
        HotpotFlow.Hold hold = HotpotFlow.reserve(board, SHOP, slot.id, GUEST, "gap-old-res");
        HotpotFlow.seat(board, SHOP, hold.id, slot.id, 7L, true, "gap-old-seat");
        assertEquals(HotpotFlow.HoldStatus.SEATED, hold.status);
        assertFalse(table.occupied);
        assertEquals(1, slot.occupied);

        HotpotFlow.Session session = HotpotFlow.seatReservation(board, SHOP, hold.id, slot.id, "F7", "gap-old-open");
        assertEquals(GUEST, session.ownerUid);
        assertEquals(hold.id, session.holdId);
        assertTrue(table.occupied);
        assertEquals(1, slot.occupied);
        assertEquals(1, openSessions(board));
    }

    @Test
    void reservationSeatRejectsTheWrongShopAWalkInAndAClosedSession() {
        HotpotFlow.Board board = new HotpotFlow.Board();
        HotpotFlow.Slot slot = window(board, SHOP, 2, "gap-slot-deny");
        HotpotFlow.Slot otherSlot = window(board, OTHER_SHOP, 1, "gap-slot-deny2");
        HotpotFlow.Table table = HotpotFlow.addTable(board, SHOP, "G8", 7L, "gap-table-deny");
        HotpotFlow.Hold hold = HotpotFlow.reserve(board, SHOP, slot.id, GUEST, "gap-deny-res");
        HotpotFlow.Hold foreign = HotpotFlow.reserve(board, OTHER_SHOP, otherSlot.id, GUEST, "gap-deny-fore");
        HotpotFlow.Hold walked = HotpotFlow.walkIn(board, SHOP, slot.id, null, 0L, true, "gap-deny-walk");

        ServiceException blank = assertThrows(ServiceException.class,
                () -> HotpotFlow.seatReservation(board, SHOP, hold.id, slot.id, "G8", " "));
        assertEquals(BAD_REQUEST.getCode(), blank.getCode());
        ServiceException otherSlotId = assertThrows(ServiceException.class,
                () -> HotpotFlow.seatReservation(board, SHOP, hold.id, otherSlot.id, "G8", "gap-deny-slot"));
        assertEquals(BAD_REQUEST.getCode(), otherSlotId.getCode());
        ServiceException otherShop = assertThrows(ServiceException.class,
                () -> HotpotFlow.seatReservation(board, SHOP, foreign.id, otherSlot.id, "G8", "gap-deny-shop"));
        assertEquals(FORBIDDEN.getCode(), otherShop.getCode());
        ServiceException walkIn = assertThrows(ServiceException.class,
                () -> HotpotFlow.seatReservation(board, SHOP, walked.id, slot.id, "G8", "gap-deny-uid0"));
        assertEquals(BAD_REQUEST.getCode(), walkIn.getCode());
        assertFalse(table.occupied);
        assertEquals(HotpotFlow.HoldStatus.HELD, hold.status);
        assertEquals(0, board.sessions.size());

        HotpotFlow.Session session = HotpotFlow.seatReservation(board, SHOP, hold.id, slot.id, "G8", "gap-deny-open");
        HotpotFlow.Settlement released = HotpotFlow.beginSettlement(board, GUEST, session.id, session.version, false);
        assertFalse(released.paymentSuccess);
        assertTrue(released.zeroPayReleased);
        assertFalse(session.paid);
        assertEquals(HotpotFlow.SessionStatus.RELEASED, session.status);
        assertFalse(table.occupied);

        ServiceException again = assertThrows(ServiceException.class,
                () -> HotpotFlow.seatReservation(board, SHOP, hold.id, slot.id, "G8", "gap-deny-again"));
        assertEquals(BAD_REQUEST.getCode(), again.getCode());
        assertEquals(1, board.sessions.size());
        assertFalse(table.occupied);
        assertEquals(1, slot.occupied);

        HotpotFlow.Hold later = HotpotFlow.reserve(board, SHOP, slot.id, OTHER_GUEST, "gap-deny-paid");
        HotpotFlow.Session open = HotpotFlow.seatReservation(board, SHOP, later.id, slot.id, "G8", "gap-deny-paid1");
        open.paid = true;
        ServiceException paid = assertThrows(ServiceException.class,
                () -> HotpotFlow.seatReservation(board, SHOP, later.id, slot.id, "G8", "gap-deny-paid2"));
        assertEquals(BAD_REQUEST.getCode(), paid.getCode());
        assertTrue(open.paid);
        assertEquals(HotpotFlow.SessionStatus.OPEN, open.status);
        assertEquals(open.id, table.sessionId);
    }

    @Test
    void theSameSeatKeyInAnotherShopAttachesThatShopsGuest() {
        HotpotFlow.Board board = new HotpotFlow.Board();
        HotpotFlow.Slot mine = window(board, SHOP, 1, "gap-slot-iso-a");
        HotpotFlow.Slot theirs = window(board, OTHER_SHOP, 1, "gap-slot-iso-b");
        HotpotFlow.Table mineTable = HotpotFlow.addTable(board, SHOP, "H9", 7L, "gap-table-iso-a");
        HotpotFlow.Table theirTable = HotpotFlow.addTable(board, OTHER_SHOP, "H9", 7L, "gap-table-iso-b");
        HotpotFlow.Hold mineHold = HotpotFlow.reserve(board, SHOP, mine.id, GUEST, "gap-iso-res-a");
        HotpotFlow.Hold theirHold = HotpotFlow.reserve(board, OTHER_SHOP, theirs.id, OTHER_GUEST, "gap-iso-res-b");

        HotpotFlow.Session first = HotpotFlow.seatReservation(board, SHOP, mineHold.id, mine.id, "H9", "gap-iso-key");
        HotpotFlow.Session second = HotpotFlow.seatReservation(board, OTHER_SHOP, theirHold.id, theirs.id, "H9",
                "gap-iso-key");
        assertFalse(first.id == second.id);
        assertEquals(GUEST, first.ownerUid);
        assertEquals(OTHER_GUEST, second.ownerUid);
        assertEquals(GUEST, mineTable.boundUid);
        assertEquals(OTHER_GUEST, theirTable.boundUid);
        assertEquals(1, mine.occupied);
        assertEquals(1, theirs.occupied);
    }

    @Test
    void aSlotWindowIsStoredAndDoesNotCancelOrRelease() {
        HotpotFlow.Board board = new HotpotFlow.Board();
        ServiceException half = assertThrows(ServiceException.class,
                () -> HotpotFlow.addSlot(board, SHOP, 2, LocalTime.of(11, 0), null, 7L, "gap-window-half"));
        assertEquals(BAD_REQUEST.getCode(), half.getCode());
        assertEquals(0, board.slots.size());

        HotpotFlow.Slot slot = HotpotFlow.addSlot(board, SHOP, 2, LocalTime.of(11, 0), LocalTime.of(11, 30), 7L,
                "gap-window-01");
        assertEquals(LocalTime.of(11, 0), slot.startTime);
        assertEquals(LocalTime.of(11, 30), slot.endTime);
        HotpotFlow.Hold hold = HotpotFlow.reserve(board, SHOP, slot.id, GUEST, "gap-window-res");

        assertFalse(HotpotFlow.lateAutoCancels());
        assertEquals(0, HotpotFlow.sweep(board));
        assertEquals(HotpotFlow.HoldStatus.HELD, hold.status);
        assertEquals(1, slot.occupied);

        ServiceException changed = assertThrows(ServiceException.class,
                () -> HotpotFlow.addSlot(board, SHOP, 2, LocalTime.of(11, 0), LocalTime.of(12, 0), 7L,
                        "gap-window-01"));
        assertEquals(BAD_REQUEST.getCode(), changed.getCode());
        assertEquals(LocalTime.of(11, 30), slot.endTime);
        assertEquals(1, slot.occupied);
        assertEquals(1, board.slots.size());
    }

    @Test
    void menuQuoteUsesTheShelfPriceAndRejectsAnotherShop() {
        HotpotFlow.MenuDish dish = HotpotFlow.quoteMenuDish(SHOP, BEEF, SHOP, "鲜牛肉", new BigDecimal("18.50"), 1);
        assertEquals(1850, dish.priceFen);
        assertEquals("鲜牛肉", dish.name);
        assertEquals(1850, HotpotFlow.yuanToFen(new BigDecimal("18.500")));

        ServiceException fraction = assertThrows(ServiceException.class,
                () -> HotpotFlow.yuanToFen(new BigDecimal("18.555")));
        assertEquals(BAD_REQUEST.getCode(), fraction.getCode());
        ServiceException missingPrice = assertThrows(ServiceException.class,
                () -> HotpotFlow.quoteMenuDish(SHOP, BEEF, SHOP, "鲜牛肉", null, 1));
        assertEquals(BAD_REQUEST.getCode(), missingPrice.getCode());
        ServiceException hidden = assertThrows(ServiceException.class,
                () -> HotpotFlow.quoteMenuDish(SHOP, BEEF, SHOP, "鲜牛肉", new BigDecimal("18.50"), 0));
        assertEquals(BAD_REQUEST.getCode(), hidden.getCode());
        ServiceException unnamed = assertThrows(ServiceException.class,
                () -> HotpotFlow.quoteMenuDish(SHOP, BEEF, SHOP, "  ", new BigDecimal("18.50"), 1));
        assertEquals(BAD_REQUEST.getCode(), unnamed.getCode());

        ServiceException missing = assertThrows(ServiceException.class,
                () -> HotpotFlow.quoteMenuDish(SHOP, BEEF, null, "鲜牛肉", new BigDecimal("18.50"), 1));
        assertEquals(NOT_FOUND.getCode(), missing.getCode());
        ServiceException other = assertThrows(ServiceException.class,
                () -> HotpotFlow.quoteMenuDish(SHOP, BEEF, OTHER_SHOP, "别店牛肉", new BigDecimal("9.00"), 1));
        assertEquals(FORBIDDEN.getCode(), other.getCode());
        assertFalse(other.getMessage().contains(Long.toString(OTHER_SHOP)));
    }

    private static HotpotFlow.Slot window(HotpotFlow.Board board, long shopId, int capacity, String key) {
        return HotpotFlow.addSlot(board, shopId, capacity, LocalTime.of(11, 0), LocalTime.of(14, 0), 7L, key);
    }

    private static void menu(HotpotFlow.Board board, long shopId, long productId, String name, int priceFen) {
        HotpotFlow.MenuDish dish = new HotpotFlow.MenuDish();
        dish.id = productId;
        dish.shopId = shopId;
        dish.name = name;
        dish.priceFen = priceFen;
        board.menu.add(dish);
    }

    private static int openSessions(HotpotFlow.Board board) {
        int count = 0;
        for (HotpotFlow.Session session : board.sessions.values()) {
            if (session.status == HotpotFlow.SessionStatus.OPEN) {
                count++;
            }
        }
        return count;
    }

}
