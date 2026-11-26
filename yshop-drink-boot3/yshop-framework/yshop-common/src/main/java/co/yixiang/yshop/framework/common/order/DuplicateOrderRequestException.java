package co.yixiang.yshop.framework.common.order;

/**
 * 同一个稳定键已经有订单。调用方应退回已有订单，而不是再插入一笔。
 */
public class DuplicateOrderRequestException extends RuntimeException {

    public DuplicateOrderRequestException() {
    }

    public DuplicateOrderRequestException(Throwable cause) {
        super(cause);
    }

}
