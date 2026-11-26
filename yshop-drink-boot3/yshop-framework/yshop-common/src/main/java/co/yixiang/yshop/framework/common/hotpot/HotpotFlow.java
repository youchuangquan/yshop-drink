package co.yixiang.yshop.framework.common.hotpot;

import co.yixiang.yshop.framework.common.order.OrderRequestKeys;
import co.yixiang.yshop.framework.common.order.PayChannels;
import co.yixiang.yshop.framework.common.shop.ShopAccess;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.NOT_FOUND;
import static co.yixiang.yshop.framework.common.exception.util.ServiceExceptionUtil.exception;
import static co.yixiang.yshop.framework.common.exception.util.ServiceExceptionUtil.exception0;

/**
 * 火锅预约、入座、开台和结账。
 * 预约占的是时段容量，不是桌号。迟到自动取消保持关闭，不编停留时长。
 * 锅底价格只来自本店已有配置。没有接通的收款渠道不能把账单标成已付。
 */
public final class HotpotFlow {

    public static final String DISPOSITION_ZERO_PAY = "ZERO_PAY";
    public static final String DISPOSITION_UNCONNECTED = "UNCONNECTED";

    private HotpotFlow() {
    }

    /**
     * 这一轮不自动取消。以后即使有停留时长，也不在这里打开。
     */
    public static boolean lateAutoCancels() {
        return false;
    }

    /**
     * 不扫描、不取消。调用方不能靠传入时长把迟到单清掉，因为这里不接收时长。
     */
    public static int sweep(Board board) {
        return 0;
    }

    public enum HoldStatus {
        HELD, SEATED, CANCELLED, RELEASED
    }

    public enum SessionStatus {
        OPEN, RELEASED
    }

    public static final class Board {
        public final Map<Long, Slot> slots = new LinkedHashMap<>();
        public final Map<Long, Hold> holds = new LinkedHashMap<>();
        public final Map<Long, Table> tables = new LinkedHashMap<>();
        public final Map<Long, Session> sessions = new LinkedHashMap<>();
        public final List<Pot> pots = new ArrayList<>();
        public final Map<String, RequestUse> requests = new LinkedHashMap<>();
        public boolean mutated;
        private long sequence = 1;

        public long nextId() {
            return sequence++;
        }

        public void noteId(long id) {
            if (id >= sequence) {
                sequence = id + 1;
            }
        }
    }

    public static final class Slot {
        public long id;
        public long shopId;
        public int capacity;
        public int occupied;
        public int occupiedDelta;
        public boolean persisted;
        public boolean dirty;
    }

    public static final class Hold {
        public long id;
        public long shopId;
        public long slotId;
        public long ownerUid;
        public String requestKey;
        public HoldStatus status;
        public boolean persisted;
        public boolean dirty;
    }

    public static final class Table {
        public long id;
        public long shopId;
        public String signCode;
        public boolean occupied;
        public Long boundUid;
        public Long sessionId;
        public boolean persisted;
        public boolean dirty;
    }

    public static final class Pot {
        public long id;
        public long shopId;
        public String name;
        public Integer priceFen;
        public boolean persisted;
    }

    public static final class SessionPot {
        public long id;
        public long potId;
        public String name;
        public int priceFen;
        public boolean persisted;
    }

    public static final class Session {
        public long id;
        public long shopId;
        public long slotId;
        public Long tableId;
        public Long holdId;
        public long ownerUid;
        public int version;
        public int payableFen;
        public boolean paid;
        public boolean zeroPayReleased;
        public SessionStatus status;
        public String requestKey;
        public final List<SessionPot> pots = new ArrayList<>();
        public boolean persisted;
        public boolean dirty;
    }

    public static final class RequestUse {
        public long shopId;
        public String requestKey;
        public long ownerUid;
        public long slotId;
        public String action;
        public Long holdId;
        public Long sessionId;
        public Long potId;
        public Long resourceId;
        public String mark;
        public long id;
        public boolean persisted;
        public boolean dirty;
    }

