package cc.nanoic.yunanexus.common.redis.service.impl;

import cc.nanoic.yunanexus.common.redis.service.YunaRedisService;
import org.redisson.api.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class YunaRedisServiceImpl implements YunaRedisService {
    private final RedissonClient redissonClient;

    public YunaRedisServiceImpl(RedissonClient redissonCli) {
        this.redissonClient = redissonCli;
    }

    /**
     * 设置键值对
     * 
     * @param key   键
     * @param value 值
     */
    @Override
    public void set(String key, String value) {
        redissonClient.<String>getBucket(key).set(value);
    }

    /**
     * 设置键值对
     * 
     * @param key   键
     * @param value 值
     * @param ttl   过期时间
     */
    @Override
    public void set(String key, String value, Duration ttl) {
        redissonClient.<String>getBucket(key).set(value, ttl);
    }

    /**
     * 根据键获取值
     * 
     * @param key 键
     * @return 值
     */
    @Override
    public String get(String key) {
        return redissonClient.<String>getBucket(key).get();
    }

    /**
     * 删除键值对
     * 
     * @param key 键
     * @return 是否删除成功
     */
    @Override
    public Boolean delete(String key) {
        return redissonClient.getKeys().delete(key) > 0;
    }

    /**
     * 设置键值对过期时间
     * 
     * @param key 键
     * @param ttl 过期时间
     * @return 是否设置成功
     */
    @Override
    public Boolean expire(String key, Duration ttl) {
        return redissonClient.getKeys().expire(ttl, key) > 0;
    }

    /**
     * 自动增量
     * 
     * @param key 键
     * @return 增量后的值
     */
    @Override
    public Long increment(String key) {
        RAtomicLong atomicLong = redissonClient.getAtomicLong(key);
        return atomicLong.incrementAndGet();
    }

    /**
     * 自动增量
     * 
     * @param key   键
     * @param delta 增量值
     * @return 增量后的值
     */
    @Override
    public Long increment(String key, long delta) {
        RAtomicLong atomicLong = redissonClient.getAtomicLong(key);
        return atomicLong.addAndGet(delta);
    }

    /**
     * 设置哈希表字段值
     * 
     * @param key   键
     * @param field 字段
     * @param value 值
     */
    @Override
    public void hSet(String key, String field, String value) {
        redissonClient.<String, String>getMap(key).put(field, value);
    }

    /**
     * 获取哈希表字段值
     * 
     * @param key   键
     * @param field 字段
     * @return 值
     */
    @Override
    public String hGet(String key, String field) {
        return redissonClient.<String, String>getMap(key).get(field);
    }

    /**
     * 获取哈希表所有字段值
     * 
     * @param key 键
     * @return 所有字段值
     */
    @Override
    public Map<Object, Object> hGetAll(String key) {
        RMap<String, String> map = redissonClient.getMap(key);
        return new HashMap<>(map.readAllMap());
    }

    /**
     * 尝试加锁
     * 
     * @param key   键
     * @param owner 锁所有者
     * @param ttl   锁过期时间
     * @return 是否加锁成功
     */
    @Override
    public boolean tryLock(String key, String owner, Duration ttl) {
        RLock lock = redissonClient.getLock(key + ":" + owner);
        try {
            return lock.tryLock(0, ttl.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 解锁
     * 
     * @param key   键
     * @param owner 锁所有者
     * @return 是否解锁成功
     */
    @Override
    public boolean unlock(String key, String owner) {
        RLock lock = redissonClient.getLock(key + ":" + owner);
        if (!lock.isHeldByCurrentThread()) {
            return false;
        }
        lock.unlock();
        return true;
    }

    /**
     * 执行任务并加锁
     * 
     * @param lockKey 锁键
     * @param task    任务
     */
    public void executeWithLock(String lockKey, Runnable task) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // 尝试加锁
            if (lock.tryLock(10, 60, TimeUnit.SECONDS)) {
                task.run();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取锁失败", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 允许请求
     * 
     * @param key    键
     * @param max    最大请求数
     * @param window 时间窗口
     * @return 是否允许请求
     */
    public boolean allowRequest(String key, long max, Duration window) {
        RRateLimiter limiter = redissonClient.getRateLimiter(key);
        limiter.trySetRate(RateType.OVERALL, max, window);
        // 尝试获取一个令牌
        return limiter.tryAcquire(1);
    }

}
