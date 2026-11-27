package co.yixiang.yshop.framework.common.order;

import co.yixiang.yshop.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MalatangFlowTest {

    @Test
    void quoteUsesShopFeesAndConfiguredFlavorPrice() {
        ShopOptionCatalog.Option flavor = flavor(1L, "麻辣", "3.50");
        ShopOptionCatalog.Option addon = addon(6L, "宽粉", "2.00");
        MalatangQuote.Quote quote = MalatangQuote.price(
                fees("1.20", "6.00"),
                MalatangQuote.TAKE_OUT,
                new BigDecimal("20.00"),
                flavor,
                List.of(new MalatangQuote.AddonPick(addon, 2)),
                new BigDecimal("1.00"));

        assertEquals(0, new BigDecimal("3.50").compareTo(quote.flavorAmount));
        assertEquals("麻辣", quote.flavorName);
        assertEquals(0, new BigDecimal("4.00").compareTo(quote.addonAmount));
        assertEquals(0, new BigDecimal("2.00").compareTo(quote.addonLines.get(0).unitPrice));
        assertEquals(0, new BigDecimal("1.20").compareTo(quote.packingAmount));
        assertEquals(0, new BigDecimal("6.00").compareTo(quote.deliveryAmount));
        assertEquals(0, new BigDecimal("33.70").compareTo(quote.payAmount));
    }

    @Test
    void missingPackingIsOmittedAndMissingDeliveryRejectsTakeout() {
        MalatangQuote.Quote takein = MalatangQuote.price(
                new MalatangQuote.ShopFees(null, null),
                MalatangQuote.TAKE_IN,
                new BigDecimal("10.00"),
                null,
                List.of(),
                null);
        assertNull(takein.packingAmount);
        assertNull(takein.deliveryAmount);
        assertNull(takein.flavorAmount);
        assertEquals(0, new BigDecimal("10.00").compareTo(takein.payAmount));

        ServiceException unknownType = assertThrows(ServiceException.class, () -> MalatangQuote.price(
                fees("1", "6"),
                "skip-fees",
                new BigDecimal("10.00"),
                null,
                List.of(),
                null));
        assertEquals(BAD_REQUEST.getCode(), unknownType.getCode());

        ServiceException missingDelivery = assertThrows(ServiceException.class, () -> MalatangQuote.price(
                new MalatangQuote.ShopFees(null, null),
                MalatangQuote.TAKE_OUT,
                new BigDecimal("10.00"),
                null,
                List.of(),
                null));
        assertEquals(BAD_REQUEST.getCode(), missingDelivery.getCode());
    }

    @Test
    void configuredZeroFeesStayZeroAndDeskSkipsBoth() {
        MalatangQuote.Quote takeout = MalatangQuote.price(
                fees("0.00", "0.00"),
                MalatangQuote.TAKE_OUT,
                new BigDecimal("10.00"),
                null,
                List.of(),
                null);
        assertEquals(0, BigDecimal.ZERO.compareTo(takeout.packingAmount));
        assertEquals(0, BigDecimal.ZERO.compareTo(takeout.deliveryAmount));
        assertEquals(0, new BigDecimal("10.00").compareTo(takeout.payAmount));

        MalatangQuote.Quote desk = MalatangQuote.price(
                fees("2.00", "8.00"),
                "desk",
                new BigDecimal("10.00"),
                null,
                List.of(),
                null);
        assertNull(desk.packingAmount);
        assertNull(desk.deliveryAmount);
        assertEquals(0, new BigDecimal("10.00").compareTo(desk.payAmount));
    }

    @Test
    void flavorWithoutAConfiguredPriceIsRejected() {
        ShopOptionCatalog.Option flavor = new ShopOptionCatalog.Option(5L, 1L, ShopOptionCatalog.FLAVOR, "麻辣", null);
        ServiceException missing = assertThrows(ServiceException.class, () -> MalatangQuote.price(
                fees("1", "1"),
                MalatangQuote.TAKE_IN,
                new BigDecimal("10"),
                flavor,
                List.of(),
                null));
        assertEquals(BAD_REQUEST.getCode(), missing.getCode());

        ServiceException blankConfig = assertThrows(ServiceException.class, () -> ShopOptionCatalog.validate(List.of(
                new ShopOptionCatalog.Option(null, 1L, ShopOptionCatalog.FLAVOR, "麻辣", null))));
        assertEquals(BAD_REQUEST.getCode(), blankConfig.getCode());

        ServiceException negative = assertThrows(ServiceException.class, () -> ShopOptionCatalog.validate(List.of(
                new ShopOptionCatalog.Option(null, 1L, ShopOptionCatalog.ADDON, "香菜", new BigDecimal("-1")))));
        assertEquals(BAD_REQUEST.getCode(), negative.getCode());
    }

    @Test
    void optionsAndQueueNumbersStayInsideTheShop() {
        List<ShopOptionCatalog.Option> book = List.of(
                flavor(5L, "麻辣", "3.50"),
                new ShopOptionCatalog.Option(9L, 2L, ShopOptionCatalog.FLAVOR, "麻辣", new BigDecimal("99.00")),
                addon(6L, "宽粉", "2.00"));
        assertEquals(2, ShopOptionCatalog.ofShop(1L, book).size());
        assertEquals(1, ShopOptionCatalog.ofShop(2L, book).size());
        assertEquals(0, new BigDecimal("3.50").compareTo(
                ShopOptionCatalog.require(1L, 5L, ShopOptionCatalog.FLAVOR, book).price));

        ServiceException otherShop = assertThrows(ServiceException.class,
                () -> ShopOptionCatalog.require(1L, 9L, ShopOptionCatalog.FLAVOR, book));
        assertEquals(FORBIDDEN.getCode(), otherShop.getCode());
        ServiceException missing = assertThrows(ServiceException.class,
                () -> ShopOptionCatalog.require(1L, 404L, ShopOptionCatalog.FLAVOR, book));
        assertEquals(BAD_REQUEST.getCode(), missing.getCode());

        assertNull(QueueTickets.issue(false, null, 1L, List.of()));
        Integer shopOne = QueueTickets.issue(true, null, 1L, List.of());
        assertEquals(1, shopOne);
        List<QueueTickets.Ticket> issued = new ArrayList<>();
        issued.add(new QueueTickets.Ticket(1L, "A-1", shopOne));
        issued.add(new QueueTickets.Ticket(2L, "B-9", 9));
        assertEquals(shopOne, QueueTickets.issue(true, shopOne, 1L, issued));
        assertEquals(2, QueueTickets.issue(true, null, 1L, issued));
        assertEquals(10, QueueTickets.issue(true, null, 2L, issued));
        assertEquals(1, QueueTickets.issue(true, null, 3L, issued));
    }

    @Test
    void unconnectedPaymentDoesNotIssueAQueueNumber() {
        AtomicInteger issued = new AtomicInteger();
        PaymentReconciliation.Result result = PaymentReconciliation.apply(
                "wx-notice-1",
                "weixin",
                "S1",
                new PaymentReconciliation.Orders() {
                    @Override
                    public PaymentReconciliation.OrderState load(String orderId) {
                        return new PaymentReconciliation.OrderState(true, false, false);
                    }

                    @Override
                    public boolean markPaidIfUnpaid(String orderId, String payType) {
                        issued.incrementAndGet();
                        return true;
                    }
                },
                new PaymentReconciliation.Notices() {
                    private PaymentReconciliation.Disposition saved;

                    @Override
                    public PaymentReconciliation.Disposition find(String notifyKey) {
                        return saved;
                    }

                    @Override
                    public boolean claim(String notifyKey, PaymentReconciliation.Disposition disposition) {
                        saved = disposition;
                        return true;
                    }

                    @Override
                    public void replace(String notifyKey, PaymentReconciliation.Disposition disposition) {
                        saved = disposition;
                    }
                },
                (orderId, notifyKey, payType) -> issued.incrementAndGet(),
                () -> issued.incrementAndGet());

        assertEquals(PaymentReconciliation.Disposition.UNCONNECTED, result.disposition());
        assertFalse(result.settled());
        assertEquals(0, issued.get());
        assertNull(QueueTickets.issue(false, null, 1L, List.of()));
    }

    private static MalatangQuote.ShopFees fees(String packing, String delivery) {
        return new MalatangQuote.ShopFees(new BigDecimal(packing), new BigDecimal(delivery));
    }

    private static ShopOptionCatalog.Option flavor(long id, String name, String price) {
        return new ShopOptionCatalog.Option(id, 1L, ShopOptionCatalog.FLAVOR, name, new BigDecimal(price));
    }

    private static ShopOptionCatalog.Option addon(long id, String name, String price) {
        return new ShopOptionCatalog.Option(id, 1L, ShopOptionCatalog.ADDON, name, new BigDecimal(price));
    }

}