    /**
     * 公开扫码只能有这两项。桌台绑定的顾客不在这里。
     */
    public static final class PublicScan {
        private final String signCode;
        private final boolean occupied;

        public PublicScan(String signCode, boolean occupied) {
            this.signCode = signCode;
            this.occupied = occupied;
        }

        public String signCode() {
            return signCode;
        }

        public boolean occupied() {
            return occupied;
        }
    }

    public static final class Settlement {
        public final Session session;
        public final boolean paymentSuccess;
        public final boolean zeroPayReleased;
        public final String disposition;

        public Settlement(Session session, boolean paymentSuccess, boolean zeroPayReleased, String disposition) {
            this.session = session;
            this.paymentSuccess = paymentSuccess;
            this.zeroPayReleased = zeroPayReleased;
            this.disposition = disposition;
        }
    }

    public static Slot addSlot(Board board, long shopId, int capacity, long actorUid, String rawKey) {
        shopId = ShopAccess.requirePositiveShopId(shopId);
        if (capacity <= 0 || actorUid < 0) {
            throw exception(BAD_REQUEST);
        }
        String key = OrderRequestKeys.require(rawKey);
        RequestUse prior = board.requests.get(keyId(shopId, key));
        if (prior != null) {
            rejectOwner(prior, actorUid);
            if (!"SLOT".equals(prior.action) || prior.resourceId == null
                    || !Integer.toString(capacity).equals(prior.mark)) {
                throw exception(BAD_REQUEST);
            }
            Slot existing = board.slots.get(prior.resourceId);
            if (existing == null || existing.shopId != shopId) {
                throw exception(BAD_REQUEST);
            }
            return existing;
        }
        Slot slot = new Slot();
        slot.id = board.nextId();
        slot.shopId = shopId;
        slot.capacity = capacity;
        slot.occupied = 0;
        board.slots.put(slot.id, slot);
        remember(board, shopId, actorUid, 0L, "SLOT", key, null, null, null, slot.id, Integer.toString(capacity));
        touch(board);
        return slot;
    }

    public static Table addTable(Board board, long shopId, String signCode, long actorUid, String rawKey) {
        shopId = ShopAccess.requirePositiveShopId(shopId);
        String sign = requireSign(signCode);
        String key = OrderRequestKeys.require(rawKey);
        RequestUse prior = board.requests.get(keyId(shopId, key));
        if (prior != null) {
            rejectOwner(prior, actorUid);
            if (!"TABLE".equals(prior.action) || !sign.equals(prior.mark)) {
                throw exception(BAD_REQUEST);
            }
            Table existing = board.tables.get(prior.resourceId);
            if (existing == null || existing.shopId != shopId) {
                throw exception(BAD_REQUEST);
            }
            return existing;
        }
        if (findTable(board, shopId, sign) != null) {
            throw exception(BAD_REQUEST);
        }
        Table table = new Table();
        table.id = board.nextId();
        table.shopId = shopId;
        table.signCode = sign;
        board.tables.put(table.id, table);
        remember(board, shopId, actorUid, 0L, "TABLE", key, null, null, null, table.id, sign);
        touch(board);
        return table;
    }

    public static Pot addPotConfig(Board board, long shopId, String name, Integer priceFen, long actorUid, String rawKey) {
        shopId = ShopAccess.requirePositiveShopId(shopId);
        if (name == null || name.trim().isEmpty() || priceFen == null || priceFen < 0 || actorUid < 0) {
            throw exception(BAD_REQUEST);
        }
        String potName = name.trim();
        String mark = potName + ":" + priceFen;
        String key = OrderRequestKeys.require(rawKey);
        RequestUse prior = board.requests.get(keyId(shopId, key));
        if (prior != null) {
            rejectOwner(prior, actorUid);
            if (!"POT".equals(prior.action) || !mark.equals(prior.mark) || prior.slotId != 0L) {
                throw exception(BAD_REQUEST);
            }
            Pot existing = findPotById(board, prior.resourceId);
            if (existing == null || existing.shopId != shopId) {
                throw exception(BAD_REQUEST);
            }
            return existing;
        }
        Pot pot = new Pot();
        pot.id = board.nextId();
        pot.shopId = shopId;
        pot.name = potName;
        pot.priceFen = priceFen;
        board.pots.add(pot);
        remember(board, shopId, actorUid, 0L, "POT", key, null, null, null, pot.id, mark);
        touch(board);
        return pot;
    }

