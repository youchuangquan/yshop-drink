package co.yixiang.yshop.module.score.controller.admin.scoreorder.vo;

import lombok.*;
import java.util.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "管理后台 - 积分商城订单创建 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ScoreOrderCreateReqVO extends ScoreOrderBaseVO {

    @Schema(description = "稳定请求键。同一个键重复提交返回已有订单。不传不是防重；空字符串会拒绝")
    private String requestKey;

}
