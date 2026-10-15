package cc.nanoic.yunanexus.permission.domain;

import lombok.Data;

import java.util.List;

/**
 * 字段级校验结果（供业务序列化层决定字段可读 / 可写与掩码）。<p>
 *
 * 语义：{@code restricted=false} 时无字段级限制（全部字段放行）；
 * {@code restricted=true} 时仅 {@code allowedFields} 内字段放行，其余应屏蔽 / 掩码。
 *
 * @author yunanexus
 */
@Data
public class FieldCheckResponse {

    /** 全局用户 ID */
    private Long userId;

    /** 资源类型 */
    private String resourceType;

    /** 访问类型：1-读 2-写 */
    private Integer access;

    /** 是否存在字段级限制（该资源类型 / 访问类型下存在命中规则） */
    private boolean restricted;

    /** 允许的字段集合（{@code restricted=true} 时有效） */
    private List<String> allowedFields;

    public static FieldCheckResponse of(long userId, String resourceType, int access,
                                        boolean restricted, List<String> allowedFields) {
        FieldCheckResponse response = new FieldCheckResponse();
        response.setUserId(userId);
        response.setResourceType(resourceType);
        response.setAccess(access);
        response.setRestricted(restricted);
        response.setAllowedFields(allowedFields == null ? List.of() : allowedFields);
        return response;
    }
}
