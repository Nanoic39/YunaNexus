package cc.nanoic.yunanexus.auth.service;

import cc.nanoic.yunanexus.auth.entity.AdminUserVO;
import cc.nanoic.yunanexus.auth.entity.Roles;
import cc.nanoic.yunanexus.auth.entity.UserRoles;
import cc.nanoic.yunanexus.auth.mapper.RolesMapper;
import cc.nanoic.yunanexus.auth.mapper.UserRolesMapper;
import cc.nanoic.yunanexus.common.web.common.BusinessException;
import cc.nanoic.yunanexus.common.web.common.R;
import cn.hutool.core.util.HexUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 管理员用户管理 Service —— 通过 UserRoles 表关联查询
 */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    @Resource
    private UserRolesMapper userRolesMapper;

    @Resource
    private RolesMapper rolesMapper;

    /**
     * 列出所有有角色绑定的用户（去重），支持按关键字搜索 globalId/uuid
     */
    public List<AdminUserVO> listUsers(String keyword, Integer statusFilter, Integer page, Integer size) {
        List<UserRoles> allBindings = userRolesMapper.selectList(
                new LambdaQueryWrapper<UserRoles>()
                        .eq(UserRoles::getStatus, 1));
        // 按 globalId 分组
        Map<String, List<UserRoles>> grouped = new LinkedHashMap<>();
        for (UserRoles ur : allBindings) {
            String hexGid = HexUtil.encodeHexStr(ur.getGlobalId());
            if (statusFilter != null) {
                // status 过滤需要查 users 表，暂不支持
            }
            grouped.computeIfAbsent(hexGid, k -> new ArrayList<>()).add(ur);
        }

        List<AdminUserVO> result = new ArrayList<>();
        for (Map.Entry<String, List<UserRoles>> entry : grouped.entrySet()) {
            String hexGid = entry.getKey();
            List<UserRoles> bindings = entry.getValue();

            if (keyword != null && !keyword.isEmpty()) {
                if (!hexGid.contains(keyword)) {
                    continue;
                }
            }

            AdminUserVO vo = new AdminUserVO();
            vo.setGlobalId(hexGid);
            // 通过 roleId 查询角色信息
            List<Map<String, Object>> userRoles = new ArrayList<>();
            for (UserRoles ur : bindings) {
                Roles role = rolesMapper.selectById(ur.getRoleId());
                if (role != null) {
                    Map<String, Object> roleInfo = new HashMap<>();
                    roleInfo.put("id", role.getId());
                    roleInfo.put("name", role.getName());
                    roleInfo.put("level", role.getLevel());
                    roleInfo.put("permissions", JSON.parseArray(role.getPermissions(), String.class));
                    roleInfo.put("status", role.getStatus());
                    userRoles.add(roleInfo);
                }
            }
            vo.setRoles(userRoles);
            vo.setStatus(1); // 有有效角色绑定即视为正常
            vo.setCreatedAt(bindings.get(0).getCreatedAt() != null ? bindings.get(0).getCreatedAt().toString() : null);
            result.add(vo);
        }

        // 简单分页
        int start = (page - 1) * size;
        if (start >= result.size()) return Collections.emptyList();
        int end = Math.min(start + size, result.size());
        return result.subList(start, end);
    }

    public long countUsers(String keyword, Integer statusFilter) {
        List<UserRoles> allBindings = userRolesMapper.selectList(
                new LambdaQueryWrapper<UserRoles>().eq(UserRoles::getStatus, 1));
        Set<String> gids = new HashSet<>();
        for (UserRoles ur : allBindings) {
            String hexGid = HexUtil.encodeHexStr(ur.getGlobalId());
            if (keyword != null && !keyword.isEmpty() && !hexGid.contains(keyword)) {
                continue;
            }
            gids.add(hexGid);
        }
        return gids.size();
    }

    /**
     * 分配用户角色
     */
    @Transactional
    public void assignRoles(String hexGlobalId, List<Long> roleIds) {
        byte[] globalId = HexUtil.decodeHex(hexGlobalId);
        // 清除现有绑定
        userRolesMapper.update(null,
                new LambdaUpdateWrapper<UserRoles>()
                        .eq(UserRoles::getGlobalId, globalId)
                        .set(UserRoles::getStatus, 2));
        // 创建新绑定
        for (Long roleId : roleIds) {
            UserRoles ur = new UserRoles();
            ur.setGlobalId(globalId);
            ur.setRoleId(roleId);
            ur.setStatus(1);
            ur.setCreatedAt(java.time.LocalDateTime.now());
            userRolesMapper.insert(ur);
        }
    }

    /**
     * 更新用户状态（通过 UserRoles 状态控制）
     * 状态: 0=注销, 1=正常, 2=封禁, 3=冻结
     */
    @Transactional
    public void updateUserStatus(String hexGlobalId, Integer status) {
        byte[] globalId = HexUtil.decodeHex(hexGlobalId);
        if (status != null) {
            // 非正常状态：取消所有角色绑定
            if (status != 1) {
                userRolesMapper.update(null,
                        new LambdaUpdateWrapper<UserRoles>()
                                .eq(UserRoles::getGlobalId, globalId)
                                .set(UserRoles::getStatus, status));
            }
        }
    }
}
