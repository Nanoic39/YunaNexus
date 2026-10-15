package cc.nanoic.yunanexus.user.service;

import cc.nanoic.yunanexus.user.domain.UserTagView;

import java.util.List;

/**
 * 用户标签服务（roadmap 5.9）。
 *
 * @author yunanexus
 */
public interface UserTagService {

    /**
     * 查询用户标签。
     *
     * @param userId 全局用户 ID
     * @return 标签集合（按创建顺序）
     */
    List<UserTagView> listTags(long userId);

    /**
     * 覆盖式设置用户标签（整体替换）。
     *
     * @param userId 全局用户 ID
     * @param tags   标签集合
     */
    void setTags(long userId, List<UserTagView> tags);
}
