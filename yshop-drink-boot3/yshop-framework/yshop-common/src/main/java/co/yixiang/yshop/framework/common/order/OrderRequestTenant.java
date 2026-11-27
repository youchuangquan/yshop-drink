package co.yixiang.yshop.framework.common.order;

/**
 * 请求键唯一索引里的租户槽。
 * MySQL 不把 NULL 看成相同值，租户关闭时如果 tenant_id 留空，同一用户、同一店、同一请求键可以插进两笔。
 * 没有租户上下文时写入 0，唯一索引才能拦住并发的第二笔。已经有租户编号时原样保留。
 */
public final class OrderRequestTenant {

    public static final long CLOSED = 0L;

    private OrderRequestTenant() {
    }

    public static long slot(Long tenantId) {
        return tenantId == null ? CLOSED : tenantId;
    }

}
