package cc.nanoic.yunanexus.auth.service;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** 自助接入申请与审核；凭证只保存摘要，列表从不返回明文。 */
@Service
public class OAuthApplicationService {
    private final JdbcTemplate db;

    public OAuthApplicationService(JdbcTemplate db) {
        this.db = db;
    }

    public record Save(
            String appName,
            String description,
            String logo,
            String websiteUrl,
            List<String> redirectUris,
            List<String> scopes) {}

    private static final Set<String> SCOPES =
            Set.of("profile:read", "email:read", "files:read", "files:write", "notice:read");
    private static final String SELECT =
            "SELECT a.id,a.client_code AS clientId,a.app_name AS"
                + " appName,a.description,a.logo,a.website_url AS websiteUrl,a.redirect_uris AS"
                + " redirectUris,a.scopes,a.owner_user_id AS ownerUserId,u.username AS"
                + " ownerName,a.status,a.audit_remark AS auditRemark,a.audit_time AS"
                + " auditTime,a.create_time AS createTime,a.update_time AS updateTime FROM"
                + " auth_oauth_application a JOIN auth_user u ON u.user_id=a.owner_user_id WHERE"
                + " a.deleted=0";

    private Map<String, Object> view(Map<String, Object> row) {
        var v = new LinkedHashMap<>(row);
        v.put("redirectUris", List.of(row.get("redirectUris").toString().split("\n")));
        v.put("scopes", List.of(row.get("scopes").toString().split(",")));
        v.replaceAll(
                (k, value) ->
                        value instanceof java.sql.Timestamp t
                                ? t.toLocalDateTime().toString()
                                : value);
        v.put("clientSecret", null);
        return v;
    }

    public List<Map<String, Object>> list(Long owner) {
        var rows =
                owner == null
                        ? db.queryForList(SELECT + " ORDER BY a.id DESC LIMIT 1000")
                        : db.queryForList(
                                SELECT + " AND a.owner_user_id=? ORDER BY a.id DESC LIMIT 100",
                                owner);
        return rows.stream().map(this::view).toList();
    }

    private Map<String, Object> require(long id, Long owner, boolean lock) {
        var rows =
                owner == null
                        ? db.queryForList(SELECT + " AND a.id=?" + (lock ? " FOR UPDATE" : ""), id)
                        : db.queryForList(
                                SELECT
                                        + " AND a.id=? AND a.owner_user_id=?"
                                        + (lock ? " FOR UPDATE" : ""),
                                id,
                                owner);
        if (rows.isEmpty()) throw new BizException(ErrorCode.NOT_FOUND, "应用不存在");
        return view(rows.getFirst());
    }

    public static void validate(Save b) {
        if (b == null
                || b.appName() == null
                || b.appName().isBlank()
                || b.appName().length() > 64
                || b.redirectUris() == null
                || b.redirectUris().isEmpty()
                || b.redirectUris().size() > 10
                || b.scopes() == null
                || b.scopes().isEmpty()
                || b.scopes().size() > 5
                || !SCOPES.containsAll(b.scopes())) throw bad("名称、回调地址或权限无效");
        if (b.description() != null && b.description().length() > 1000) throw bad("描述最多1000字");
        for (String uri : b.redirectUris()) safeUri(uri, true);
        if (b.websiteUrl() != null && !b.websiteUrl().isBlank()) safeUri(b.websiteUrl(), false);
        if (b.logo() != null && !b.logo().isBlank()) safeUri(b.logo(), false);
    }

    static void safeUri(String value, boolean callback) {
        try {
            URI u = URI.create(value);
            if (value.toLowerCase(Locale.ROOT).contains("%0d")
                    || value.toLowerCase(Locale.ROOT).contains("%0a")
                    || value.length() > 500
                    || value.chars().anyMatch(c -> c <= 32)
                    || u.getHost() == null
                    || u.getUserInfo() != null
                    || u.getFragment() != null
                    || !("https".equals(u.getScheme())
                            || ("http".equals(u.getScheme())
                                    && Set.of("localhost", "127.0.0.1").contains(u.getHost()))))
                throw new IllegalArgumentException();
            String q = u.getRawQuery();
            if (callback && q != null)
                for (String item : q.split("&")) {
                    String key =
                            java.net.URLDecoder.decode(
                                    item.split("=", 2)[0], StandardCharsets.UTF_8);
                    if (Set.of("code", "state").contains(key)) throw new IllegalArgumentException();
                }
        } catch (Exception e) {
            throw bad("地址需为HTTPS（本机回环允许HTTP），且不可含身份、片段或code/state参数");
        }
    }

