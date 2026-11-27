package co.yixiang.yshop.module.store.service.shopoption;

import co.yixiang.yshop.module.store.controller.admin.storeshop.vo.ShopOptionItemVO;

import java.util.List;

public interface ShopOptionService {

    List<ShopOptionItemVO> listByShop(Long shopId);

    /**
     * items 为 null 时保留原配置。空列表表示清空本店选项，不会动另一家店。
     */
    void replace(Long shopId, List<ShopOptionItemVO> items);

}
