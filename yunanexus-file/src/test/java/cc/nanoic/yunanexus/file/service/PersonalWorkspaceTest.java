package cc.nanoic.yunanexus.file.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.file.config.*;
import cc.nanoic.yunanexus.file.entity.*;
import cc.nanoic.yunanexus.file.mapper.*;
import cc.nanoic.yunanexus.file.storage.FileStorage;

import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.*;

import javax.imageio.ImageIO;

class PersonalWorkspaceTest {
    FileMetaMapper files = mock(FileMetaMapper.class);
    FileStorage storage = mock(FileStorage.class);
    FileService legacy = mock(FileService.class);
    PersonalFileService personal =
            new PersonalFileService(
                    files,
                    id -> 0,
                    new FileProperties(),
                    storage,
                    legacy,
                    mock(FileShareMapper.class));
    FileShareMapper shares = mock(FileShareMapper.class);
    FileShareService service = new FileShareService(personal, shares);

    @Test
    void strictOwnershipAppliesEvenWithoutDepartmentScope() {
        FileMeta file = file(4, 8);
        when(files.selectByFileId(4)).thenReturn(file);
        assertThrows(BizException.class, () -> personal.requireOwned(4, 9));
        assertThrows(BizException.class, () -> personal.download(4, 9));
        assertThrows(BizException.class, () -> personal.delete(4, 9));
        verifyNoInteractions(legacy, storage);
        assertEquals(file, personal.requireOwned(4, 8));
        file.setStatus(0);
        assertThrows(BizException.class, () -> personal.requireOwned(4, 8));
    }

