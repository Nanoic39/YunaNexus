package cc.nanoic.yunanexus.common.redis.service;

import java.time.Duration;
import java.util.Map;

public interface YunaRedisService {
    /**
     * 设置键值对(默认无过期时间)
     * @param key 键
     * @param value 值
     */
    void set(String key, String value);
    /**
     * 设置键值对(指定过期时间)
     * @param key 键
     * @param value 值
     * @param ttl 过期时间
     */
    void set(String key, String value, Duration ttl);
    /**
     * 获取键值对
     * @param key 键
     * @return 值
     */
    String get(String key);
    /**
     * 删除键值对
     * @param key 键
     * @return 是否删除成功
     */
    Boolean delete(String key);
    /**
     * 设置键值对过期时间
     * @param key 键
     * @param ttl 过期时间
     * @return 是否设置成功
     */
    Boolean expire(String key, Duration ttl);
    /**
     * 增加键值对的数值(默认增加1)
     * @param key 键
     * @return 增加后的数值
     */
    Long increment(String key);
    /**
     * 增加键值对的数值(指定增加值)
     * @param key 键
     * @param delta 增加值
     * @return 增加后的数值
     */
    Long increment(String key, long delta);
    /**
     * 设置哈希键值对
     * @param key 键
     * @param field 字段
     * @param value 值
     */
    void hSet(String key, String field, String value);
    /**
     * 获取哈希键值对
     * @param key 键
     * @param field 字段
     * @return 值
     */
    String hGet(String key, String field);
    /**
     * 获取所有哈希键值对
     * @param key 键
     * @return 所有哈希键值对
     */
    Map<Object, Object> hGetAll(String key);

    /**
     * 尝试获取锁
     * @param key 键
     * @param owner 锁所有者
     * @param ttl 锁过期时间
     * @return 是否获取锁成功
     */
    boolean tryLock(String key, String owner, Duration ttl);
    /**
     * 解锁
     * @param key 键
     * @param owner 锁所有者
     * @return 是否解锁成功
     */
    boolean unlock(String key, String owner);
    /**
     * 允许请求
     * @param key 键
     * @param max 最大请求数
     * @param window 时间窗口
     * @return 是否允许请求成功
     */
    boolean allowRequest(String key, long max, Duration window);
}
