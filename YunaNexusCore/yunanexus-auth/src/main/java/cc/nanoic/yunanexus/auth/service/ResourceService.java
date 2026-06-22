package cc.nanoic.yunanexus.auth.service;

import cc.nanoic.yunanexus.auth.entity.ResourceEntity;
import cc.nanoic.yunanexus.auth.entity.VO.ResourceVO;
import cc.nanoic.yunanexus.auth.mapper.ResourceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ResourceService {

    @Resource
    private ResourceMapper resourceMapper;

    /**
     * 构建用户可见的菜单树（type=0目录 + type=1菜单，visible=1）
     */
    public List<ResourceVO> buildMenuTree(Set<String> userPermissions) {
        List<ResourceEntity> all = resourceMapper.selectList(
                new LambdaQueryWrapper<ResourceEntity>()
                        .in(ResourceEntity::getType, 0, 1)
                        .eq(ResourceEntity::getVisible, 1)
                        .orderByAsc(ResourceEntity::getSortNo));

        return buildTree(all, userPermissions, 0L);
    }

    /**
     * 获取用户拥有的按钮权限码列表（type=2，visible=1）
     */
    public List<String> getUserButtonCodes(Set<String> userPermissions) {
        List<ResourceEntity> buttons = resourceMapper.selectList(
                new LambdaQueryWrapper<ResourceEntity>()
                        .eq(ResourceEntity::getType, 2)
                        .eq(ResourceEntity::getVisible, 1)
                        .orderByAsc(ResourceEntity::getSortNo));

        if (userPermissions.contains("*:*:*:*")) {
            return buttons.stream()
                    .map(ResourceEntity::getCode)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        return buttons.stream()
                .filter(r -> r.getCode() == null || userPermissions.contains(r.getCode()))
                .map(ResourceEntity::getCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<ResourceVO> buildTree(List<ResourceEntity> all, Set<String> perms, Long parentId) {
        List<ResourceVO> result = new ArrayList<>();
        for (ResourceEntity r : all) {
            if (!r.getParentId().equals(parentId)) {
                continue;
            }

            // code为null = 所有人可见；否则需要权限
            if (r.getCode() != null && !perms.contains(r.getCode())) {
                // 没有 *:*:*:* 超级权限时需检查
                if (!perms.contains("*:*:*:*")) {
                    continue;
                }
            }

            ResourceVO vo = toVO(r);
            vo.setChildren(buildTree(all, perms, r.getId()));
            result.add(vo);
        }
        return result;
    }

    private ResourceVO toVO(ResourceEntity r) {
        ResourceVO vo = new ResourceVO();
        vo.setId(r.getId());
        vo.setName(r.getName());
        vo.setCode(r.getCode());
        vo.setType(r.getType());
        vo.setIcon(r.getIcon());
        vo.setPath(r.getPath());
        vo.setRedirect(r.getRedirect());
        vo.setComponent(r.getComponent());
        vo.setSortNo(r.getSortNo());
        return vo;
    }
}
