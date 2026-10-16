package cc.nanoic.yunanexus.user.service;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.user.config.ShardRoutingDataSource;
import cc.nanoic.yunanexus.user.domain.UserBadgeGrantRequest;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

import javax.sql.DataSource;

/** 徽章目录集中在物理用户库0；用户获得的徽章仍按userId路由。 */
@Service
public class BadgeCatalogService {
    private final JdbcTemplate db;
    private final UserBadgeService badges;
    private final UserProfileService profiles;

    public BadgeCatalogService(
            DataSource source, UserBadgeService badges, UserProfileService profiles) {
        this.db = new JdbcTemplate(((ShardRoutingDataSource) source).getDelegates().getFirst());
        this.badges = badges;
        this.profiles = profiles;
    }

    public record Save(
            String badgeCode,
            String badgeName,
            int badgeLevel,
            String description,
            String icon,
            String grantType,
            String condition,
            int status) {}

    private static final String SELECT =
            "SELECT b.id,b.badge_code AS badgeCode,b.badge_name AS badgeName,b.badge_level AS"
                + " badgeLevel,b.description,b.icon,b.grant_type AS grantType,b.condition_expr AS"
                + " `condition`,b.status,(SELECT COUNT(*) FROM badge_grant_record g WHERE"
                + " g.badge_id=b.id AND g.completed=1) AS grantedCount,b.create_time AS"
                + " createTime,b.update_time AS updateTime FROM badge_definition b WHERE"
                + " b.deleted=0";

    private Map<String,Object> normalize(Map<String,Object> row){var v=new LinkedHashMap<>(row);v.replaceAll((k,value)->value instanceof java.sql.Timestamp t?t.toLocalDateTime().toString():value);return v;}
    public List<Map<String, Object>> list() {
        return db.queryForList(SELECT + " ORDER BY b.id DESC LIMIT 1000").stream().map(this::normalize).toList();
    }

    private Map<String, Object> require(long id) {
        var rows = db.queryForList(SELECT + " AND b.id=?", id);
        if (rows.isEmpty()) throw new BizException(ErrorCode.NOT_FOUND, "徽章不存在");
        return normalize(rows.getFirst());
    }

    public static void validate(Save b) {
        if (b == null
                || b.badgeCode() == null
                || !b.badgeCode().matches("[A-Za-z0-9_.:-]{1,64}")
                || b.badgeName() == null
                || b.badgeName().isBlank()
                || b.badgeName().length() > 64
                || b.badgeLevel() < 1
                || b.badgeLevel() > 9
                || (b.status() != 0 && b.status() != 1)) throw bad("徽章编码、名称、等级或状态无效");
        if (!"manual".equals(b.grantType())) throw bad("当前支持手动发放，自动条件发放尚未开放");
        if (b.description() != null && b.description().length() > 255) throw bad("描述最多255字");
        if (b.icon() != null && !b.icon().isBlank())
            try {
                var u = java.net.URI.create(b.icon());
                if (b.icon().length() > 512
                        || !"https".equals(u.getScheme())
                        || u.getHost() == null
                        || u.getUserInfo() != null) throw new IllegalArgumentException();
            } catch (Exception e) {
                throw bad("图标需为有效HTTPS地址");
            }
    }

    public Map<String, Object> create(Save b) {
        validate(b);
        try {
            db.update(
                    "INSERT INTO"
                        + " badge_definition(badge_code,badge_name,badge_level,description,icon,grant_type,status)"
                        + " VALUES(?,?,?,?,?,'manual',?)",
                    b.badgeCode(),
                    b.badgeName().trim(),
                    b.badgeLevel(),
                    b.description(),
                    b.icon(),
                    b.status());
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw bad("徽章编码已存在");
        }
        long id =
                db.queryForObject(
                        "SELECT id FROM badge_definition WHERE badge_code=?",
                        Long.class,
                        b.badgeCode());
        return require(id);
    }

    public Map<String, Object> update(long id, Save b) {
        validate(b);
        var old = require(id);
        if (!old.get("badgeCode").equals(b.badgeCode())) throw bad("徽章编码创建后不可修改");
        db.update(
                "UPDATE badge_definition SET"
                    + " badge_name=?,badge_level=?,description=?,icon=?,status=? WHERE id=? AND"
                    + " deleted=0",
                b.badgeName().trim(),
                b.badgeLevel(),
                b.description(),
                b.icon(),
                b.status(),
                id);
        return require(id);
    }

    public void remove(long id) {
        require(id);
        db.update("UPDATE badge_definition SET deleted=1,status=0 WHERE id=?", id);
    }

    public List<Map<String, Object>> records() {
        return db.queryForList(
                "SELECT g.id,g.badge_id AS badgeId,b.badge_code AS badgeCode,b.badge_name AS"
                    + " badgeName,g.user_id AS userId,g.username,'manual' AS"
                    + " grantType,g.granted_time AS grantedTime FROM badge_grant_record g JOIN"
                    + " badge_definition b ON b.id=g.badge_id WHERE g.completed=1 ORDER BY g.id"
                    + " DESC LIMIT 1000");
    }

    public void grant(long id, List<Long> ids) {
        var b = require(id);
        if (((Number) b.get("status")).intValue() != 1) throw bad("停用徽章不可发放");
        if (ids == null
                || ids.isEmpty()
                || ids.size() > 100
                || ids.stream().anyMatch(v -> v == null || v <= 0)) throw bad("每次发放1~100名用户");
        var users = new LinkedHashMap<Long, String>();
        for (long uid : new LinkedHashSet<>(ids)) {
            var p = profiles.getByUserId(uid);
            if (p == null) throw bad("目标用户不存在");
            users.put(uid, p.getUsername());
        }
        for (var entry : users.entrySet()) {
            long uid = entry.getKey();
            db.update(
                    "INSERT IGNORE INTO badge_grant_record(badge_id,user_id,username,completed)"
                        + " VALUES(?,?,?,0)",
                    id,
                    uid,
                    entry.getValue());
            var request = new UserBadgeGrantRequest();
            request.setUserId(uid);
            request.setBadgeCode(b.get("badgeCode").toString());
            request.setBadgeName(b.get("badgeName").toString());
            request.setBadgeLevel(((Number) b.get("badgeLevel")).intValue());
            request.setDescription((String) b.get("description"));
            request.setIcon((String) b.get("icon"));
            badges.grantBadge(uid, request);
            db.update(
                    "UPDATE badge_grant_record SET completed=1 WHERE badge_id=? AND user_id=?",
                    id,
                    uid);
        }
    }

    private static BizException bad(String m) {
        return new BizException(ErrorCode.BAD_REQUEST, m);
    }
}
