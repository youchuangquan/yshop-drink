package co.yixiang.yshop.framework.common.order;

import java.util.List;

/**
 * 取餐号在付款之后才发。未付款返回空，不占号。
 * 序号按店递增，另一家店的号不参与。
 */
public final class QueueTickets {

    private QueueTickets() {
    }

    public static final class Ticket {
        public final long shopId;
        public final String orderId;
        public final int number;

        public Ticket(long shopId, String orderId, int number) {
            this.shopId = shopId;
            this.orderId = orderId;
            this.number = number;
        }
    }

    /**
     * @param paid           这一次付款已经入账
     * @param existingNumber 订单上已经有的号。有号就原样返回，不再发第二个
     * @param shopId         本店
     * @param shopTickets    调用方按本店查出的已发号。混进来的他店号会被丢掉
     * @return 未付款时是 null
     */
    public static Integer issue(boolean paid, Integer existingNumber, long shopId, List<Ticket> shopTickets) {
        if (!paid) {
            return null;
        }
        if (existingNumber != null && existingNumber > 0) {
            return existingNumber;
        }
        int max = 0;
        if (shopTickets != null) {
            for (Ticket ticket : shopTickets) {
                if (ticket == null || ticket.shopId != shopId || ticket.number <= 0) {
                    continue;
                }
                if (ticket.number > max) {
                    max = ticket.number;
                }
            }
        }
        return max + 1;
    }

}
