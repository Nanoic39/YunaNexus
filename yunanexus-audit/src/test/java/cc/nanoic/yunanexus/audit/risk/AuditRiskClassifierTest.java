package cc.nanoic.yunanexus.audit.risk;

import cc.nanoic.yunanexus.audit.entity.AuditLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 高危动作判定口径。
 *
 * <p>规则表即审计语义：宁可少报，也不能把常规读写报上来——刷屏的告警等于没告警。
 */
class AuditRiskClassifierTest {

    private static AuditLog log(String module, String action, String method, String uri, Integer status) {
        AuditLog log = new AuditLog();
        log.setModule(module);
        log.setService("gateway");
        log.setAction(action);
        log.setMethod(method);
        log.setUri(uri);
        log.setStatus(status == null ? AuditLog.STATUS_SUCCESS : status);
        return log;
    }

    @Test
    @DisplayName("常规读操作不通知")
    void readIsSilent() {
        assertEquals(AuditRiskClassifier.Level.NONE,
                AuditRiskClassifier.classify(log("user", "QUERY", "GET", "/api/v1/users", null)).level());
    }

    @Test
    @DisplayName("常规非破坏性写入不通知")
    void normalWriteIsSilent() {
        assertEquals(AuditRiskClassifier.Level.NONE,
                AuditRiskClassifier.classify(log("notice", "UPDATE", "PUT", "/api/v1/notices/1", null)).level());
    }

    @Test
    @DisplayName("导出审计日志判为严重")
    void auditExportIsCritical() {
        AuditRiskClassifier.Risk risk = AuditRiskClassifier.classify(
                log("audit", "QUERY", "GET", "/api/v1/audit/logs/export.csv", null));
        assertEquals(AuditRiskClassifier.Level.CRITICAL, risk.level());
        assertEquals("audit_export", risk.rule());
    }

    @Test
    @DisplayName("授权面写入判为严重，只读不报")
    void authzWriteOnly() {
        assertEquals(AuditRiskClassifier.Level.CRITICAL, AuditRiskClassifier.classify(
                log("permission", "GRANT", "POST", "/api/v1/permissions/grant", null)).level());
        assertEquals(AuditRiskClassifier.Level.NONE, AuditRiskClassifier.classify(
                log("role", "QUERY", "GET", "/api/v1/roles", null)).level());
    }

    @Test
    @DisplayName("凭据变更为高风险")
    void credentialChangeIsHigh() {
        AuditRiskClassifier.Risk risk = AuditRiskClassifier.classify(
                log("user", "UPDATE", "PUT", "/api/v1/users/7/password", null));
        assertEquals(AuditRiskClassifier.Level.HIGH, risk.level());
        assertEquals("credential_change", risk.rule());
    }

    @Test
    @DisplayName("删除类写入为高风险")
    void destructiveWriteIsHigh() {
        assertEquals("destructive_write", AuditRiskClassifier.classify(
                log("user", "DELETE", "DELETE", "/api/v1/users/7", null)).rule());
    }

    @Test
    @DisplayName("越权被拒为高风险")
    void accessDeniedIsHigh() {
        assertEquals("access_denied", AuditRiskClassifier.classify(
                log("user", "QUERY", "GET", "/api/v1/users/9/forbidden", AuditLog.STATUS_FAILED)).rule());
    }

    @Test
    @DisplayName("空记录不判风险")
    void nullIsSafe() {
        assertEquals(AuditRiskClassifier.Level.NONE, AuditRiskClassifier.classify(null).level());
    }
}
