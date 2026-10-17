package cc.nanoic.yunanexus.file.service;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.file.entity.FileShare;
import cc.nanoic.yunanexus.file.mapper.FileShareMapper;

import org.springframework.stereotype.Service;

import java.security.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** 有期限、可撤销的本人文件分享；提取码仅保存带盐 PBKDF2 摘要。 */
@Service
public class FileShareService {
    private final PersonalFileService personal;
    private final FileShareMapper mapper;
    private static final SecureRandom RANDOM = new SecureRandom();

    public FileShareService(PersonalFileService personal, FileShareMapper mapper) {
        this.personal = personal;
        this.mapper = mapper;
    }

    private <T> T index(Supplier<T> action) {
        return personal.inShard(0, action);
    }

    public record View(
            String shareId,
            Long fileId,
            String fileName,
            boolean protectedByCode,
            @cc.nanoic.yunanexus.common.web.time.UtcDateTime LocalDateTime createdAt,
            @cc.nanoic.yunanexus.common.web.time.UtcDateTime LocalDateTime expiresAt,
            boolean revoked) {}

    public View view(FileShare s) {
        return new View(
                s.getShareId(),
                s.getFileId(),
                s.getFileName(),
                s.getCodeHash() != null,
                s.getCreatedAt(),
                s.getExpiresAt(),
                Integer.valueOf(1).equals(s.getRevoked()));
    }

    public List<View> list(long userId) {
        var entries = index(() -> mapper.list(userId));
        var names =
                personal.ownedNames(userId, entries.stream().map(FileShare::getFileId).toList());
        return entries.stream()
                .map(
                        s ->
                                new View(
                                        s.getShareId(),
                                        s.getFileId(),
                                        names.getOrDefault(s.getFileId(), s.getFileName()),
                                        s.getCodeHash() != null,
                                        s.getCreatedAt(),
                                        s.getExpiresAt(),
                                        Integer.valueOf(1).equals(s.getRevoked())))
                .toList();
    }

    public View create(long userId, long fileId, int days, String code) {
        if (days < 1
                || days > 30
                || (code != null && !code.isEmpty() && !code.matches("[A-Za-z0-9]{4,32}")))
            throw new BizException(ErrorCode.BAD_REQUEST, "有效期为 1~30 天，提取码为 4~32 位字母或数字");
        var file = personal.requireOwned(fileId, userId);
        return index(
                () -> {
                    mapper.prune(userId);
                    if (mapper.count(userId) >= 200)
                        throw new BizException(ErrorCode.BAD_REQUEST, "分享记录已达上限，请等待历史记录清理");
                    byte[] bytes = new byte[32];
                    RANDOM.nextBytes(bytes);
                    FileShare s = new FileShare();
                    s.setShareId(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
                    s.setUserId(userId);
                    s.setFileId(fileId);
                    s.setFileName(file.getFileName());
                    s.setCreatedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
                    s.setExpiresAt(s.getCreatedAt().plusDays(days));
                    s.setRevoked(0);
                    if (code != null && !code.isEmpty()) s.setCodeHash(hash(code, s.getShareId()));
                    mapper.insert(s);
                    return view(s);
                });
    }

    public void revoke(long userId, String shareId) {
        if (index(() -> mapper.revoke(shareId, userId)) == 0) throw unavailable();
    }

    public FileShare redeem(String shareId, String code) {
        if (shareId == null
                || !shareId.matches("[A-Za-z0-9_-]{43}")
                || (code != null && code.length() > 32)) throw unavailable();
        FileShare s = index(() -> mapper.find(shareId));
        if (s == null
                || Integer.valueOf(1).equals(s.getRevoked())
                || !s.getExpiresAt().isAfter(LocalDateTime.now(java.time.ZoneOffset.UTC))) throw unavailable();
        if (s.getCodeHash() != null) {
            if (index(() -> mapper.failure(shareId)) == 0)
                throw new BizException(ErrorCode.BAD_REQUEST, "尝试次数过多，请 15 分钟后重试");
            if (!MessageDigest.isEqual(
                    s.getCodeHash().getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    hash(code == null ? "" : code, s.getShareId())
                            .getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
                throw unavailable();
            }
            index(
                    () -> {
                        mapper.resetFailures(shareId);
                        return null;
                    });
        }
        personal.requireOwned(s.getFileId(), s.getUserId());
        return s;
    }

    static String hash(String code, String salt) {
        PBEKeySpec spec =
                new PBEKeySpec(
                        code.toCharArray(),
                        salt.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                        210000,
                        256);
        try {
            return HexFormat.of()
                    .formatHex(
                            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                                    .generateSecret(spec)
                                    .getEncoded());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("提取码摘要不可用", e);
        } finally {
            spec.clearPassword();
        }
    }

    private BizException unavailable() {
        return new BizException(ErrorCode.FILE_NOT_FOUND, "分享不存在、已失效或提取码不正确");
    }
}
