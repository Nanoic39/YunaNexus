package cc.nanoic.yunanexus.file.storage;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.file.config.FileProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LocalFileStorage} 单元测试（真实临时目录）。
 *
 * @author yunanexus
 */
class LocalFileStorageTest {

    /** "hello" 的 SHA-256（十六进制小写） */
    private static final String HELLO_SHA256 =
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

    @TempDir
    Path tempDir;

    private LocalFileStorage storage;

    @BeforeEach
    void setUp() {
        FileProperties properties = new FileProperties();
        properties.getStorage().setBaseDir(tempDir.toString());
        storage = new LocalFileStorage(properties);
    }

    @Test
    void storeWritesBytesAndComputesSha256() {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

        FileStorage.StoredObject stored = storage.store(new ByteArrayInputStream(content), "txt");

        assertEquals(5, stored.size());
        assertEquals(HELLO_SHA256, stored.sha256());
        assertTrue(stored.path().endsWith(".txt"), "路径应保留扩展名");
        assertTrue(storage.exists(stored.path()), "存储后文件应存在");
        assertEquals(1, storage.storageType());
    }

    @Test
    void storeWithoutExtensionKeepsPathBare() {
        FileStorage.StoredObject stored = storage.store(new ByteArrayInputStream(new byte[0]), null);

        assertEquals(0, stored.size());
        assertFalse(stored.path().contains("."), "无扩展名不应追加点号");
    }

    @Test
    void openReturnsStoredContent() throws IOException {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        FileStorage.StoredObject stored = storage.store(new ByteArrayInputStream(content), "txt");

        try (InputStream in = storage.open(stored.path())) {
            assertEquals("hello", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void openMissingFileThrowsFileNotFound() {
        BizException ex = assertThrows(BizException.class,
                () -> storage.open("2099/01/01/does-not-exist.txt"));
        assertEquals(ErrorCode.FILE_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void deleteRemovesFile() {
        FileStorage.StoredObject stored = storage.store(
                new ByteArrayInputStream("bye".getBytes(StandardCharsets.UTF_8)), "dat");

        assertTrue(storage.delete(stored.path()));
        assertFalse(storage.exists(stored.path()));
    }

    @Test
    void deleteMissingFileReturnsFalse() {
        assertFalse(storage.delete("2099/01/01/does-not-exist.dat"));
    }

    @Test
    void openRejectsPathTraversal() {
        BizException ex = assertThrows(BizException.class, () -> storage.open("../../etc/hosts"));
        assertEquals(ErrorCode.FILE_ACCESS_DENIED.getCode(), ex.getCode());
    }

    // ---------- Range 区间读取 ----------

    @Test
    void openWithOffsetAndLengthReturnsSlice() throws IOException {
        FileStorage.StoredObject stored = storage.store(
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)), "txt");

        try (InputStream in = storage.open(stored.path(), 1, 3)) {
            assertEquals("ell", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void openWithNegativeLengthReadsToEnd() throws IOException {
        FileStorage.StoredObject stored = storage.store(
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)), "txt");

        try (InputStream in = storage.open(stored.path(), 2, -1)) {
            assertEquals("llo", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    // ---------- 分片上传 ----------

    @Test
    void storeChunkListsUploadedIndexes() {
        storage.storeChunk("up1", 1, new ByteArrayInputStream("b".getBytes(StandardCharsets.UTF_8)));
        storage.storeChunk("up1", 0, new ByteArrayInputStream("a".getBytes(StandardCharsets.UTF_8)));

        assertEquals(Set.of(0, 1), storage.listChunks("up1"));
    }

    @Test
    void storeChunkOverwritesExistingIndex() {
        storage.storeChunk("up1", 0, new ByteArrayInputStream("old".getBytes(StandardCharsets.UTF_8)));
        storage.storeChunk("up1", 0, new ByteArrayInputStream("new".getBytes(StandardCharsets.UTF_8)));

        assertEquals(Set.of(0), storage.listChunks("up1"));
    }

    @Test
    void listChunksReturnsEmptyForUnknownUpload() {
        assertTrue(storage.listChunks("nope").isEmpty());
    }

    @Test
    void storeAndLoadChunkSessionRoundTrips() {
        byte[] payload = "{\"uploadId\":\"up1\"}".getBytes(StandardCharsets.UTF_8);

        storage.storeChunkSession("up1", payload);

        assertArrayEquals(payload, storage.loadChunkSession("up1"));
    }

    @Test
    void loadChunkSessionReturnsNullWhenMissing() {
        assertNull(storage.loadChunkSession("nope"));
    }

    @Test
    void mergeChunksConcatenatesInOrderAndCleansTemp() throws IOException {
        storage.storeChunk("up1", 0, new ByteArrayInputStream("he".getBytes(StandardCharsets.UTF_8)));
        storage.storeChunk("up1", 1, new ByteArrayInputStream("llo".getBytes(StandardCharsets.UTF_8)));

        FileStorage.StoredObject merged = storage.mergeChunks("up1", 2, "txt");

        assertEquals(5L, merged.size());
        assertEquals(HELLO_SHA256, merged.sha256());
        try (InputStream in = storage.open(merged.path())) {
            assertEquals("hello", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertTrue(storage.listChunks("up1").isEmpty(), "合并后临时分片应被清理");
        assertFalse(storage.listChunkUploads().contains("up1"), "合并后会话目录应被清理");
    }

    @Test
    void mergeChunksThrowsWhenChunkMissing() {
        storage.storeChunk("up1", 0, new ByteArrayInputStream("he".getBytes(StandardCharsets.UTF_8)));

        BizException ex = assertThrows(BizException.class, () -> storage.mergeChunks("up1", 2, "txt"));
        assertEquals(ErrorCode.FILE_CHUNK_MISSING.getCode(), ex.getCode());
    }

    @Test
    void abortChunksRemovesTempDir() {
        storage.storeChunk("up1", 0, new ByteArrayInputStream("a".getBytes(StandardCharsets.UTF_8)));

        storage.abortChunks("up1");

        assertTrue(storage.listChunks("up1").isEmpty());
        assertFalse(storage.listChunkUploads().contains("up1"));
    }
}
