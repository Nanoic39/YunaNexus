package cc.nanoic.yunanexus.common.rocketmq.service;

public interface YunaRocketMQService {

    /**
     * 统一发送事件消息
     * @param topic 主题
     * @param tag 标签
     * @param key 业务唯一键
     * @param payload Json字符串
     */
    void send(String topic, String tag, String key, String payload);

}
