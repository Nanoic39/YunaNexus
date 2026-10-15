package cc.nanoic.yunanexus.file.domain;

import java.io.InputStream;

/**
 * 文件下载载荷（支持 HTTP Range 区间）。<p>
 * 调用方负责关闭 {@code content}。
 *
 * @param fileName    原始文件名
 * @param contentType MIME 类型
 * @param totalSize   文件总字节数
 * @param start       本次返回区间起始偏移（全量时为 0）
 * @param end         本次返回区间结束偏移（含，全量时为 {@code totalSize - 1}）
 * @param partial     是否为部分内容（Range 请求命中时为 true）
 * @param content     内容流（由调用方负责关闭）
 * @author yunanexus
 */
public record FileRange(String fileName,
                        String contentType,
                        long totalSize,
                        long start,
                        long end,
                        boolean partial,
                        InputStream content) {
}
