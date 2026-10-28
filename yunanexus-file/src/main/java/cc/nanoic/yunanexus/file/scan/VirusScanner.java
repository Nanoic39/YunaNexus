package cc.nanoic.yunanexus.file.scan;

/**
 * 病毒扫描引擎抽象。
 *
 * <p>做成接口而不是直接调 ClamAV：扫描引擎可能被替换（ClamAV / 商业引擎 / 云 API），
 * 也可能从"本机侧车"换成"独立扫描服务"。业务侧只依赖这个接口。
 *
 * @author yunanexus
 */
public interface VirusScanner {

    /**
     * 扫描结果。
     *
     * @param clean   是否通过
     * @param detail  说明（检出时含引擎返回的签名名）
     * @param engine  引擎标识，便于审计追溯
     */
    record ScanResult(boolean clean, String detail, String engine) {
    }

    /**
     * 扫描一个本地文件。
     *
     * @param path 文件绝对路径
     * @return 扫描结果
     * @throws ScanUnavailableException 引擎不可用时抛出（由调用方决定 fail-open 还是 fail-closed）
     */
    ScanResult scan(java.nio.file.Path path);

    /**
     * 引擎是否可用（供健康检查与降级判断）。
     *
     * @return 可用返回 true
     */
    boolean available();
}