    @Transactional
    public Map<String, Object> create(long owner, Save b) {
        validate(b);
        db.queryForObject(
                "SELECT user_id FROM auth_user WHERE user_id=? FOR UPDATE", Long.class, owner);
        if (db.queryForObject(
                        "SELECT COUNT(*) FROM auth_oauth_application WHERE owner_user_id=?",
                        Long.class,
                        owner)
                >= 100) throw bad("最多保留100条应用记录");
        if (db.queryForObject(
                        "SELECT COUNT(*) FROM auth_oauth_application WHERE owner_user_id=? AND"
                                + " deleted=0",
                        Long.class,
                        owner)
                >= 20) throw bad("最多创建20个应用");
        String code = "oauth_" + random();
        String secret = random();
        db.update(
                "INSERT INTO"
                    + " auth_oauth_application(owner_user_id,client_code,secret_hash,app_name,description,logo,website_url,redirect_uris,scopes,status)"
                    + " VALUES(?,?,?,?,?,?,?,?,?,0)",
                owner,
                code,
                hash(secret),
                b.appName().trim(),
                b.description(),
                b.logo(),
                b.websiteUrl(),
                String.join("\n", new LinkedHashSet<>(b.redirectUris())),
                String.join(",", new TreeSet<>(b.scopes())));
        long id =
                db.queryForObject(
                        "SELECT id FROM auth_oauth_application WHERE client_code=?",
                        Long.class,
                        code);
        var v = require(id, owner, false);
        v.put("clientSecret", secret);
        return v;
    }

    @Transactional
    public Map<String, Object> update(long owner, long id, Save b) {
        validate(b);
        var old = require(id, owner, true);
        if (((Number) old.get("status")).intValue() == 3) throw bad("停用应用不可修改");
        db.update(
                "UPDATE auth_oauth_application SET"
                    + " app_name=?,description=?,logo=?,website_url=?,redirect_uris=?,scopes=?,status=0,audit_remark=NULL,audit_time=NULL"
                    + " WHERE id=?",
                b.appName().trim(),
                b.description(),
                b.logo(),
                b.websiteUrl(),
                String.join("\n", new LinkedHashSet<>(b.redirectUris())),
                String.join(",", new TreeSet<>(b.scopes())),
                id);
        disable(old.get("clientId").toString());
        db.update("UPDATE auth_platform SET status=0 WHERE client_code=?", old.get("clientId"));
        return require(id, owner, false);
    }

    @Transactional
    public Map<String, Object> audit(long id, boolean approved, String remark) {
        var old = require(id, null, true);
        if (((Number) old.get("status")).intValue() != 0) throw bad("仅待审核应用可以审核");
        if (remark != null && remark.length() > 500) throw bad("审核备注过长");
        db.update(
                "UPDATE auth_oauth_application SET status=?,audit_remark=?,audit_time=NOW() WHERE"
                        + " id=?",
                approved ? 1 : 2,
                remark,
                id);
        if (approved) {
            @SuppressWarnings("unchecked")
            var urls = (List<String>) old.get("redirectUris");
            db.update(
                    "INSERT INTO auth_client(client_code,client_name,redirect_uri,status)"
                        + " VALUES(?,?,?,1) ON DUPLICATE KEY UPDATE"
                        + " client_name=VALUES(client_name),redirect_uri=VALUES(redirect_uri),status=1",
                    old.get("clientId"),
                    old.get("appName"),
                    urls.getFirst());
        }
        return require(id, null, false);
    }

    @Transactional
    public void status(long id, int status) {
        var old = require(id, null, true);
        int current = ((Number) old.get("status")).intValue();
        if ((status != 1 && status != 3) || (current != 1 && current != 3)) throw bad("仅已审核应用可启停");
        db.update("UPDATE auth_oauth_application SET status=? WHERE id=?", status, id);
        db.update(
                "UPDATE auth_client SET status=? WHERE client_code=?",
                status == 1 ? 1 : 0,
                old.get("clientId"));
    }

    @Transactional
    public void remove(long id, Long owner) {
        var old = require(id, owner, true);
        disable(old.get("clientId").toString());
        db.update("UPDATE auth_platform SET status=0 WHERE client_code=?", old.get("clientId"));
        db.update("UPDATE auth_oauth_application SET deleted=1,status=3 WHERE id=?", id);
    }

