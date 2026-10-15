package cc.nanoic.yunanexus.auth.client;

import cc.nanoic.yunanexus.auth.entity.AuthRegisterOutbox;
import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * {@link UserProfileSyncClient} 的 Feign 实现（阶段 5 用户中心落地后启用）。<p>
 *
 * 将 outbox 记录映射为用户中心内部接口请求；响应非成功码时抛出异常，交由补偿任务
 * 按指数退避重试（ADR-021）。
 *
 * @author yunanexus
 */
@Component
public class FeignUserProfileSyncClient implements UserProfileSyncClient {

    private final UserProfileSyncFeignClient feignClient;

    public FeignUserProfileSyncClient(UserProfileSyncFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    @Override
    public void createUserProfile(AuthRegisterOutbox outbox) {
        UserProfileCreateRequest request = new UserProfileCreateRequest();
        request.setUserId(outbox.getUserId());
        request.setShardIndex(outbox.getShardIndex());
        request.setExternalId(outbox.getExternalId());
        request.setUsername(outbox.getUsername());
        request.setEmail(outbox.getEmail());
        request.setMobileCountry(outbox.getMobileCountry());
        request.setMobile(outbox.getMobile());

        Result<Integer> result = feignClient.create(request);
        requireSuccess(result, "创建用户资料");
    }

    @Override
    public boolean exists(long userId) {
        Result<UserProfileView> result = feignClient.byUserId(userId);
        requireSuccess(result, "回查用户资料");
        return result.getData() != null;
    }

    @Override
    public UserProfileView getProfile(long userId) {
        Result<UserProfileView> result = feignClient.byUserId(userId);
        requireSuccess(result, "查询用户资料");
        return result.getData();
    }

    private void requireSuccess(Result<?> result, String action) {
        if (result == null || result.getCode() != ErrorCode.SUCCESS.getCode()) {
            throw new IllegalStateException("用户中心" + action + "失败: "
                    + (result == null ? "无响应" : result.getCode() + " " + result.getMsg()));
        }
    }
}