    public static Hold reserve(Board board, long shopId, long slotId, long ownerUid, String rawKey) {
        shopId = ShopAccess.requirePositiveShopId(shopId);
        if (ownerUid <= 0 || slotId <= 0) {
            throw exception(BAD_REQUEST);
        }
        Slot slot = requireSlot(board, shopId, slotId);
        RequestUse prior = lookup(board, shopId, ownerUid, slot.id, rawKey);
        if (prior != null) {
            Hold existing = existingHold(board, shopId, prior);
            if (existing == null) {
                throw exception(BAD_REQUEST);
            }
            return existing;
        }
        if (slot.occupied >= slot.capacity) {
            throw exception(BAD_REQUEST);
        }
        occupy(board, slot, 1);
        Hold hold = newHold(board, shopId, slot.id, ownerUid, OrderRequestKeys.require(rawKey), HoldStatus.HELD);
        remember(board, shopId, ownerUid, slot.id, "RESERVE", hold.requestKey, hold.id, null, null, null, null);
        return hold;
    }

    /**
     * 取消只认预约单本人。请求体只有 holdId，店和键都不从这里再取一份。
     */
    public static Hold cancel(Board board, long actorUid, long holdId) {
        if (actorUid <= 0 || holdId <= 0) {
            throw exception(BAD_REQUEST);
        }
        Hold hold = board.holds.get(holdId);
        if (hold == null) {
            throw exception(NOT_FOUND);
        }
        if (hold.ownerUid != actorUid) {
            throw exception(FORBIDDEN);
        }
        if (hold.status == HoldStatus.CANCELLED) {
            return hold;
        }
        if (hold.status != HoldStatus.HELD) {
            throw exception(BAD_REQUEST);
        }
        hold.status = HoldStatus.CANCELLED;
        hold.dirty = hold.persisted;
        Slot slot = requireSlot(board, hold.shopId, hold.slotId);
        occupy(board, slot, -1);
        touch(board);
        return hold;
    }

    public static Hold seat(Board board, long shopId, long holdId, long slotId, long actorUid, boolean staff, String rawKey) {
        shopId = ShopAccess.requirePositiveShopId(shopId);
        String key = OrderRequestKeys.require(rawKey);
        if (holdId <= 0 || slotId <= 0) {
            throw exception(BAD_REQUEST);
        }
        Hold hold = board.holds.get(holdId);
        if (hold == null) {
            throw exception(NOT_FOUND);
        }
        if (hold.shopId != shopId) {
            throw exception(FORBIDDEN);
        }
        if (!staff && hold.ownerUid != actorUid) {
            throw exception(FORBIDDEN);
        }
        if (hold.slotId != slotId) {
            throw exception(BAD_REQUEST);
        }
        RequestUse prior = lookup(board, shopId, hold.ownerUid, hold.slotId, key);
        if (prior != null && prior.holdId != null && prior.holdId != hold.id) {
            throw exception(BAD_REQUEST);
        }
        if (hold.status == HoldStatus.SEATED) {
            if (prior == null) {
                remember(board, shopId, hold.ownerUid, hold.slotId, "SEAT", key,
                        hold.id, null, null, null, null);
            }
            return hold;
        }
        if (hold.status != HoldStatus.HELD) {
            throw exception(BAD_REQUEST);
        }
        hold.status = HoldStatus.SEATED;
        hold.dirty = hold.persisted;
        touch(board);
        if (prior == null) {
            remember(board, shopId, hold.ownerUid, hold.slotId, "SEAT", key,
                    hold.id, null, null, null, null);
        }
        return hold;
    }

