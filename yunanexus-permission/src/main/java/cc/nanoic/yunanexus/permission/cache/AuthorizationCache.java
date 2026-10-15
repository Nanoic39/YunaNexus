package cc.nanoic.yunanexus.permission.cache;

import cc.nanoic.yunanexus.permission.config.PermissionCacheProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 鉴权结果缓存（进程内，带 TTL）。<p>
 *
 * 仅缓存「无 ABAC 上下文」的判定结果；管理后台任意写操作经
 * {@code AdminCacheEvictFilter} 触发 {@link #clear()}，叠加短 TTL 双重兜底，
 * 保证权限变更能及时生效。
 *
 * @author yunanexus
 */
@Component
public class AuthorizationCache {

    private final boolean enabled;
    private final long ttlMillis;
    private final int maxSize;
    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    public AuthorizationCache(PermissionCacheProperties properties) {
        this.enabled = properties.isEnabled();
        this.ttlMillis = Math.max(1, properties.getTtlSeconds()) * 1000L;
        this.maxSize = Math.max(1, properties.getMaxSize());
    }

    /**
     * 读取缓存。
     *
     * @param key 缓存键
     * @return 命中的布尔结果；未命中或已过期返回 {@code null}
     */
    public Boolean get(String key) {
        if (!enabled) {
            return null;
        }
        Entry entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expireAt() < System.currentTimeMillis()) {
            store.remove(key);
            return null;
        }
        return entry.value();
    }

    public void put(String key, boolean value) {
        if (!enabled) {
            return;
        }
        if (store.size() >= maxSize) {
            store.clear();
        }
        store.put(key, new Entry(value, System.currentTimeMillis() + ttlMillis));
    }

    /** 清空缓存（权限数据变更时调用）。 */
    public void clear() {
        store.clear();
    }

    private record Entry(boolean value, long expireAt) {
    }
}