    @Test
    void shardContextIsRestoredAfterFailure() {
        ShardContextHolder.set(7);
        try {
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            personal.inShard(
                                    0,
                                    () -> {
                                        throw new IllegalStateException();
                                    }));
            assertEquals(7, ShardContextHolder.get());
        } finally {
            ShardContextHolder.clear();
        }
    }

    @Test
    void rejectsUnboundedPagination() {
        assertThrows(BizException.class, () -> personal.list(9, Integer.MAX_VALUE, 20, ""));
        assertThrows(BizException.class, () -> personal.list(9, 1, 101, ""));
        verifyNoInteractions(files);
    }

    @Test
    void refusesShareOfAnotherUsersFile() {
        when(files.selectByFileId(4)).thenReturn(file(4, 8));
        assertThrows(BizException.class, () -> service.create(9, 4, 7, "1234"));
        verifyNoInteractions(shares);
    }

    @Test
    void sharesHaveBoundedExpiryAndSaltedCode() {
        when(files.selectByFileId(4)).thenReturn(file(4, 9));
        var s = service.create(9, 4, 7, "1234");
        assertEquals(43, s.shareId().length());
        assertTrue(s.protectedByCode());
        var captor = org.mockito.ArgumentCaptor.forClass(FileShare.class);
        verify(shares).insert(captor.capture());
        assertNotEquals("1234", captor.getValue().getCodeHash());
        assertEquals(FileShareService.hash("1234", s.shareId()), captor.getValue().getCodeHash());
        assertThrows(BizException.class, () -> service.create(9, 4, 31, ""));
    }

    @Test
    void expiryRevocationAndDeletedFileAreEnforcedOnEveryRead() {
        var s = share();
        when(shares.find(s.getShareId())).thenReturn(s);
        when(files.selectByFileId(4)).thenReturn(file(4, 9));
        assertEquals(s, service.redeem(s.getShareId(), ""));
        s.setRevoked(1);
        assertThrows(BizException.class, () -> service.redeem(s.getShareId(), ""));
        s.setRevoked(0);
        s.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        assertThrows(BizException.class, () -> service.redeem(s.getShareId(), ""));
        s.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(files.selectByFileId(4)).thenReturn(null);
        assertThrows(BizException.class, () -> service.redeem(s.getShareId(), ""));
    }

    @Test
    void protectedSharesConsumeRateBudgetBeforeCheckingCode() {
        var s = share();
        s.setCodeHash(FileShareService.hash("1234", s.getShareId()));
        when(shares.find(s.getShareId())).thenReturn(s);
        when(shares.failure(s.getShareId())).thenReturn(1);
        assertThrows(BizException.class, () -> service.redeem(s.getShareId(), "wrong"));
        verify(shares, never()).resetFailures(anyString());
        when(shares.failure(s.getShareId())).thenReturn(0);
        assertThrows(BizException.class, () -> service.redeem(s.getShareId(), "1234"));
        verifyNoInteractions(files);
    }

    @Test
    void revokeUsesOwnerPredicate() {
        assertThrows(BizException.class, () -> service.revoke(9, "id"));
        verify(shares).revoke("id", 9);
    }

    @Test
    void avatarRejectsNonRasterAndReencodesToSquare() throws Exception {
        var avatars = new AvatarService(legacy);
        assertThrows(
                BizException.class,
                () ->
                        avatars.upload(
                                new MockMultipartFile(
                                        "file", "evil.svg", "image/png", "<svg/>".getBytes()),
                                9));
        BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        avatars.upload(
                new MockMultipartFile("file", "avatar.png", "image/png", out.toByteArray()), 9);
        var captor =
                org.mockito.ArgumentCaptor.forClass(
                        org.springframework.web.multipart.MultipartFile.class);
        verify(legacy).upload(captor.capture(), eq(9L));
        var decoded = ImageIO.read(captor.getValue().getInputStream());
        assertEquals(512, decoded.getWidth());
        assertEquals(512, decoded.getHeight());
        assertEquals("image/jpeg", captor.getValue().getContentType());
    }

    @Test
    void renameOnlyChangesOwnedActiveMetadata() {
        var f = file(4, 9);
        f.setStoragePath("objects/stored.txt");
        when(files.selectByFileId(4)).thenReturn(f);
        when(files.renameOwned(4, 9, "renamed.txt")).thenReturn(1);
        assertEquals("renamed.txt", personal.rename(4, 9, "  renamed.txt  ").getFileName());
        assertEquals("objects/stored.txt", f.getStoragePath());
        assertEquals("renamed.txt", personal.download(4, 9).fileName());
        verify(files).renameOwned(4, 9, "renamed.txt");
        verifyNoInteractions(legacy);
    }

    @Test
    void renameRejectsOtherOwnersDeletedRowsAndUnsafeNames() {
        when(files.selectByFileId(4)).thenReturn(file(4, 8));
        assertThrows(BizException.class, () -> personal.rename(4, 9, "name.txt"));
        when(files.selectByFileId(4)).thenReturn(file(4, 9));
        for (String name :
                List.of(
                        "",
                        "..",
                        "../name.txt",
                        "dir\\name.txt",
                        "evil\r\n.txt",
                        "name.exe",
                        "x".repeat(256)))
            assertThrows(BizException.class, () -> personal.rename(4, 9, name), name);
        assertThrows(BizException.class, () -> personal.rename(4, 9, null));
        when(files.renameOwned(4, 9, "name.txt")).thenReturn(0);
        assertThrows(BizException.class, () -> personal.rename(4, 9, "name.txt"));
        verifyNoInteractions(storage, legacy);
    }

    @Test
    void repeatedRenameIsIdempotentAndShareListUsesCurrentName() {
        var f = file(4, 9);
        when(files.selectByFileId(4)).thenReturn(f);
        assertEquals("test.txt", personal.rename(4, 9, "test.txt").getFileName());
        verify(files, never()).renameOwned(anyLong(), anyLong(), anyString());
        var s = share();
        s.setFileName("old.txt");
        when(shares.list(9)).thenReturn(List.of(s));
        when(files.selectOwnedNames(eq(9L), anyCollection())).thenReturn(List.of(f));
        assertEquals("test.txt", service.list(9).getFirst().fileName());
        verify(files, times(1)).selectOwnedNames(eq(9L), anyCollection());
    }

    private FileMeta file(long id, long owner) {
        FileMeta f = new FileMeta();
        f.setFileId(id);
        f.setCreateBy(owner);
        f.setStatus(1);
        f.setFileName("test.txt");
        f.setSize(3L);
        return f;
    }

    private FileShare share() {
        FileShare s = new FileShare();
        s.setShareId("a".repeat(43));
        s.setUserId(9L);
        s.setFileId(4L);
        s.setRevoked(0);
        s.setExpiresAt(LocalDateTime.now().plusDays(1));
        return s;
    }
}
