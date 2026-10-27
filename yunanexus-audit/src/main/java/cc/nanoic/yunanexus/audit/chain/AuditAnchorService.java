package cc.nanoic.yunanexus.audit.chain;

import cc.nanoic.yunanexus.audit.config.AuditProperties;
import cc.nanoic.yunanexus.audit.config.ShardContextHolder;
import cc.nanoic.yunanexus.audit.entity.AuditLog;
import cc.nanoic.yunanexus.audit.mapper.AuditLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 外部锚定：定期把各分片的链头写到库外，事后比对以发现"整库被重写"。<p>
 *
 * 两类断裂它各管一半：
 * <ul>
 *   <li>库里被改一条 → 链校验（{@link AuditChainVerifier}）负责；</li>
 *   <li>整库被重算 → 本类负责：锚定时的链头摘要与库里同 id 记录的摘要对不上，
 *       或锚定记录凭空消失。</li>
 * </ul>
 *
 * @author yunanexus
 */
@Slf4j
@Service
public class AuditAnchorService {

    private final AuditLogMapper auditLogMapper;
    private final AuditAnchorStore store;
    private final int logicalCount;

    public AuditAnchorService(AuditLogMapper auditLogMapper, AuditProperties properties) {
        this.auditLogMapper = auditLogMapper;
        this.store = new AuditAnchorStore(properties.getAnchor().getDir());
        this.logicalCount = properties.getShard().getLogicalCount();
    }

    /**
     * 为所有分片写入一次锚定。
     *
     * @return 实际写入条数（空分片跳过）
     */
    public int anchorNow() {
        List<AuditAnchorRecord> records = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (int shardIndex = 0; shardIndex < logicalCount; shardIndex++) {
            String head = currentHead(shardIndex);
            if (head == null || head.isEmpty()) {
                continue;
            }
            records.add(new AuditAnchorRecord(shardIndex, headId(shardIndex), head, now));
        }
        try {
            int written = store.append(records);
            log.info("审计锚定写入 {} 条（分片 {} 个）", written, records.size());
            return written;
        } catch (IOException error) {
            // 锚定失败必须显性化：没有锚定就等于放弃了"防整库重写"这条能力
            log.error("审计锚定写入失败 dir={}：{}", store, error.toString());
            return 0;
        }
    }

    /**
     * 比对锚定与现状。
     *
     * @return 每个不一致项的描述；为空表示全部一致
     */
    public List<String> verifyAnchors() {
        List<String> findings = new ArrayList<>();
        Map<Integer, AuditAnchorRecord> anchors;
        try {
            anchors = store.latestPerShard();
        } catch (IOException error) {
            findings.add("anchor_read_failed:" + error.getMessage());
            return findings;
        }
        for (Map.Entry<Integer, AuditAnchorRecord> entry : anchors.entrySet()) {
            int shardIndex = entry.getKey();
            AuditAnchorRecord anchor = entry.getValue();
            AuditLog record = findById(shardIndex, anchor.id());
            if (record == null) {
                findings.add(shardIndex + ":" + anchor.id() + ":anchored_record_missing");
                continue;
            }
            String actual = record.getRecordHash();
            if (actual == null || !actual.equalsIgnoreCase(anchor.recordHash())) {
                findings.add(shardIndex + ":" + anchor.id() + ":anchor_mismatch");
            }
        }
        for (String finding : findings) {
            log.error("审计锚定校验失败 {}", finding);
        }
        return findings;
    }

    /**
     * 列出各分片最新锚定（运维查看用）。
     *
     * @return 锚定记录列表
     */
    public List<AuditAnchorRecord> listAnchors() {
        try {
            return new ArrayList<>(store.latestPerShard().values());
        } catch (IOException error) {
            return List.of();
        }
    }

    private AuditLog findById(int shardIndex, long id) {
        ShardContextHolder.set(shardIndex);
        try {
            return auditLogMapper.selectById(id);
        } finally {
            ShardContextHolder.clear();
        }
    }

    private String currentHead(int shardIndex) {
        ShardContextHolder.set(shardIndex);
        try {
            return auditLogMapper.selectHeadHash(shardIndex);
        } finally {
            ShardContextHolder.clear();
        }
    }

    private long headId(int shardIndex) {
        ShardContextHolder.set(shardIndex);
        try {
            Long id = auditLogMapper.selectHeadId(shardIndex);
            return id == null ? 0L : id;
        } finally {
            ShardContextHolder.clear();
        }
    }
}
