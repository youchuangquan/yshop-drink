package co.yixiang.yshop.framework.common.hotpot;

import co.yixiang.yshop.framework.common.order.OrderRequestKeys;
import co.yixiang.yshop.framework.common.order.PayChannels;
import co.yixiang.yshop.framework.common.shop.ShopAccess;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.NOT_FOUND;
import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static co.yixiang.yshop.framework.common.exception.util.ServiceExceptionUtil.exception;
import static co.yixiang.yshop.framework.common.exception.util.ServiceExceptionUtil.exception0;

/**
 * 火锅预约、入座、开台和结账。
 * 预约占的是时段容量，不是桌号。迟到自动取消保持关闭，不编停留时长。
 * 锅底价格只来自本店已有配置，菜品价格只来自本店在售商品。没有接通的收款渠道不能把账单标成已付。
 * 顾客看桌或扫码不占桌。顾客占桌只发生在第一道菜下单时。店员可以把预约安排到空桌并开出台次。
 * 时段起止只给店员看是否已过点，这里不比较时钟，也不因此取消预约或释放容量。
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
        public final List<MenuDish> menu = new ArrayList<>();
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
        public LocalTime startTime;
        public LocalTime endTime;
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
        public final List<SessionDish> dishes = new ArrayList<>();
        public boolean persisted;
        public boolean dirty;
    }

    public static final class SessionDish {
        public long id;
        public long productId;
        public String name;
        public int priceFen;
        public int quantity;
        public boolean persisted;
    }

    /**
     * 本店在售菜。价格是下单时从商品标价折成的分，不在这里另编。
     */
    public static final class MenuDish {
        public long id;
        public long shopId;
        public String name;
        public int priceFen;
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
    /**
     * 店员占位列表的一行。只有这六项。
     */
    public static final class StaffHold {
        private final long id;
        private final long slotId;
        private final long shopId;
        private final long uid;
        private final HoldStatus status;
        private final Long sessionId;

        public StaffHold(long id, long slotId, long shopId, long uid, HoldStatus status, Long sessionId) {
            this.id = id;
            this.slotId = slotId;
            this.shopId = shopId;
            this.uid = uid;
            this.status = status;
            this.sessionId = sessionId;
        }

        public long id() {
            return id;
        }

        public long slotId() {
            return slotId;
        }

        public long shopId() {
            return shopId;
        }

        public long uid() {
            return uid;
        }

        public HoldStatus status() {
            return status;
        }

        public Long sessionId() {
            return sessionId;
        }
    }

    /**
     * 绑定顾客自己的账单。应付、明细、状态和版本都来自这一次开台，不在这里另编。
     * 没有顾客号，也没有让调用方填写的实付金额。
     */
    public static final class BoundBill {
        private final long sessionId;
        private final int version;
        private final int payableFen;
        private final String status;
        private final List<SessionPot> pots;
        private final List<SessionDish> dishes;

        public BoundBill(Session session) {
            this.sessionId = session.id;
            this.version = session.version;
            this.payableFen = session.payableFen;
            this.status = session.status.name();
            this.pots = List.copyOf(session.pots);
            this.dishes = List.copyOf(session.dishes);
        }

        public long sessionId() {
            return sessionId;
        }

        public int version() {
            return version;
        }

        public int payableFen() {
            return payableFen;
        }

        public String status() {
            return status;
        }

        public List<SessionPot> pots() {
            return pots;
        }

        public List<SessionDish> dishes() {
            return dishes;
        }
    }

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
        return addSlot(board, shopId, capacity, null, null, actorUid, rawKey);
    }

    /**
     * 起止都空表示旧数据没有时刻。只给一边、或同一个键改时刻，都拒绝。
     * 保存时刻不等于判断迟到，也不会取消预约。
     */
    public static Slot addSlot(Board board, long shopId, int capacity, LocalTime start, LocalTime end,
                               long actorUid, String rawKey) {
        shopId = ShopAccess.requirePositiveShopId(shopId);
        if (capacity <= 0 || actorUid < 0 || (start == null) != (end == null)) {
            throw exception(BAD_REQUEST);
        }
        String key = OrderRequestKeys.require(rawKey);
        String mark = slotMark(capacity, start, end);
        RequestUse prior = board.requests.get(keyId(shopId, key));
        if (prior != null) {
            rejectOwner(prior, actorUid);
            if (!"SLOT".equals(prior.action) || prior.resourceId == null || !mark.equals(prior.mark)) {
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
        slot.startTime = start;
        slot.endTime = end;
        board.slots.put(slot.id, slot);
        remember(board, shopId, actorUid, 0L, "SLOT", key, null, null, null, slot.id, mark);
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

    public static List<Slot> slotsOf(Board board, long shopId) {
        shopId = ShopAccess.requirePositiveShopId(shopId);
        List<Slot> rows = new ArrayList<>();
        for (Slot slot : board.slots.values()) {
            if (slot.shopId == shopId) {
                rows.add(slot);
            }
        }
        return rows;
    }

    /**
     * 店员看本店仍占着容量的预约。不含姓名、电话、人数、到店时间和请求键。
     */
    public static List<StaffHold> currentHolds(Board board, long shopId) {
        shopId = ShopAccess.requirePositiveShopId(shopId);
        List<StaffHold> rows = new ArrayList<>();
        for (Hold hold : board.holds.values()) {
            if (hold.shopId != shopId) {
                continue;
            }
            if (hold.status != HoldStatus.HELD && hold.status != HoldStatus.SEATED) {
                continue;
            }
            Session open = openSessionFor(board, hold.id);
            rows.add(new StaffHold(hold.id, hold.slotId, hold.shopId, hold.ownerUid, hold.status,
                    open == null ? null : open.id));
        }
        return rows;
    }

    /**
     * 绑定顾客查看自己的开台账单。对不上就失败，响应里不带身份。
     */
    public static BoundBill boundBill(Board board, long shopId, String signCode, long callerUid) {
        shopId = ShopAccess.requirePositiveShopId(shopId);
        if (callerUid <= 0) {
            throw exception(UNAUTHORIZED);
        }
        if (signCode == null || signCode.trim().isEmpty()) {
            throw exception(BAD_REQUEST);
        }
        Table table = findTable(board, shopId, signCode.trim());
        Session session = table == null || table.sessionId == null ? null : board.sessions.get(table.sessionId);
        if (table == null || table.boundUid == null || table.boundUid != callerUid || session == null
                || session.shopId != shopId || session.status != SessionStatus.OPEN || session.ownerUid != callerUid) {
            throw exception(NOT_FOUND);
        }
        return new BoundBill(session);
    }

    /**
     * 商品标价折成分。多出来的小数、空价、负价都不收，也不另编一个价。
     */
    public static int yuanToFen(BigDecimal yuan) {
        if (yuan == null || yuan.signum() < 0) {
            throw exception(BAD_REQUEST);
        }
        try {
            return yuan.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).intValueExact();
        } catch (ArithmeticException ex) {
            throw exception(BAD_REQUEST);
        }
    }

    /**
     * 菜品报价只接受本店、在售、有名称、有标价的商品。另一家店是 403，没有这条商品是 404。
     */
    public static MenuDish quoteMenuDish(long shopId, long productId, Long productShopId, String name,
                                         BigDecimal priceYuan, Integer onShelf) {
        shopId = ShopAccess.requirePositiveShopId(shopId);
        if (productId <= 0) {
            throw exception(BAD_REQUEST);
        }
        if (productShopId == null) {
            throw exception(NOT_FOUND);
        }
        if (productShopId != shopId) {
            throw exception(FORBIDDEN);
        }
        if (onShelf == null || onShelf != 1 || name == null || name.trim().isEmpty()) {
            throw exception(BAD_REQUEST);
        }
        MenuDish dish = new MenuDish();
        dish.id = productId;
        dish.shopId = shopId;
        dish.name = name.trim();
        dish.priceFen = yuanToFen(priceYuan);
        return dish;
    }

    /**
     * 顾客把一道菜下到空桌时才占桌并开台。已经是本人的开台则只加这一行。
     * 扫码不走这里。没有这道菜、或容量已满时，桌台保持原样。
     * 追加到已有开台时必须带上当前 version。同一个请求键不会把这道菜记两次。
     */
    public static Session orderDish(Board board, long shopId, long slotId, String signCode, long actorUid,
                                    long productId, int quantity, Integer version, String rawKey) {
        shopId = ShopAccess.requirePositiveShopId(shopId);
        if (actorUid <= 0 || slotId <= 0 || productId <= 0 || quantity <= 0) {
            throw exception(BAD_REQUEST);
        }
        Slot slot = requireSlot(board, shopId, slotId);
        Table table = requireTable(board, shopId, signCode);
        MenuDish dish = resolveDish(board, shopId, productId);
        String mark = productId + "x" + quantity;
        RequestUse prior = lookup(board, shopId, actorUid, slot.id, rawKey);
        if (prior != null && prior.mark != null && !mark.equals(prior.mark)) {
            throw exception(BAD_REQUEST);
        }
        if (prior != null && prior.sessionId != null && mark.equals(prior.mark)) {
            Session existing = board.sessions.get(prior.sessionId);
            if (existing == null || existing.shopId != shopId || existing.ownerUid != actorUid) {
                throw exception(BAD_REQUEST);
            }
            refuseUnusable(existing);
            if (existing.tableId != null && existing.tableId != table.id) {
                throw exception(BAD_REQUEST);
            }
            return existing;
        }
        if (table.boundUid != null && table.boundUid != actorUid) {
            throw exception(FORBIDDEN);
        }
        if (table.occupied && table.boundUid == null) {
            throw exception(FORBIDDEN);
        }
        Session sitting = sessionOnTable(board, table);
        if (sitting != null) {
            if (sitting.shopId != shopId || sitting.ownerUid != actorUid) {
                throw exception(FORBIDDEN);
            }
            return appendDish(board, shopId, slot, table, sitting, actorUid, dish, quantity, version, prior, mark,
                    OrderRequestKeys.require(rawKey));
        }
        Hold cover = cover(board, shopId, slot.id, actorUid, prior);
        Session already = cover == null ? null : openSessionFor(board, cover.id);
        if (already != null) {
            return appendDish(board, shopId, slot, table, already, actorUid, dish, quantity, version, prior, mark,
                    OrderRequestKeys.require(rawKey));
        }
        if (version != null) {
            throw exception(BAD_REQUEST);
        }
        if (cover == null) {
            if (slot.occupied >= slot.capacity) {
                throw exception(BAD_REQUEST);
            }
            occupy(board, slot, 1);
            cover = newHold(board, shopId, slot.id, actorUid, OrderRequestKeys.require(rawKey), HoldStatus.SEATED);
        } else if (cover.status == HoldStatus.HELD) {
            cover.status = HoldStatus.SEATED;
            cover.dirty = cover.persisted;
            touch(board);
        } else if (cover.status != HoldStatus.SEATED) {
            throw exception(BAD_REQUEST);
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
        session.zeroPayReleased = false;
        session.status = SessionStatus.OPEN;
        session.requestKey = OrderRequestKeys.require(rawKey);
        board.sessions.put(session.id, session);
        bindTable(board, table, actorUid, session.id);
        session.tableId = table.id;
        addDishLine(board, session, dish, quantity, false);
        linkRequest(board, shopId, actorUid, slot.id, "DISH", OrderRequestKeys.require(rawKey), prior,
                cover.id, session.id, mark);
        return session;
    }

    /**
     * 店员把一条仍占用容量的预约安排到空桌，并开出属于预约人的台次。
     * 不是散客：预约人 uid 为 0 时拒绝。容量不加第二次。
     * 已支付、应付为 0 已释放、或不是 OPEN 的台次不能再被当成新开桌。
     */
    public static Session seatReservation(Board board, long shopId, long holdId, long slotId, String signCode,
                                          String rawKey) {
        shopId = ShopAccess.requirePositiveShopId(shopId);
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
        if (hold.slotId != slotId || hold.ownerUid <= 0) {
            throw exception(BAD_REQUEST);
        }
        if (hold.status != HoldStatus.HELD && hold.status != HoldStatus.SEATED) {
            throw exception(BAD_REQUEST);
        }
        Table table = requireTable(board, shopId, signCode);
        String mark = "table:" + table.signCode;
        RequestUse prior = lookup(board, shopId, hold.ownerUid, hold.slotId, rawKey);
        if (prior != null && prior.holdId != null && prior.holdId != hold.id) {
            throw exception(BAD_REQUEST);
        }
        if (prior != null && prior.mark != null && prior.mark.startsWith("table:") && !mark.equals(prior.mark)) {
            throw exception(BAD_REQUEST);
        }
        Session open = openSessionFor(board, hold.id);
        if (open != null) {
            refuseUnusable(open);
            attachOpenTable(board, table, open);
            markSeated(board, hold);
            linkRequest(board, shopId, hold.ownerUid, hold.slotId, "SEAT_TABLE", OrderRequestKeys.require(rawKey),
                    prior, hold.id, open.id, mark);
            return open;
        }
        Session closed = anySessionFor(board, hold.id);
        if (closed != null) {
            refuseUnusable(closed);
        }
        if (table.boundUid != null && table.boundUid != hold.ownerUid) {
            throw exception(FORBIDDEN);
        }
        if (table.occupied || table.sessionId != null || table.boundUid != null) {
            throw exception(BAD_REQUEST);
        }
        markSeated(board, hold);
        Session session = new Session();
        session.id = board.nextId();
        session.shopId = shopId;
        session.slotId = hold.slotId;
        session.holdId = hold.id;
        session.tableId = table.id;
        session.ownerUid = hold.ownerUid;
        session.version = 1;
        session.payableFen = 0;
        session.paid = false;
        session.zeroPayReleased = false;
        session.status = SessionStatus.OPEN;
        session.requestKey = OrderRequestKeys.require(rawKey);
        board.sessions.put(session.id, session);
        bindTable(board, table, hold.ownerUid, session.id);
        linkRequest(board, shopId, hold.ownerUid, hold.slotId, "SEAT_TABLE", session.requestKey, prior,
                hold.id, session.id, mark);
        return session;
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
        for (SessionDish line : session.dishes) {
            long row = (long) line.priceFen * line.quantity;
            if (row < 0) {
                throw exception(BAD_REQUEST);
            }
            sum += row;
        }
        if (sum > Integer.MAX_VALUE) {
            throw exception(BAD_REQUEST);
        }
        return (int) sum;
    }

    private static Session appendDish(Board board, long shopId, Slot slot, Table table, Session session, long actorUid,
                                      MenuDish dish, int quantity, Integer version, RequestUse prior, String mark,
                                      String requestKey) {
        refuseUnusable(session);
        if (session.ownerUid != actorUid || session.slotId != slot.id) {
            throw exception(BAD_REQUEST);
        }
        if (version == null || session.version != version) {
            throw exception(BAD_REQUEST);
        }
        if (session.tableId != null && session.tableId != table.id) {
            throw exception(BAD_REQUEST);
        }
        if (session.tableId == null) {
            if (table.occupied && (table.boundUid == null || table.boundUid != actorUid || table.sessionId != null)) {
                throw exception(BAD_REQUEST);
            }
            bindTable(board, table, actorUid, session.id);
            session.tableId = table.id;
            session.dirty = session.persisted;
        }
        addDishLine(board, session, dish, quantity, true);
        linkRequest(board, shopId, actorUid, slot.id, "DISH", requestKey, prior, session.holdId, session.id, mark);
        return session;
    }

    private static void addDishLine(Board board, Session session, MenuDish dish, int quantity, boolean bumpVersion) {
        SessionDish line = new SessionDish();
        line.id = board.nextId();
        line.productId = dish.id;
        line.name = dish.name;
        line.priceFen = dish.priceFen;
        line.quantity = quantity;
        session.dishes.add(line);
        session.payableFen = payable(session);
        if (bumpVersion) {
            session.version = session.version + 1;
        }
        session.dirty = session.persisted;
        touch(board);
    }

    private static void linkRequest(Board board, long shopId, long ownerUid, long slotId, String action, String key,
                                    RequestUse prior, Long holdId, long sessionId, String mark) {
        if (prior == null) {
            remember(board, shopId, ownerUid, slotId, action, key, holdId, sessionId, null, null, mark);
            return;
        }
        boolean changed = false;
        if (prior.holdId == null || !prior.holdId.equals(holdId)) {
            prior.holdId = holdId;
            changed = true;
        }
        if (prior.sessionId == null || prior.sessionId != sessionId) {
            prior.sessionId = sessionId;
            changed = true;
        }
        if (prior.mark == null) {
            prior.mark = mark;
            changed = true;
        }
        if (changed) {
            prior.dirty = prior.persisted;
            touch(board);
        }
    }

    private static void attachOpenTable(Board board, Table table, Session session) {
        if (session.tableId != null && session.tableId != table.id) {
            throw exception(BAD_REQUEST);
        }
        if (table.sessionId != null && table.sessionId != session.id) {
            throw exception(BAD_REQUEST);
        }
        if (table.boundUid != null && table.boundUid != session.ownerUid) {
            throw exception(FORBIDDEN);
        }
        if (table.occupied && table.sessionId == null) {
            throw exception(BAD_REQUEST);
        }
        if (session.tableId == null) {
            if (table.occupied) {
                throw exception(BAD_REQUEST);
            }
            bindTable(board, table, session.ownerUid, session.id);
            session.tableId = table.id;
            session.dirty = session.persisted;
            touch(board);
        }
    }

    private static void markSeated(Board board, Hold hold) {
        if (hold.status == HoldStatus.HELD) {
            hold.status = HoldStatus.SEATED;
            hold.dirty = hold.persisted;
            touch(board);
        }
    }

    private static Session sessionOnTable(Board board, Table table) {
        if (table.sessionId == null) {
            return null;
        }
        return board.sessions.get(table.sessionId);
    }

    private static Session anySessionFor(Board board, long holdId) {
        for (Session session : board.sessions.values()) {
            if (session.holdId != null && session.holdId == holdId) {
                return session;
            }
        }
        return null;
    }

    private static void refuseUnusable(Session session) {
        if (session.paid || session.zeroPayReleased || session.status != SessionStatus.OPEN) {
            throw exception(BAD_REQUEST);
        }
    }

    private static MenuDish resolveDish(Board board, long shopId, long productId) {
        boolean otherShop = false;
        MenuDish matched = null;
        for (MenuDish dish : board.menu) {
            if (dish.id != productId) {
                continue;
            }
            if (dish.shopId == shopId) {
                matched = dish;
            } else {
                otherShop = true;
            }
        }
        if (matched == null) {
            if (otherShop) {
                throw exception(FORBIDDEN);
            }
            throw exception(BAD_REQUEST);
        }
        if (matched.priceFen < 0 || matched.name == null || matched.name.isEmpty()) {
            throw exception(BAD_REQUEST);
        }
        return matched;
    }

    private static String slotMark(int capacity, LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            return Integer.toString(capacity);
        }
        return capacity + "|" + start + "|" + end;
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
