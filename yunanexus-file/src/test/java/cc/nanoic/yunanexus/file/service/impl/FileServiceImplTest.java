package cc.nanoic.yunanexus.file.service.impl;

import cc.nanoic.yunanexus.common.core.model.PageResult;
import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.common.datascope.DataScopeContext;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.file.client.MetaIdClient;
import cc.nanoic.yunanexus.file.client.PermissionClient;
import cc.nanoic.yunanexus.file.config.FileProperties;
import cc.nanoic.yunanexus.file.config.ShardContextHolder;
import cc.nanoic.yunanexus.file.domain.ChunkInitRequest;
import cc.nanoic.yunanexus.file.domain.ChunkInitResult;
import cc.nanoic.yunanexus.file.domain.ChunkUploadStatus;
import cc.nanoic.yunanexus.file.domain.DataScopeView;
import cc.nanoic.yunanexus.file.domain.FileRange;
import cc.nanoic.yunanexus.file.domain.FileView;
import cc.nanoic.yunanexus.file.domain.UploadSession;
import cc.nanoic.yunanexus.file.entity.FileMeta;
import cc.nanoic.yunanexus.file.mapper.FileMetaMapper;
import cc.nanoic.yunanexus.file.service.FileQuotaService;
import cc.nanoic.yunanexus.file.shard.ShardRouter;
import cc.nanoic.yunanexus.file.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link FileServiceImpl} 单元测试（上传 / 下载 / 删除 / 列表 / 分片 / 秒传）。
 *
 * @author yunanexus
 */
class FileServiceImplTest {

    private static final long USER_ID = 7L;

    private FileMetaMapper fileMetaMapper;
    private FileStorage fileStorage;
    private FileProperties properties;
    private ShardRouter shardRouter;
    private MetaIdClient metaIdClient;
    private PermissionClient permissionClient;
    private FileServiceImpl service;

    private FileQuotaService quotaService;

    @BeforeEach
    void setUp() {
        fileMetaMapper = mock(FileMetaMapper.class);
        fileStorage = mock(FileStorage.class);
        properties = new FileProperties();
        shardRouter = mock(ShardRouter.class);
        metaIdClient = mock(MetaIdClient.class);
        permissionClient = mock(PermissionClient.class);
        quotaService = mock(FileQuotaService.class);
        service = new FileServiceImpl(fileMetaMapper, fileStorage, properties,
                shardRouter, metaIdClient, permissionClient, new ObjectMapper(), quotaService);
    }

