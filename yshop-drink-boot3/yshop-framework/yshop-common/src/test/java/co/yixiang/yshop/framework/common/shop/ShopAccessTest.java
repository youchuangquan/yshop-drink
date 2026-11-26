package co.yixiang.yshop.framework.common.shop;

import co.yixiang.yshop.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopAccessTest {

    @Test
    void staffReadAndWriteStayInsideOwnShop() {
        assertEquals(1L, ShopAccess.staffShop(1L, null));
        assertEquals(1L, ShopAccess.staffShop(1L, "1"));
        assertEquals(1L, ShopAccess.staffShop(1L, 1));

        ServiceException crossRead = assertThrows(ServiceException.class,
                () -> ShopAccess.staffShop(1L, 2L));
        assertEquals(FORBIDDEN.getCode(), crossRead.getCode());

        AtomicBoolean wrote = new AtomicBoolean(false);
        ServiceException crossWrite = assertThrows(ServiceException.class,
                () -> ShopAccess.staffWrite(1L, 2L, 2L, () -> wrote.set(true)));
        assertEquals(FORBIDDEN.getCode(), crossWrite.getCode());
        assertFalse(wrote.get());

        ServiceException otherRow = assertThrows(ServiceException.class,
                () -> ShopAccess.staffWrite(1L, null, 2L, () -> wrote.set(true)));
        assertEquals(FORBIDDEN.getCode(), otherRow.getCode());
        assertFalse(wrote.get());

        ShopAccess.staffWrite(1L, 1L, 1L, () -> wrote.set(true));
        assertTrue(wrote.get());
    }

    @Test
    void unboundStaffAndMissingShopAreRejectedBeforeAnyQuery() {
        AtomicInteger queries = new AtomicInteger();
        Runnable query = queries::incrementAndGet;

        ServiceException unbound = assertThrows(ServiceException.class,
                () -> ShopAccess.staffShop(0L, 1L));
        assertEquals(FORBIDDEN.getCode(), unbound.getCode());

        ServiceException missing = assertThrows(ServiceException.class,
                () -> ShopAccess.requirePositiveShopId(null));
        assertEquals(BAD_REQUEST.getCode(), missing.getCode());

        ServiceException multiShop = assertThrows(ServiceException.class,
                () -> ShopAccess.requirePositiveShopId("1,2"));
        assertEquals(BAD_REQUEST.getCode(), multiShop.getCode());

        assertEquals(0, queries.get());
        query.run();
        assertEquals(1, queries.get());
    }

    @Test
    void couponOfShopACannotOffsetShopB() {
        AtomicBoolean redeemed = new AtomicBoolean(false);
        ServiceException denied = assertThrows(ServiceException.class,
                () -> ShopAccess.useCoupon("1", 2L, () -> redeemed.set(true)));
        assertEquals(FORBIDDEN.getCode(), denied.getCode());
        assertFalse(redeemed.get());

        ServiceException universal = assertThrows(ServiceException.class,
                () -> ShopAccess.useCoupon("0", 2L, () -> redeemed.set(true)));
        assertEquals(FORBIDDEN.getCode(), universal.getCode());

        ServiceException shared = assertThrows(ServiceException.class,
                () -> ShopAccess.useCoupon("1,2", 2L, () -> redeemed.set(true)));
        assertEquals(FORBIDDEN.getCode(), shared.getCode());
        assertFalse(redeemed.get());

        ShopAccess.useCoupon("2", 2L, () -> redeemed.set(true));
        assertTrue(redeemed.get());
    }

    @Test
    void orderOfOtherShopIsForbidden() {
        ServiceException denied = assertThrows(ServiceException.class,
                () -> ShopAccess.assertSameShop(2L, ShopAccess.requirePositiveShopId(1)));
        assertEquals(FORBIDDEN.getCode(), denied.getCode());
        ShopAccess.assertSameShop(1L, 1L);
    }

}
