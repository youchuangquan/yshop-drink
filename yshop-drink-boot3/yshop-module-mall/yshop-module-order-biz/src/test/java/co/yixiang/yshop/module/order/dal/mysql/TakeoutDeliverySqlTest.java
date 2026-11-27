package co.yixiang.yshop.module.order.dal.mysql;

import co.yixiang.yshop.framework.common.enums.OrderInfoEnum;
import co.yixiang.yshop.module.order.controller.admin.storeorder.StoreOrderController;
import co.yixiang.yshop.module.order.controller.admin.storeorder.vo.StoreOrderDeliveryStatusReqVO;
import co.yixiang.yshop.module.order.dal.mysql.storeorder.StoreOrderMapper;
import co.yixiang.yshop.module.order.dal.mysql.storeorderstatus.StoreOrderStatusMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TakeoutDeliverySqlTest {

    @Test
    void conditionalUpdateIsShopScopedAndDoesNotTouchPaymentOrAddress() throws Exception {
        String sql = StoreOrderMapper.class
                .getMethod("updateTakeoutDeliveryStatus", Long.class, Long.class, Integer.class, Integer.class)
                .getAnnotation(Update.class).value()[0].toLowerCase().replaceAll("\\s+", " ");
        int whereAt = sql.indexOf(" where ");
        String set = sql.substring(sql.indexOf(" set "), whereAt);
        String where = sql.substring(whereAt);

        assertTrue(set.contains("status"));
        assertFalse(set.contains("paid"));
        assertFalse(set.contains("pay_price"));
        assertFalse(set.contains("user_address"));
        assertFalse(set.contains("packing_price"));
        assertFalse(set.contains("total_postage"));
        assertFalse(set.contains("freight_price"));
        assertFalse(set.contains("coupon_price"));
        assertFalse(set.contains("flavor_price"));
        assertFalse(set.contains("addon_price"));
        assertFalse(set.contains("deduction_price"));
        assertTrue(where.contains("id = #{id}"));
        assertTrue(where.contains("shop_id = #{shopid}"));
        assertTrue(where.contains("status = #{fromstatus}"));
        assertTrue(where.contains("paid = 1"));
        assertTrue(where.contains("order_type = 'takeout'"));
        assertTrue(where.contains("refund_status = 0"));
        assertTrue(where.contains("deleted = 0"));
        assertEquals(1, OrderInfoEnum.PAY_STATUS_1.getValue());
        assertEquals(0, OrderInfoEnum.REFUND_STATUS_0.getValue());
    }

    @Test
    void deliveryStampLookupIsBoundToOneOrder() throws Exception {
        String sql = StoreOrderStatusMapper.class
                .getMethod("selectByOrderAndType", Long.class, String.class)
                .getAnnotation(Select.class).value()[0].toLowerCase();
        assertTrue(sql.contains("oid = #{oid}"));
        assertTrue(sql.contains("change_type = #{changetype}"));
        assertTrue(sql.contains("order by id"));
        assertFalse(sql.contains("select *"));
    }

    @Test
    void deliveryEndpointReusesOrderUpdatePermission() throws Exception {
        Method method = StoreOrderController.class.getMethod("updateDeliveryStatus", StoreOrderDeliveryStatusReqVO.class);
        assertEquals("/delivery-status", method.getAnnotation(PutMapping.class).value()[0]);
        assertEquals("@ss.hasPermission('order:store-order:update')",
                method.getAnnotation(PreAuthorize.class).value());
        assertEquals("/order/store-order",
                StoreOrderController.class.getAnnotation(RequestMapping.class).value()[0]);
        assertTrue(StoreOrderController.class.getName().contains(".controller.admin."));
        assertTrue(method.getGenericReturnType().getTypeName().contains("StoreOrderRespVO"));
        assertFalse(method.getGenericReturnType().getTypeName().contains("Boolean"));
    }

}
