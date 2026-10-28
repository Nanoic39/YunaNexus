package cc.nanoic.yunanexus.file.scan;

import java.nio.file.Path;

/**
 * 未启用扫描时的占位实现：一律报告"不可用"。
 *
 * <p>刻意不返回 clean——那样会让"没接引擎"看起来像"全部通过"，
 * 是最危险的假象。返回不可用，让上层按 fail-open 标记 pending。
 *
 * @author yunanexus
 */
public class NoopScanner implements VirusScanner {

    @Override
    public ScanResult scan(Path path) {
        throw new ScanUnavailableException("未配置扫描引擎");
    }

    @Override
    public boolean available() {
        return false;
    }
}
