package cc.nanoic.yunanexus.notice.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知发送请求。
 *
 * @author yunanexus
 */
@Data
public class NoticeSendRequest {

    /** 模板编码 */
    @NotBlank(message = "模板编码不能为空")
    private String templateCode;

    /** 通道（可选，缺省取模板配置的通道）：1-站内信 2-邮件 3-短信 */
    private Integer channel;

    /** 接收方列表：站内信为用户 ID，邮件为邮箱，短信为手机号 */
    @NotEmpty(message = "接收方不能为空")
    private List<String> receivers;

    /** 模板参数（用于渲染 ${占位符}） */
    private Map<String, String> params = new HashMap<>();

    /** 延迟等级（可选，覆盖默认）：1s 5s 10s 30s 1m ...，<=0 表示立即 */
    private Integer delayLevel;
}