    public static Hold walkIn(Board board, long shopId, long slotId, String signCode, long actorUid, boolean staff, String rawKey) {
        shopId = ShopAccess.requirePositiveShopId(shopId);
        if (slotId <= 0 || (!staff && actorUid <= 0) || (staff && actorUid < 0)) {
            throw exception(BAD_REQUEST);
        }
        long ownerUid = actorUid;
        Slot slot = requireSlot(board, shopId, slotId);
        Table table = blank(signCode) ? null : requireTable(board, shopId, signCode);
        RequestUse prior = lookup(board, shopId, ownerUid, slot.id, rawKey);
        if (table != null && table.boundUid != null) {
            boolean ownReplay = prior != null && ownerUid > 0 && table.boundUid.equals(ownerUid);
            if (!ownReplay) {
                throw exception(FORBIDDEN);
            }
        }
        if (table != null && table.occupied && prior == null) {
            throw exception(BAD_REQUEST);
        }
        if (prior != null) {
            Hold existing = existingHold(board, shopId, prior);
            if (existing == null) {
                throw exception(BAD_REQUEST);
            }
            return existing;
        }
        if (slot.occupied >= slot.capacity) {
            throw exception(BAD_REQUEST);
        }
        occupy(board, slot, 1);
        Hold hold = newHold(board, shopId, slot.id, ownerUid, OrderRequestKeys.require(rawKey), HoldStatus.SEATED);
        if (table != null) {
            bindTable(board, table, ownerUid > 0 ? ownerUid : null, null);
        }
        remember(board, shopId, ownerUid, slot.id, "WALKIN", hold.requestKey, hold.id, null, null, null, null);
        return hold;
    }

    public static Session open(Board board, long shopId, long slotId, String signCode, long actorUid, boolean staff, String rawKey) {
        shopId = ShopAccess.requirePositiveShopId(shopId);
        if (slotId <= 0 || (!staff && actorUid <= 0) || (staff && actorUid < 0)) {
            throw exception(BAD_REQUEST);
        }
        Slot slot = board.slots.get(slotId);
        if (slot == null) {
            throw exception(BAD_REQUEST);
        }
        if (slot.shopId != shopId) {
            throw exception(FORBIDDEN);
        }
        long ownerUid = actorUid;
        Table table = blank(signCode) ? null : requireTable(board, shopId, signCode);
        RequestUse prior = lookup(board, shopId, ownerUid, slot.id, rawKey);
        if (prior != null && prior.sessionId != null) {
            Session existing = board.sessions.get(prior.sessionId);
            if (existing != null && existing.shopId == shopId) {
                if (table != null) {
                    rejectForeignTable(table, existing.ownerUid);
                }
                return existing;
            }
        }
        if (table != null) {
            rejectForeignTable(table, ownerUid);
            if (table.occupied && table.sessionId != null) {
                Session sitting = board.sessions.get(table.sessionId);
                if (sitting == null || sitting.ownerUid != ownerUid) {
                    throw exception(FORBIDDEN);
                }
            }
        }
        Hold cover = cover(board, shopId, slot.id, ownerUid, prior);
        if (cover == null) {
            if (slot.occupied >= slot.capacity) {
                throw exception(BAD_REQUEST);
            }
            occupy(board, slot, 1);
            cover = newHold(board, shopId, slot.id, ownerUid, OrderRequestKeys.require(rawKey), HoldStatus.SEATED);
            if (prior == null) {
                prior = remember(board, shopId, ownerUid, slot.id, "OPEN", cover.requestKey, cover.id, null, null, null, null);
            } else {
                prior.holdId = cover.id;
                prior.dirty = prior.persisted;
            }
        } else if (cover.status == HoldStatus.HELD) {
            cover.status = HoldStatus.SEATED;
            cover.dirty = cover.persisted;
            touch(board);
        } else if (cover.status != HoldStatus.SEATED) {
            throw exception(BAD_REQUEST);
        }
        Session already = openSessionFor(board, cover.id);
        if (already != null) {
            return already;
        }
        Session session = new Session();
        session.id = board.nextId();
        session.shopId = shopId;
        session.slotId = slot.id;
        session.holdId = cover.id;
        session.ownerUid = cover.ownerUid;
        session.version = 1;
        session.payableFen = 0;
        session.paid = false;
        session.status = SessionStatus.OPEN;
        session.requestKey = OrderRequestKeys.require(rawKey);
        board.sessions.put(session.id, session);
        if (table != null) {
            bindTable(board, table, session.ownerUid > 0 ? session.ownerUid : null, session.id);
            session.tableId = table.id;
        }
        if (prior == null) {
            remember(board, shopId, ownerUid, slot.id, "OPEN", session.requestKey, cover.id, session.id, null, null, null);
        } else {
            prior.sessionId = session.id;
            prior.holdId = cover.id;
            prior.dirty = prior.persisted;
        }
        touch(board);
        return session;
    }

