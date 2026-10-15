package cc.nanoic.yunanexus.file.client;

import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.file.domain.DataScopeView;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 权限中心（yunanexus-permission）内部调用客户端（OpenFeign）。<p>
 *
 * 文件中心据此解析：<br>
 * 1）上传时写入 {@code file_meta.dept_id}（用户所属部门）；<br>
 * 2）列表查询时的数据范围（L4 行级权限试点）。<br>
 * 服务名 {@code yunanexus-permission} 经 Nacos 服务发现 + LoadBalancer 解析。
 *
 * @author yunanexus
 */
@FeignClient(name = "yunanexus-permission", contextId = "permissionClient")
public interface PermissionClient {

    /**
     * 解析指定用户的数据范围。
     *
     * @param userId 全局用户 ID
     * @return 数据范围（包装于 Result）
     */
    @GetMapping("/internal/permission/data-scope")
    Result<DataScopeView> dataScope(@RequestParam("userId") long userId);

    /**
     * 查询用户所属部门（用于上传时填充 {@code dept_id}）。
     *
     * @param userId 全局用户 ID
     * @return 部门 ID；未绑定部门返回 {@code null}
     */
    @GetMapping("/internal/permission/user-dept")
    Result<Long> userDept(@RequestParam("userId") long userId);
}
