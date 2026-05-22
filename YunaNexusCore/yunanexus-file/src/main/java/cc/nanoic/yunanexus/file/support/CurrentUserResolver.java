package cc.nanoic.yunanexus.file.support;

import cc.nanoic.yunanexus.common.web.common.BusinessException;
import cc.nanoic.yunanexus.common.web.common.R;
import cc.nanoic.yunanexus.common.web.common.Result;
import cc.nanoic.yunanexus.file.client.AuthInternalClient;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
public class CurrentUserResolver {

    @Resource
    private AuthInternalClient authInternalClient;

    /**
     * UserId
     * @param authorization AccessToken
     * @return UserId
     */
    public Long requireUserId(String authorization) {
        Long userId = resolveUserId(authorization);
        if (userId == null) {
            throw new BusinessException(R.NOT_LOGIN, "无法解析当前用户");
        }

        return userId;
    }

    public Long resolveUserId(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return null;
        }
        Result<Map<String, Object>> result = authInternalClient.parse(authorization);
        if (result == null || result.getCode() != 200 || result.getData() == null) {
            return null;
        }
        return parseLong(result.getData().get("userId"));
    }


    /**
     * 解析userId
     * @param value Obj类型值
     * @return Long类型的结果
     */
    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}