    public static Session addPot(Board board, long shopId, long sessionId, long potId, int version, long actorUid,
                                 boolean staff, String rawKey) {
        shopId = ShopAccess.requirePositiveShopId(shopId);
        Session session = board.sessions.get(sessionId);
        if (session == null) {
            throw exception(NOT_FOUND);
        }
        if (session.shopId != shopId) {
            throw exception(FORBIDDEN);
        }
        if (!staff && (session.ownerUid <= 0 || session.ownerUid != actorUid)) {
            throw exception(FORBIDDEN);
        }
        if (session.status != SessionStatus.OPEN) {
            throw exception(BAD_REQUEST);
        }
        RequestUse prior = lookup(board, shopId, session.ownerUid, session.slotId, rawKey);
        if (prior != null && prior.potId != null) {
            if (prior.potId != potId) {
                throw exception(BAD_REQUEST);
            }
            return session;
        }
        if (session.version != version) {
            throw exception(BAD_REQUEST);
        }
        Pot pot = resolvePot(board, shopId, potId);
        SessionPot line = new SessionPot();
        line.id = board.nextId();
        line.potId = pot.id;
        line.name = pot.name;
        line.priceFen = pot.priceFen;
        session.pots.add(line);
        session.payableFen = payable(session);
        session.version = session.version + 1;
        session.dirty = session.persisted;
        if (prior == null) {
            remember(board, shopId, session.ownerUid, session.slotId, "ADD_POT", OrderRequestKeys.require(rawKey),
                    session.holdId, session.id, pot.id, null, null);
        } else {
            prior.potId = pot.id;
            prior.sessionId = session.id;
            prior.dirty = prior.persisted;
        }
        touch(board);
        return session;
    }

    /**
     * 顾客结账只认 sessionId 和 version。应付为 0 时释放开台时钉住的时段，这不是支付成功。
     * 应付大于 0 时，没有接通的渠道直接失败，不改已付、不释放。
     */
    public static Settlement beginSettlement(Board board, long actorUid, long sessionId, int version, boolean staff) {
        if (sessionId <= 0) {
            throw exception(BAD_REQUEST);
        }
        Session session = requireOwn(board, sessionId, actorUid, staff);
        if (session.version != version) {
            throw exception(BAD_REQUEST);
        }
        if (session.status == SessionStatus.RELEASED) {
            return new Settlement(session, false, session.zeroPayReleased,
                    session.zeroPayReleased ? DISPOSITION_ZERO_PAY : DISPOSITION_UNCONNECTED);
        }
        if (session.status != SessionStatus.OPEN) {
            throw exception(BAD_REQUEST);
        }
        if (session.payableFen == 0) {
            releasePinned(board, session);
            session.zeroPayReleased = true;
            session.paid = false;
            session.dirty = session.persisted;
            touch(board);
            return new Settlement(session, false, true, DISPOSITION_ZERO_PAY);
        }
        throw exception0(BAD_REQUEST.getCode(), "收款渠道未接通");
    }

    /**
     * 服务端渠道完成。这一轮微信、支付宝、余额、现金都未接到火锅账单上，不能把账单标成已付。
     * 没有给顾客或店员的“已付金额”入口。
     */
    public static Settlement completeFromTrustedChannel(Board board, long sessionId, String payType, String notifyKey) {
        OrderRequestKeys.requireNotify(notifyKey);
        Session session = board.sessions.get(sessionId);
        if (session == null) {
            throw exception(NOT_FOUND);
        }
        // 订单模块里余额和现金可以入账。火锅账单这一轮没有接通任何渠道，包括这两种。
        PayChannels.settles(payType);
        return new Settlement(session, false, false, DISPOSITION_UNCONNECTED);
    }

