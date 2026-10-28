package cc.nanoic.yunanexus.file.scan;

/**
 * 扫描引擎不可用。
 *
 * <p>单独成异常而不是返回 null：调用方必须显式决定降级策略
 * （fail-open 标记 pending / fail-closed 拒绝上传），不能悄悄当成"通过"。
 *
 * @author yunanexus
 */
public class ScanUnavailableException extends RuntimeException {

    public ScanUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public ScanUnavailableException(String message) {
        super(message);
    }
}
