package cc.nanoic.yunanexus.notice.service;

import cc.nanoic.yunanexus.notice.mapper.NoticeDedupMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 通知频控：同一用户同一模板在窗口内只发一次。
 *
 * @author yunanexus
 */
@Slf4j
@Service
public class NoticeDedupService {

    private final NoticeDedupMapper dedupMapper;

    public NoticeDedupService(NoticeDedupMapper dedupMapper) {
        this.dedupMapper = dedupMapper;
    }

    /**
     * 判断本条是否应投递。
     *
     * @param userId     接收人
     * @param category   模板代码
     * @param windowSecs 窗口秒数（<=0 表示不去重）
     * @return 应投递返回 true
     */
    public boolean shouldDeliver(long userId, String category, int windowSecs) {
        if (windowSecs <= 0 || userId <= 0 || category == null) {
            return true;
        }
        long windowStart = (System.currentTimeMillis() / (windowSecs * 1000L)) * (windowSecs * 1000L);
        int acquired = dedupMapper.tryAcquire(userId, category, windowStart);
        if (acquired == 1 && System.nanoTime() % 97 == 0) {
            dedupMapper.purgeBefore(windowStart - 3L * windowSecs * 1000L);
        }
        return acquired == 1;
    }
}