    public static PublicScan scan(Board board, long shopId, String signCode) {
        shopId = ShopAccess.requirePositiveShopId(shopId);
        Table table = requireTable(board, shopId, signCode);
        return new PublicScan(table.signCode, table.occupied);
    }

    private static Hold cover(Board board, long shopId, long slotId, long ownerUid, RequestUse prior) {
        if (prior != null && prior.holdId != null) {
            Hold keyed = board.holds.get(prior.holdId);
            if (keyed != null && keyed.shopId == shopId && keyed.slotId == slotId
                    && (keyed.status == HoldStatus.HELD || keyed.status == HoldStatus.SEATED)) {
                return keyed;
            }
        }
        if (ownerUid <= 0) {
            return null;
        }
        for (Hold hold : board.holds.values()) {
            if (hold.shopId == shopId && hold.slotId == slotId && hold.ownerUid == ownerUid
                    && (hold.status == HoldStatus.HELD || hold.status == HoldStatus.SEATED)
                    && openSessionFor(board, hold.id) == null) {
                return hold;
            }
        }
        return null;
    }

    private static Session openSessionFor(Board board, long holdId) {
        for (Session session : board.sessions.values()) {
            if (session.holdId != null && session.holdId == holdId && session.status == SessionStatus.OPEN) {
                return session;
            }
        }
        return null;
    }

    private static void releasePinned(Board board, Session session) {
        Slot slot = board.slots.get(session.slotId);
        if (slot == null || slot.shopId != session.shopId) {
            throw exception(BAD_REQUEST);
        }
        Hold hold = session.holdId == null ? null : board.holds.get(session.holdId);
        if (hold != null && hold.status != HoldStatus.RELEASED && hold.status != HoldStatus.CANCELLED) {
            hold.status = HoldStatus.RELEASED;
            hold.dirty = hold.persisted;
            occupy(board, slot, -1);
        } else if (hold == null) {
            occupy(board, slot, -1);
        }
        if (session.tableId != null) {
            Table table = board.tables.get(session.tableId);
            if (table != null && table.shopId == session.shopId) {
                table.occupied = false;
                table.boundUid = null;
                table.sessionId = null;
                table.dirty = table.persisted;
            }
        }
        session.status = SessionStatus.RELEASED;
        session.paid = false;
    }

    private static void occupy(Board board, Slot slot, int delta) {
        int next = slot.occupied + delta;
        if (next < 0 || next > slot.capacity) {
            throw exception(BAD_REQUEST);
        }
        slot.occupied = next;
        slot.occupiedDelta = slot.occupiedDelta + delta;
        slot.dirty = slot.persisted;
        touch(board);
    }

    private static Hold newHold(Board board, long shopId, long slotId, long ownerUid, String key, HoldStatus status) {
        Hold hold = new Hold();
        hold.id = board.nextId();
        hold.shopId = shopId;
        hold.slotId = slotId;
        hold.ownerUid = ownerUid;
        hold.requestKey = key;
        hold.status = status;
        board.holds.put(hold.id, hold);
        touch(board);
        return hold;
    }

    private static RequestUse lookup(Board board, long shopId, long ownerUid, long slotId, String rawKey) {
        String key = OrderRequestKeys.require(rawKey);
        RequestUse prior = board.requests.get(keyId(shopId, key));
        if (prior == null) {
            return null;
        }
        if (prior.slotId != slotId) {
            throw exception(BAD_REQUEST);
        }
        if (prior.ownerUid != ownerUid) {
            throw exception(FORBIDDEN);
        }
        return prior;
    }

    private static Hold existingHold(Board board, long shopId, RequestUse prior) {
        if (prior.holdId == null) {
            return null;
        }
        Hold hold = board.holds.get(prior.holdId);
        if (hold == null || hold.shopId != shopId) {
            return null;
        }
        return hold;
    }

