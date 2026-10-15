package cc.nanoic.yunanexus.permission.service.impl;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.permission.domain.ResourceGrantSaveRequest;
import cc.nanoic.yunanexus.permission.entity.PermResourceGrant;
import cc.nanoic.yunanexus.permission.mapper.PermResourceGrantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ResourceGrantServiceImpl} 单元测试。
 *
 * @author yunanexus
 */
class ResourceGrantServiceImplTest {

    private PermResourceGrantMapper grantMapper;
    private ResourceGrantServiceImpl service;

    @BeforeEach
    void setUp() {
        grantMapper = mock(PermResourceGrantMapper.class);
        service = new ResourceGrantServiceImpl(grantMapper);
    }

    private ResourceGrantSaveRequest request() {
        ResourceGrantSaveRequest request = new ResourceGrantSaveRequest();
        request.setSubjectType(PermResourceGrant.SUBJECT_USER);
        request.setSubjectId(10001L);
        request.setResourceType("file");
        request.setResourceId("100");
        request.setAction("read");
        request.setEffect(PermResourceGrant.EFFECT_ALLOW);
        return request;
    }

    private void stubInsertAssignsId(long id) {
        when(grantMapper.insert(any(PermResourceGrant.class))).thenAnswer(invocation -> {
            PermResourceGrant inserted = invocation.getArgument(0);
            inserted.setId(id);
            return 1;
        });
    }

    @Test
    void 新增授权默认启用() {
        when(grantMapper.selectCount(any())).thenReturn(0L);
        stubInsertAssignsId(1L);
        PermResourceGrant saved = new PermResourceGrant();
        saved.setId(1L);
        saved.setStatus(PermResourceGrant.STATUS_ENABLED);
        when(grantMapper.selectById(1L)).thenReturn(saved);

        PermResourceGrant result = service.create(request());

        assertEquals(1L, result.getId());
        assertEquals(PermResourceGrant.STATUS_ENABLED, result.getStatus());
    }

    @Test
    void 新增授权主体资源动作重复抛出业务异常() {
        when(grantMapper.selectCount(any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> service.create(request()));
        assertEquals(ErrorCode.GRANT_EXISTS.getCode(), ex.getCode());
        verify(grantMapper, never()).insert(any(PermResourceGrant.class));
    }

    @Test
    void 更新不存在的授权抛出业务异常() {
        when(grantMapper.selectById(9L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.update(9L, request()));
        assertEquals(ErrorCode.GRANT_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void 更新为重复三元组抛出业务异常() {
        PermResourceGrant existing = new PermResourceGrant();
        existing.setId(1L);
        when(grantMapper.selectById(1L)).thenReturn(existing);
        when(grantMapper.selectCount(any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> service.update(1L, request()));
        assertEquals(ErrorCode.GRANT_EXISTS.getCode(), ex.getCode());
    }

    @Test
    void 删除不存在的授权抛出业务异常() {
        when(grantMapper.selectById(9L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.delete(9L));
        assertEquals(ErrorCode.GRANT_NOT_FOUND.getCode(), ex.getCode());
        verify(grantMapper, never()).deleteById(9L);
    }

    @Test
    void 条件查询透传Mapper() {
        List<PermResourceGrant> expected = List.of(new PermResourceGrant());
        when(grantMapper.selectList(any())).thenReturn(expected);

        List<PermResourceGrant> result = service.list(PermResourceGrant.SUBJECT_USER, 10001L, "file", "100");

        assertEquals(expected, result);
        verify(grantMapper).selectList(any());
    }
}