    private MockMultipartFile txtFile(String content) {
        return new MockMultipartFile("file", "a.txt", "text/plain",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private FileMeta normalMeta(long fileId, long createBy, Long deptId) {
        FileMeta meta = new FileMeta();
        meta.setId(fileId);
        meta.setFileId(fileId);
        meta.setFileName("a.txt");
        meta.setExt("txt");
        meta.setContentType("text/plain");
        meta.setSize(5L);
        meta.setStorageType(FileMeta.STORAGE_LOCAL);
        meta.setStoragePath("2026/10/13/uuid.txt");
        meta.setSha256("sha");
        meta.setStatus(FileMeta.STATUS_NORMAL);
        meta.setCreateBy(createBy);
        meta.setDeptId(deptId);
        return meta;
    }

    private Result<DataScopeView> scopeResult(boolean all, List<Long> deptIds, Long selfUserId) {
        DataScopeView view = new DataScopeView();
        view.setAll(all);
        view.setDeptIds(deptIds);
        view.setSelfUserId(selfUserId);
        return Result.success(view);
    }

    private UploadSession session(String uploadId, int totalChunks, LocalDateTime createTime) {
        UploadSession session = new UploadSession();
        session.setUploadId(uploadId);
        session.setFileName("a.txt");
        session.setExt("txt");
        session.setContentType("text/plain");
        session.setTotalChunks(totalChunks);
        session.setTotalSize(5L);
        session.setUserId(USER_ID);
        session.setDeptId(3L);
        session.setFileId(16L);
        session.setShardIndex(0);
        session.setCreateTime(createTime);
        return session;
    }

    private byte[] sessionBytes(UploadSession session) {
        try {
            return new ObjectMapper().writeValueAsBytes(session);
        } catch (RuntimeException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- upload ----------

    @Test
    void uploadStoresContentAndMetadata() {
        when(metaIdClient.nextId("file")).thenReturn(Result.success(16L));
        when(shardRouter.route(16L)).thenReturn(0);
        when(permissionClient.userDept(USER_ID)).thenReturn(Result.success(3L));
        when(fileStorage.storageType()).thenReturn(FileMeta.STORAGE_LOCAL);
        when(fileStorage.store(any(InputStream.class), eq("txt")))
                .thenReturn(new FileStorage.StoredObject("2026/10/13/uuid.txt", 5L, "sha"));
        when(fileMetaMapper.insert(any(FileMeta.class))).thenReturn(1);
        when(fileMetaMapper.selectByFileId(16L)).thenReturn(normalMeta(16L, USER_ID, 3L));

        FileView view = service.upload(txtFile("hello"), USER_ID);

        assertEquals(16L, view.getFileId());
        assertEquals("a.txt", view.getFileName());
        assertEquals(5L, view.getSize());
        assertEquals(USER_ID, view.getCreateBy());
        assertEquals(3L, view.getDeptId());
        verify(fileStorage).store(any(InputStream.class), eq("txt"));
        assertNull(ShardContextHolder.get(), "上传结束后必须清理分片上下文");
    }

    @Test
    void uploadRejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.txt", "text/plain", new byte[0]);

        BizException ex = assertThrows(BizException.class, () -> service.upload(empty, USER_ID));
        assertEquals(ErrorCode.FILE_EMPTY.getCode(), ex.getCode());
        verify(fileStorage, never()).store(any(), any());
    }

    @Test
    void uploadRejectsDisallowedExtension() {
        properties.getUpload().setAllowedExtensions(List.of("png"));

        BizException ex = assertThrows(BizException.class, () -> service.upload(txtFile("hello"), USER_ID));
        assertEquals(ErrorCode.FILE_TYPE_NOT_ALLOWED.getCode(), ex.getCode());
    }

    @Test
    void uploadRejectsOversizeFile() {
        properties.getUpload().setMaxSize(1);

        BizException ex = assertThrows(BizException.class, () -> service.upload(txtFile("hello"), USER_ID));
        assertEquals(ErrorCode.FILE_SIZE_EXCEED.getCode(), ex.getCode());
    }

    @Test
    void uploadCleansUpStorageWhenPersistFails() {
        when(metaIdClient.nextId("file")).thenReturn(Result.success(16L));
        when(shardRouter.route(16L)).thenReturn(0);
        when(permissionClient.userDept(USER_ID)).thenReturn(Result.success(null));
        when(fileStorage.storageType()).thenReturn(FileMeta.STORAGE_LOCAL);
        when(fileStorage.store(any(InputStream.class), eq("txt")))
                .thenReturn(new FileStorage.StoredObject("2026/10/13/uuid.txt", 5L, "sha"));
        when(fileMetaMapper.insert(any(FileMeta.class))).thenThrow(new IllegalStateException("db down"));

        assertThrows(IllegalStateException.class, () -> service.upload(txtFile("hello"), USER_ID));

        verify(fileStorage).delete("2026/10/13/uuid.txt");
        assertNull(ShardContextHolder.get(), "落库失败路径也必须清理分片上下文");
    }

    // ---------- download ----------

    @Test
    void downloadReturnsContentWhenVisible() {
        when(fileMetaMapper.selectByFileId(16L)).thenReturn(normalMeta(16L, USER_ID, 3L));
        when(permissionClient.dataScope(USER_ID)).thenReturn(scopeResult(true, List.of(), null));
        when(fileStorage.open("2026/10/13/uuid.txt"))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        FileRange range = service.download(16L, USER_ID, null, null);

        assertEquals("a.txt", range.fileName());
        assertEquals("text/plain", range.contentType());
        assertEquals(5L, range.totalSize());
        assertEquals(0L, range.start());
        assertEquals(4L, range.end());
        assertFalse(range.partial());
    }

    @Test
    void downloadReturnsPartialRange() {
        when(fileMetaMapper.selectByFileId(16L)).thenReturn(normalMeta(16L, USER_ID, 3L));
        when(permissionClient.dataScope(USER_ID)).thenReturn(scopeResult(true, List.of(), null));
        when(fileStorage.open("2026/10/13/uuid.txt", 1L, 3L))
                .thenReturn(new ByteArrayInputStream("ell".getBytes(StandardCharsets.UTF_8)));

        FileRange range = service.download(16L, USER_ID, 1L, 3L);

        assertEquals(1L, range.start());
        assertEquals(3L, range.end());
        assertEquals(5L, range.totalSize());
        assertTrue(range.partial());
    }

    @Test
    void downloadRejectsRangeBeyondFileSize() {
        when(fileMetaMapper.selectByFileId(16L)).thenReturn(normalMeta(16L, USER_ID, 3L));
        when(permissionClient.dataScope(USER_ID)).thenReturn(scopeResult(true, List.of(), null));

        BizException ex = assertThrows(BizException.class, () -> service.download(16L, USER_ID, 9L, 12L));
        assertEquals(ErrorCode.FILE_RANGE_NOT_SATISFIABLE.getCode(), ex.getCode());
        verify(fileStorage, never()).open(anyString(), any(Long.class), any(Long.class));
    }

    @Test
    void downloadThrowsWhenFileMissing() {
        when(fileMetaMapper.selectByFileId(16L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.download(16L, USER_ID, null, null));
        assertEquals(ErrorCode.FILE_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void downloadDeniesWhenOutOfScope() {
        when(fileMetaMapper.selectByFileId(16L)).thenReturn(normalMeta(16L, 99L, null));
        when(permissionClient.dataScope(USER_ID)).thenReturn(scopeResult(false, List.of(), USER_ID));

        BizException ex = assertThrows(BizException.class, () -> service.download(16L, USER_ID, null, null));
        assertEquals(ErrorCode.FILE_ACCESS_DENIED.getCode(), ex.getCode());
        verify(fileStorage, never()).open(any());
    }

    // ---------- delete ----------

    @Test
    void deleteMarksDeletedAndRemovesContent() {
        FileMeta meta = normalMeta(16L, USER_ID, 3L);
        when(fileMetaMapper.selectByFileId(16L)).thenReturn(meta);
        when(permissionClient.dataScope(USER_ID)).thenReturn(scopeResult(true, List.of(), null));
        when(fileMetaMapper.updateById(any(FileMeta.class))).thenReturn(1);
        when(fileMetaMapper.countByStoragePath("2026/10/13/uuid.txt")).thenReturn(0L);
        when(fileStorage.delete("2026/10/13/uuid.txt")).thenReturn(true);

        service.delete(16L, USER_ID);

        assertEquals(FileMeta.STATUS_DELETED, meta.getStatus());
        verify(fileMetaMapper).updateById(meta);
        verify(fileStorage).delete("2026/10/13/uuid.txt");
    }

    @Test
    void deleteKeepsContentWhenStillReferenced() {
        FileMeta meta = normalMeta(16L, USER_ID, 3L);
        when(fileMetaMapper.selectByFileId(16L)).thenReturn(meta);
        when(permissionClient.dataScope(USER_ID)).thenReturn(scopeResult(true, List.of(), null));
        when(fileMetaMapper.updateById(any(FileMeta.class))).thenReturn(1);
        when(fileMetaMapper.countByStoragePath("2026/10/13/uuid.txt")).thenReturn(1L);

        service.delete(16L, USER_ID);

        assertEquals(FileMeta.STATUS_DELETED, meta.getStatus());
        verify(fileStorage, never()).delete(anyString());
    }

    @Test
    void deleteDeniesWhenOutOfScope() {
        when(fileMetaMapper.selectByFileId(16L)).thenReturn(normalMeta(16L, 99L, null));
        when(permissionClient.dataScope(USER_ID)).thenReturn(scopeResult(false, List.of(1L), null));

        BizException ex = assertThrows(BizException.class, () -> service.delete(16L, USER_ID));
        assertEquals(ErrorCode.FILE_ACCESS_DENIED.getCode(), ex.getCode());
        verify(fileMetaMapper, never()).updateById(any(FileMeta.class));
    }

    // ---------- 分片上传 / 秒传 ----------

    @Test
    void initChunkUploadInstantlyReusesExistingContent() {
        ChunkInitRequest request = new ChunkInitRequest();
        request.setFileName("a.txt");
        request.setTotalChunks(2);
        request.setTotalSize(5L);
        request.setSha256("sha");
        when(metaIdClient.nextId("file")).thenReturn(Result.success(16L));
        when(shardRouter.route(16L)).thenReturn(0);
        when(permissionClient.userDept(USER_ID)).thenReturn(Result.success(3L));
        when(fileMetaMapper.selectBySha256AndSize("sha", 5L)).thenReturn(normalMeta(16L, USER_ID, 1L));
        when(fileMetaMapper.insert(any(FileMeta.class))).thenReturn(1);
        when(fileMetaMapper.selectByFileId(16L)).thenReturn(normalMeta(16L, USER_ID, 3L));

        ChunkInitResult result = service.initChunkUpload(request, USER_ID);

        assertTrue(result.instantUpload());
        assertNull(result.uploadId());
        assertNotNull(result.file());
        verify(fileStorage, never()).storeChunkSession(anyString(), any());
    }

    @Test
    void fingerprintAloneCannotCopyAnotherUsersContent() {
        ChunkInitRequest request = new ChunkInitRequest();
        request.setFileName("a.txt"); request.setTotalChunks(2); request.setTotalSize(5L); request.setSha256("sha");
        when(metaIdClient.nextId("file")).thenReturn(Result.success(16L));
        when(shardRouter.route(16L)).thenReturn(0);
        when(fileMetaMapper.selectBySha256AndSize("sha",5L)).thenReturn(normalMeta(16L,99L,1L));
        ChunkInitResult result=service.initChunkUpload(request,USER_ID);
        assertFalse(result.instantUpload());
        verify(fileMetaMapper,never()).insert(any(FileMeta.class));
    }

    @Test
    void chunkSessionsAreOnlyAccessibleToTheirCreator() {
        var session=session("up1",2,LocalDateTime.now(java.time.ZoneOffset.UTC));
        when(fileStorage.loadChunkSession("up1")).thenReturn(sessionBytes(session));
        service.requireChunkOwner("up1",session.getUserId());
        assertThrows(BizException.class,()->service.requireChunkOwner("up1",session.getUserId()+1));
    }

    @Test
    void initChunkUploadCreatesSessionWhenNotDuplicate() {
        ChunkInitRequest request = new ChunkInitRequest();
        request.setFileName("a.txt");
        request.setTotalChunks(2);
        request.setTotalSize(5L);
        when(metaIdClient.nextId("file")).thenReturn(Result.success(16L));
        when(shardRouter.route(16L)).thenReturn(0);
        when(permissionClient.userDept(USER_ID)).thenReturn(Result.success(3L));

        ChunkInitResult result = service.initChunkUpload(request, USER_ID);

        assertFalse(result.instantUpload());
        assertNotNull(result.uploadId());
        assertNull(result.file());
        verify(fileStorage).storeChunkSession(eq(result.uploadId()), any(byte[].class));
    }

    @Test
    void uploadChunkRejectsOutOfRangeIndex() {
        when(fileStorage.loadChunkSession("up1"))
                .thenReturn(sessionBytes(session("up1", 2, LocalDateTime.now(java.time.ZoneOffset.UTC))));

        BizException ex = assertThrows(BizException.class,
                () -> service.uploadChunk("up1", 5, txtFile("hello")));
        assertEquals(ErrorCode.FILE_CHUNK_INDEX_INVALID.getCode(), ex.getCode());
        verify(fileStorage, never()).storeChunk(anyString(), any(Integer.class), any());
    }

    @Test
    void uploadChunkStoresContent() {
        when(fileStorage.loadChunkSession("up1"))
                .thenReturn(sessionBytes(session("up1", 2, LocalDateTime.now(java.time.ZoneOffset.UTC))));

        service.uploadChunk("up1", 1, txtFile("hello"));

        verify(fileStorage).storeChunk(eq("up1"), eq(1), any(InputStream.class));
    }

    @Test
    void chunkStatusReturnsUploadedIndexes() {
        when(fileStorage.loadChunkSession("up1"))
                .thenReturn(sessionBytes(session("up1", 3, LocalDateTime.now(java.time.ZoneOffset.UTC))));
        when(fileStorage.listChunks("up1")).thenReturn(new TreeSet<>(Set.of(2, 0)));

        ChunkUploadStatus status = service.chunkStatus("up1");

        assertEquals(3, status.totalChunks());
        assertEquals(List.of(0, 2), status.uploadedChunks());
    }

    @Test
    void completeChunkUploadMergesAndPersists() {
        when(fileStorage.loadChunkSession("up1"))
                .thenReturn(sessionBytes(session("up1", 2, LocalDateTime.now(java.time.ZoneOffset.UTC))));
        when(fileStorage.mergeChunks("up1", 2, "txt"))
                .thenReturn(new FileStorage.StoredObject("2026/10/13/new.txt", 5L, "sha"));
        when(fileMetaMapper.selectBySha256AndSize("sha", 5L)).thenReturn(null);
        when(fileMetaMapper.insert(any(FileMeta.class))).thenReturn(1);
        when(fileMetaMapper.selectByFileId(16L)).thenReturn(normalMeta(16L, USER_ID, 3L));

        FileView view = service.completeChunkUpload("up1", USER_ID);

        assertEquals(16L, view.getFileId());
        verify(fileStorage, never()).delete(anyString());
        assertNull(ShardContextHolder.get(), "合并落库结束后必须清理分片上下文");
    }

    @Test
    void completeChunkUploadReusesDuplicateStorage() {
        when(fileStorage.loadChunkSession("up1"))
                .thenReturn(sessionBytes(session("up1", 2, LocalDateTime.now(java.time.ZoneOffset.UTC))));
        when(fileStorage.mergeChunks("up1", 2, "txt"))
                .thenReturn(new FileStorage.StoredObject("2026/10/13/new.txt", 5L, "sha"));
        FileMeta duplicate = normalMeta(16L, 99L, 1L);
        duplicate.setStoragePath("2026/10/13/old.txt");
        when(fileMetaMapper.selectBySha256AndSize("sha", 5L)).thenReturn(duplicate);
        when(fileMetaMapper.insert(any(FileMeta.class))).thenReturn(1);
        when(fileMetaMapper.selectByFileId(16L)).thenReturn(normalMeta(16L, USER_ID, 3L));

        service.completeChunkUpload("up1", USER_ID);

        ArgumentCaptor<FileMeta> captor = ArgumentCaptor.forClass(FileMeta.class);
        verify(fileMetaMapper).insert(captor.capture());
        assertEquals("2026/10/13/old.txt", captor.getValue().getStoragePath(), "应复用已有存储路径");
        verify(fileStorage).delete("2026/10/13/new.txt");
    }

    @Test
    void abortChunkUploadClearsTempChunks() {
        when(fileStorage.loadChunkSession("up1"))
                .thenReturn(sessionBytes(session("up1", 2, LocalDateTime.now(java.time.ZoneOffset.UTC))));

        service.abortChunkUpload("up1");

        verify(fileStorage).abortChunks("up1");
    }

    @Test
    void cleanupRemovesExpiredSessions() {
        UploadSession expired = session("old", 2, LocalDateTime.now(java.time.ZoneOffset.UTC).minusDays(2));
        when(fileStorage.listChunkUploads()).thenReturn(List.of("old", "broken"));
        when(fileStorage.loadChunkSession("old")).thenReturn(sessionBytes(expired));
        when(fileStorage.loadChunkSession("broken")).thenReturn(null);

        int removed = service.cleanupExpiredChunkUploads();

        assertEquals(2, removed);
        verify(fileStorage).abortChunks("old");
        verify(fileStorage).abortChunks("broken");
    }

    // ---------- listVisible ----------

    @Test
    void listVisibleScansEveryPhysicalShard() {
        when(permissionClient.dataScope(USER_ID)).thenReturn(scopeResult(true, List.of(), null));
        when(fileMetaMapper.selectVisible()).thenReturn(List.of(normalMeta(16L, USER_ID, 3L)));

        PageResult<FileView> result = service.listVisible(USER_ID, 1, 20);

        assertEquals(properties.getShard().getPhysicalCount(), result.getRecords().size(), "每个物理库各返回一条");
        assertEquals(properties.getShard().getPhysicalCount(), result.getTotal(), "总数为各物理库汇总");
        verify(fileMetaMapper, times(properties.getShard().getPhysicalCount())).selectVisible();
        assertNull(ShardContextHolder.get(), "列表扫描结束后必须清理分片上下文");
        assertNull(DataScopeContext.get(), "列表扫描结束后必须清理数据范围上下文");
    }

    @Test
    void listVisiblePropagatesDataScopeFailure() {
        when(permissionClient.dataScope(USER_ID)).thenThrow(new RuntimeException("permission down"));

        BizException ex = assertThrows(BizException.class, () -> service.listVisible(USER_ID, 1, 20));
        assertEquals(ErrorCode.INTERNAL_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("数据范围"));
    }
    @Test
    void personalDeletionWorksWithoutAnyDepartmentRole() {
        when(fileMetaMapper.selectByFileId(16L)).thenReturn(normalMeta(16L,USER_ID,3L));
        service.deleteOwned(16L,USER_ID);
        verify(fileMetaMapper).updateById(any(FileMeta.class));
        verifyNoInteractions(permissionClient);
    }

    @Test
    void personalDeletionCannotUseAdministrativeDataScopeToDeleteAnotherOwner() {
        when(fileMetaMapper.selectByFileId(16L)).thenReturn(normalMeta(16L,99L,3L));
        assertThrows(BizException.class,()->service.deleteOwned(16L,USER_ID));
        verify(fileMetaMapper,never()).updateById(any(FileMeta.class));
        verifyNoInteractions(permissionClient,fileStorage);
    }
}
