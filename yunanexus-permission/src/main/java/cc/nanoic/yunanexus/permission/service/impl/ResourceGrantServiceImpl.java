package cc.nanoic.yunanexus.permission.service.impl;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.permission.domain.ResourceGrantSaveRequest;
import cc.nanoic.yunanexus.permission.entity.PermResourceGrant;
import cc.nanoic.yunanexus.permission.mapper.PermResourceGrantMapper;
import cc.nanoic.yunanexus.permission.service.ResourceGrantService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 资源实例级授权管理服务实现。
 *
 * @author yunanexus
 */
@Service
public class ResourceGrantServiceImpl implements ResourceGrantService {

    private final PermResourceGrantMapper grantMapper;

    public ResourceGrantServiceImpl(PermResourceGrantMapper grantMapper) {
        this.grantMapper = grantMapper;
    }

    @Override
    public PermResourceGrant create(ResourceGrantSaveRequest request) {
        if (exists(request, null)) {
            throw new BizException(ErrorCode.GRANT_EXISTS);
        }
        PermResourceGrant grant = new PermResourceGrant();
        apply(grant, request);
        grantMapper.insert(grant);
        return grantMapper.selectById(grant.getId());
    }

    @Override
    public PermResourceGrant update(long id, ResourceGrantSaveRequest request) {
        PermResourceGrant grant = requireGrant(id);
        if (exists(request, id)) {
            throw new BizException(ErrorCode.GRANT_EXISTS);
        }
        apply(grant, request);
        grantMapper.updateById(grant);
        return grantMapper.selectById(id);
    }

    @Override
    public void delete(long id) {
        requireGrant(id);
        grantMapper.deleteById(id);
    }

    @Override
    public PermResourceGrant getById(long id) {
        return requireGrant(id);
    }

    @Override
    public List<PermResourceGrant> list(Integer subjectType, Long subjectId, String resourceType, String resourceId) {
        return grantMapper.selectList(new LambdaQueryWrapper<PermResourceGrant>()
                .eq(subjectType != null, PermResourceGrant::getSubjectType, subjectType)
                .eq(subjectId != null, PermResourceGrant::getSubjectId, subjectId)
                .eq(resourceType != null, PermResourceGrant::getResourceType, resourceType)
                .eq(resourceId != null, PermResourceGrant::getResourceId, resourceId)
                .orderByAsc(PermResourceGrant::getResourceType)
                .orderByAsc(PermResourceGrant::getResourceId)
                .orderByAsc(PermResourceGrant::getId));
    }

    /** 唯一性判定：同一「主体 + 资源实例 + 动作」仅允许一条；{@code excludeId} 用于更新时排除自身。 */
    private boolean exists(ResourceGrantSaveRequest request, Long excludeId) {
        LambdaQueryWrapper<PermResourceGrant> wrapper = new LambdaQueryWrapper<PermResourceGrant>()
                .eq(PermResourceGrant::getSubjectType, request.getSubjectType())
                .eq(PermResourceGrant::getSubjectId, request.getSubjectId())
                .eq(PermResourceGrant::getResourceType, request.getResourceType())
                .eq(PermResourceGrant::getResourceId, request.getResourceId())
                .eq(PermResourceGrant::getAction, request.getAction());
        if (excludeId != null) {
            wrapper.ne(PermResourceGrant::getId, excludeId);
        }
        return grantMapper.selectCount(wrapper) > 0;
    }

    private void apply(PermResourceGrant grant, ResourceGrantSaveRequest request) {
        grant.setSubjectType(request.getSubjectType());
        grant.setSubjectId(request.getSubjectId());
        grant.setResourceType(request.getResourceType());
        grant.setResourceId(request.getResourceId());
        grant.setAction(request.getAction());
        grant.setEffect(request.getEffect());
        grant.setExpireTime(request.getExpireTime());
        grant.setStatus(request.getStatus() == null ? PermResourceGrant.STATUS_ENABLED : request.getStatus());
    }

    private PermResourceGrant requireGrant(long id) {
        PermResourceGrant grant = grantMapper.selectById(id);
        if (grant == null) {
            throw new BizException(ErrorCode.GRANT_NOT_FOUND);
        }
        return grant;
    }
}
