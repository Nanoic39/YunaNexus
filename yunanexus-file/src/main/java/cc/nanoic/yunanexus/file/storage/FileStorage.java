package cc.nanoic.yunanexus.file.storage;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * 文件存储抽象（roadmap 7.2）。<p>
 *
 * 屏蔽具体存储后端（本地磁盘 / MinIO / OSS）。当前仅实现
 * {@link LocalFileStorage}（本地磁盘），后续新增后端只需实现本接口并注册为 Bean，
 * 由 {@link #storageType()} 与 {@code file_meta.storage_type} 对应。
 *
 * @author yunanexus
 */
public interface FileStorage {

    /** 存储类型标识（对应 {@code file_meta.storage_type}） */
    int storageType();

    /**
     * 保存文件内容。
     *
     * @param in  文件输入流（由调用方负责关闭）
     * @param ext 扩展名（小写、不含点），可为 {@code null}
     * @return 存储结果（相对路径 / 字节数 / SHA-256）
     */
    StoredObject store(InputStream in, String ext);

    /**
     * 打开文件内容。
     *
     * @param storagePath {@link StoredObject#path()}
     * @return 文件输入流（由调用方负责关闭）
     */
    InputStream open(String storagePath);

    /**
     * 打开文件内容的一个字节区间（HTTP Range 下载）。
     *
     * @param storagePath {@link StoredObject#path()}
     * @param offset      起始偏移（从 0 开始）
     * @param length      读取字节数；{@code < 0} 表示读到文件末尾
     * @return 限定区间的输入流（由调用方负责关闭）
     */
    InputStream open(String storagePath, long offset, long length);

    /** 判断文件是否存在 */
    boolean exists(String storagePath);

    /**
     * 删除文件。
     *
     * @return true 表示删除成功或文件本就不存在
     */
    boolean delete(String storagePath);

    /**
     * 分片上传：保存单个分片（下标覆盖式，支持失败重传）。
     *
     * @param uploadId 上传会话 ID
     * @param index    分片下标（从 0 开始）
     * @param in       分片内容流（由调用方负责关闭）
     */
    void storeChunk(String uploadId, int index, InputStream in);

    /**
     * 分片上传：列出已成功上传的分片下标，用于断点续传。
     *
     * @param uploadId 上传会话 ID
     * @return 已上传分片下标（无序），无任何分片时返回空集合
     */
    Set<Integer> listChunks(String uploadId);

    /**
     * 分片上传：保存上传会话元数据（字节内容由调用方序列化）。
     *
     * @param uploadId 上传会话 ID
     * @param session  会话元数据字节
     */
    void storeChunkSession(String uploadId, byte[] session);

    /**
     * 分片上传：读取上传会话元数据。
     *
     * @param uploadId 上传会话 ID
     * @return 会话元数据字节，不存在返回 {@code null}
     */
    byte[] loadChunkSession(String uploadId);

    /**
     * 分片上传：按序合并分片为最终对象并清理临时分片。
     *
     * @param uploadId    上传会话 ID
     * @param totalChunks 分片总数
     * @param ext         扩展名（小写、不含点），可为 {@code null}
     * @return 存储结果（相对路径 / 字节数 / SHA-256）
     */
    StoredObject mergeChunks(String uploadId, int totalChunks, String ext);

    /**
     * 分片上传：列出所有上传会话 ID（用于过期清理）。
     *
     * @return 上传会话 ID 列表
     */
    List<String> listChunkUploads();

    /**
     * 分片上传：放弃上传，清理临时分片与会话元数据。
     *
     * @param uploadId 上传会话 ID
     */
    void abortChunks(String uploadId);

    /**
     * 存储结果。
     *
     * @param path   存储路径（相对根目录 / 对象键）
     * @param size   字节数
     * @param sha256 内容 SHA-256（十六进制小写）
     */
    record StoredObject(String path, long size, String sha256) {
    }
}
