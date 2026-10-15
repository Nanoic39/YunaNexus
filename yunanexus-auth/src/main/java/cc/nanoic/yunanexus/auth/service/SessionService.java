package cc.nanoic.yunanexus.auth.service;

import cc.nanoic.yunanexus.auth.domain.SessionInfo;

import java.util.Collection;
import java.util.List;

/**
 * 会话服务（Redis，ADR-003）。<p>
 *
 * 负责会话的建立、校验、登出与「踢人」，并维护反向索引 {@code user_sessions:{userId}}
 * 支持单点登出。存储模型：{@code auth:{sid}:session:v2} → JSON(SessionInfo)，
 * 撤销集 {@code auth:{sid}:revoked:v2} → ZSET(jti → 绝对过期毫秒)。
 *
 * @author yunanexus
 */
public interface SessionService {

    /**
     * 建立会话并登记反向索引。<p>
     *
     * 会话 key 与反向索引成员的 TTL 均与会话有效期对齐，随刷新令牌滑动续期。
     *
     * @param session 会话信息
     */
    void create(SessionInfo session);

    /**
     * 按会话 ID 读取会话。
     *
     * @param sid 会话 ID
     * @return 会话信息，不存在返回 null
     */
    SessionInfo get(String sid);

    /**
     * 删除单一会话（单端登出），并同步移除反向索引。
     *
     * @param sid 会话 ID
     */
    void remove(String sid);

    /**
     * 列出用户全部会话 ID（反向索引读取）。
     *
     * @param userId 全局内部用户 ID
     * @return 会话 ID 集合
     */
    List<String> listSidsByUser(long userId);

    /**
     * 踢出用户全部会话（单点登出），逐一会话撤销令牌并清理索引。
     *
     * @param userId 全局内部用户 ID
     * @return 被踢出的会话数
     */
    int kickAll(long userId);

    /**
     * 轮转会话令牌（刷新令牌轮转时调用）。<p>
     *
     * 原子校验当前 refresh jti 等于期望值，将当前 access/refresh jti 写入撤销集，
     * 并用更新后的会话信息覆盖 session key。会话不存在、已过期或 jti 不匹配返回 false。
     *
     * @param sid                 会话 ID
     * @param expectedRefreshJti  期望的当前 refresh jti
     * @param updated             更新后的会话信息（包含新 jti 与新过期时间）
     * @return true 表示轮转成功，false 表示会话不存在/已消费/已过期
     */
    boolean rotate(String sid, String expectedRefreshJti, SessionInfo updated);
}