    private static RequestUse remember(Board board, long shopId, long ownerUid, long slotId, String action, String key,
                                       Long holdId, Long sessionId, Long potId, Long resourceId, String mark) {
        RequestUse use = new RequestUse();
        use.shopId = shopId;
        use.ownerUid = ownerUid;
        use.slotId = slotId;
        use.action = action;
        use.requestKey = key;
        use.holdId = holdId;
        use.sessionId = sessionId;
        use.potId = potId;
        use.resourceId = resourceId;
        use.mark = mark;
        board.requests.put(keyId(shopId, key), use);
        touch(board);
        return use;
    }

    private static Slot requireSlot(Board board, long shopId, long slotId) {
        Slot slot = board.slots.get(slotId);
        if (slot == null) {
            throw exception(BAD_REQUEST);
        }
        if (slot.shopId != shopId) {
            throw exception(FORBIDDEN);
        }
        return slot;
    }

    private static Table requireTable(Board board, long shopId, String signCode) {
        Table table = findTable(board, shopId, requireSign(signCode));
        if (table == null) {
            throw exception(NOT_FOUND);
        }
        return table;
    }

    private static Table findTable(Board board, long shopId, String sign) {
        for (Table table : board.tables.values()) {
            if (table.shopId == shopId && sign.equals(table.signCode)) {
                return table;
            }
        }
        return null;
    }

    private static void rejectForeignTable(Table table, long actorUid) {
        if (table.boundUid != null && (actorUid <= 0 || table.boundUid != actorUid)) {
            throw exception(FORBIDDEN);
        }
    }

    private static void bindTable(Board board, Table table, Long boundUid, Long sessionId) {
        table.occupied = true;
        table.boundUid = boundUid;
        table.sessionId = sessionId;
        table.dirty = table.persisted;
        touch(board);
    }

    private static Session requireOwn(Board board, long sessionId, long actorUid, boolean staff) {
        Session session = board.sessions.get(sessionId);
        if (session == null) {
            throw exception(NOT_FOUND);
        }
        if (session.ownerUid > 0) {
            if (session.ownerUid != actorUid) {
                throw exception(FORBIDDEN);
            }
            return session;
        }
        if (!staff) {
            throw exception(FORBIDDEN);
        }
        return session;
    }

    private static Pot resolvePot(Board board, long shopId, long potId) {
        boolean anyForShop = false;
        Pot matched = null;
        boolean otherShop = false;
        for (Pot pot : board.pots) {
            if (pot.shopId == shopId) {
                anyForShop = true;
            }
            if (pot.id == potId) {
                if (pot.shopId == shopId) {
                    matched = pot;
                } else {
                    otherShop = true;
                }
            }
        }
        if (!anyForShop) {
            throw exception(BAD_REQUEST);
        }
        if (matched == null) {
            if (otherShop) {
                throw exception(FORBIDDEN);
            }
            throw exception(BAD_REQUEST);
        }
        if (matched.priceFen == null || matched.priceFen < 0 || matched.name == null || matched.name.isEmpty()) {
            throw exception(BAD_REQUEST);
        }
        return matched;
    }

    private static Pot findPotById(Board board, Long id) {
        if (id == null) {
            return null;
        }
        for (Pot pot : board.pots) {
            if (pot.id == id) {
                return pot;
            }
        }
        return null;
    }

    private static int payable(Session session) {
        long sum = 0;
        for (SessionPot line : session.pots) {
            sum += line.priceFen;
        }
        if (sum > Integer.MAX_VALUE) {
            throw exception(BAD_REQUEST);
        }
        return (int) sum;
    }

    private static void rejectOwner(RequestUse prior, long actorUid) {
        if (prior.ownerUid != actorUid) {
            throw exception(FORBIDDEN);
        }
    }

    private static String requireSign(String signCode) {
        if (signCode == null || signCode.trim().isEmpty()) {
            throw exception(BAD_REQUEST);
        }
        return signCode.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String keyId(long shopId, String key) {
        return shopId + ":" + key;
    }

    private static void touch(Board board) {
        board.mutated = true;
    }

}
