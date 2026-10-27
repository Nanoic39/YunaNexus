package cc.nanoic.yunanexus.audit.domain;

import java.time.LocalDateTime;

/**
 * 审计检索条件。<p>
 *
 * 设计要点：
 * <ul>
 *   <li><b>一律做规范化</b>：空白串转 {@code null}、超长截断。否则前端传个空串就会
 *       让 SQL 多出一个 {code = ''} 的死条件，查不出数据还很难排查。</li>
 *   <li><b>关键字转义 LIKE 通配符</b>：用户输入的 {@code %} / {@code _} 必须是字面量，
 *       否则一个 {@code %} 就能把分片库扫一遍。</li>
 *   <li><b>时间区间做方向校验</b>：起止颠倒属于参数错误，应在入口拒绝而不是查出空结果。</li>
 * </ul>
 *
 * @author yunanexus
 */
public class AuditQueryFilter {

    /** LIKE 关键字最大长度（超出截断，避免超长 pattern） */
    private static final int MAX_KEYWORD = 64;
    /** 各字符串条件最大长度 */
    private static final int MAX_TEXT = 64;

    private Long userId;
    private String username;
    private String service;
    private String module;
    private String action;
    private Integer status;
    private String ip;
    /** 模糊匹配 uri / params / error_msg */
    private String keyword;
    private LocalDateTime start;
    private LocalDateTime end;

    /** LIKE 转义字符 */
    private static final char ESCAPE = '\\';

    private static String clean(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    /**
     * 把 LIKE 通配符转义成字面量。
     *
     * @param value 原始关键字
     * @return 转义后的关键字；空值为 {@code null}
     */
    static String escapeLike(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder(value.length() + 8);
        for (char ch : value.toCharArray()) {
            if (ch == '%' || ch == '_' || ch == ESCAPE) {
                builder.append(ESCAPE);
            }
            builder.append(ch);
        }
        return builder.toString();
    }

    /**
     * 规范化所有条件；调用方拿到的对象即可直接拼 SQL。
     *
     * @return 自身（便于链式调用）
     * @throws IllegalArgumentException 时间区间起止颠倒
     */
    public AuditQueryFilter normalize() {
        username = clean(username, MAX_TEXT);
        service = clean(service, MAX_TEXT);
        module = clean(module, MAX_TEXT);
        action = clean(action, MAX_TEXT);
        ip = clean(ip, MAX_TEXT);
        keyword = escapeLike(clean(keyword, MAX_KEYWORD));
        if (start != null && end != null && start.isAfter(end)) {
            throw new IllegalArgumentException("start_after_end");
        }
        return this;
    }

    /** 是否带任何条件（用于"全量导出"风控判断） */
    public boolean isEmpty() {
        return userId == null && username == null && service == null && module == null
                && action == null && status == null && ip == null && keyword == null
                && start == null && end == null;
    }

    public Long getUserId() {
        return userId;
    }

    public AuditQueryFilter setUserId(Long userId) {
        this.userId = userId;
        return this;
    }

    public String getUsername() {
        return username;
    }

    public AuditQueryFilter setUsername(String username) {
        this.username = username;
        return this;
    }

    public String getService() {
        return service;
    }

    public AuditQueryFilter setService(String service) {
        this.service = service;
        return this;
    }

    public String getModule() {
        return module;
    }

    public AuditQueryFilter setModule(String module) {
        this.module = module;
        return this;
    }

    public String getAction() {
        return action;
    }

    public AuditQueryFilter setAction(String action) {
        this.action = action;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public AuditQueryFilter setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public String getIp() {
        return ip;
    }

    public AuditQueryFilter setIp(String ip) {
        this.ip = ip;
        return this;
    }

    public String getKeyword() {
        return keyword;
    }

    public AuditQueryFilter setKeyword(String keyword) {
        this.keyword = keyword;
        return this;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public AuditQueryFilter setStart(LocalDateTime start) {
        this.start = start;
        return this;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    public AuditQueryFilter setEnd(LocalDateTime end) {
        this.end = end;
        return this;
    }
}
