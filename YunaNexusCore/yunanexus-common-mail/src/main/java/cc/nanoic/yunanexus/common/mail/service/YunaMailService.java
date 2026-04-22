package cc.nanoic.yunanexus.common.mail.service;

import cc.nanoic.yunanexus.common.mail.enums.MailTemplateType;

import java.util.Map;

public interface YunaMailService {
    void sendByType(String to, MailTemplateType type, Map<String, Object> params);
    void sendCustom(String to, String templateKey, Map<String, Object> params);
}