    @Transactional
    public Map<String, Object> reset(long owner, long id) {
        var old = require(id, owner, true);
        if (((Number) old.get("status")).intValue() != 1) throw bad("仅已通过应用可以重置密钥");
        String secret = random();
        db.update("UPDATE auth_oauth_application SET secret_hash=? WHERE id=?", hash(secret), id);
        old.put("clientSecret", secret);
        return old;
    }

    private void disable(String code) {
        db.update("UPDATE auth_client SET status=0 WHERE client_code=?", code);
    }

    public boolean redirectAllowed(String code, String uri) {
        var rows =
                db.queryForList(
                        "SELECT redirect_uris FROM auth_oauth_application WHERE client_code=? AND"
                                + " status=1 AND deleted=0",
                        code);
        return rows.size() == 1
                && List.of(rows.getFirst().get("redirect_uris").toString().split("\n"))
                        .contains(uri);
    }

    public void verifySecret(String code, String secret) {
        if (!code.startsWith("oauth_")) return;
        var rows =
                db.queryForList(
                        "SELECT secret_hash FROM auth_oauth_application WHERE client_code=? AND"
                                + " status=1 AND deleted=0",
                        code);
        if (secret == null
                || secret.length() > 128
                || rows.size() != 1
                || !MessageDigest.isEqual(
                        hash(secret).getBytes(StandardCharsets.US_ASCII),
                        rows.getFirst()
                                .get("secret_hash")
                                .toString()
                                .getBytes(StandardCharsets.US_ASCII)))
            throw new BizException(ErrorCode.FORBIDDEN, "客户端凭证无效");
    }

    public Map<String, Object> consentPreview(String code, String redirectUri) {
        if (code == null || !code.startsWith("oauth_") || code.length() > 64 || redirectUri == null)
            throw bad("授权请求无效");
        var rows =
                db.queryForList(
                        "SELECT client_code AS clientId,app_name AS appName,description,website_url"
                            + " AS websiteUrl,scopes,redirect_uris AS redirectUris FROM"
                            + " auth_oauth_application WHERE client_code=? AND status=1 AND"
                            + " deleted=0",
                        code);
        if (rows.size() != 1) throw new BizException(ErrorCode.NOT_FOUND, "应用未启用或不存在");
        var row = new LinkedHashMap<>(rows.getFirst());
        if (!List.of(row.get("redirectUris").toString().split("\n")).contains(redirectUri))
            throw new BizException(ErrorCode.FORBIDDEN, "回调地址不匹配");
        row.remove("redirectUris");
        row.put("redirectUri", redirectUri);
        row.put("scopes", List.of(row.get("scopes").toString().split(",")));
        return row;
    }

    @Transactional
    public void consent(long userId, String code, String redirectUri) {
        // Lock the app against concurrent scope changes before persisting this consent.
        var rows =
                db.queryForList(
                        "SELECT id FROM auth_oauth_application WHERE client_code=? AND status=1 AND"
                            + " deleted=0 FOR UPDATE",
                        code);
        if (rows.size() != 1) throw new BizException(ErrorCode.NOT_FOUND, "应用未启用或不存在");
        consentPreview(code, redirectUri);
        db.update(
                "INSERT INTO auth_platform(user_id,client_code,status,authorized_at)"
                    + " VALUES(?,?,1,NOW()) ON DUPLICATE KEY UPDATE status=1,authorized_at=NOW()",
                userId,
                code);
    }

    public boolean allows(String code, String method, String path) {
        var rows =
                db.queryForList(
                        "SELECT scopes FROM auth_oauth_application WHERE client_code=? AND status=1"
                                + " AND deleted=0",
                        code);
        if (rows.size() != 1) return false;
        var scopes = Set.of(rows.getFirst().get("scopes").toString().split(","));
        if ("GET".equals(method)
                && "/api/v1/user/profile".equals(path)
                && !scopes.contains("email:read")) return false;
        if ("POST".equals(method) && "/api/v1/auth/logout".equals(path)) return true;
        if ("GET".equals(method) && "/api/v1/auth/me".equals(path))
            return scopes.contains("profile:read");
        if ("GET".equals(method)
                && ("/api/v1/notice/inbox".equals(path)
                        || "/api/v1/notice/inbox/summary".equals(path)
                        || path.matches("/api/v1/notice/inbox/[1-9][0-9]*")))
            return scopes.contains("notice:read");
        return PersonalApiKeyService.allows(scopes, method, path);
    }

    private static String random() {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String hash(String v) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(v.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BizException bad(String message) {
        return new BizException(ErrorCode.BAD_REQUEST, message);
    }
}
