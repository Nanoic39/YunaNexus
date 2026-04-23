package cc.nanoic.yunanexus.common.mail.service;

import cc.nanoic.yunanexus.common.mail.enums.MailTemplateType;

import java.util.Map;

public interface YunaMailService {
    void sendMail(String to, MailTemplateType type, Map<String, Object> params);
}
