package cc.nanoic.yunanexus.file.service;
import cc.nanoic.yunanexus.file.mapper.*;
import cc.nanoic.yunanexus.file.entity.*;
import cc.nanoic.yunanexus.file.config.*;
import cc.nanoic.yunanexus.file.storage.FileStorage;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import java.time.LocalDateTime;
import java.util.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersonalWorkspaceTest {
    FileMetaMapper files=mock(FileMetaMapper.class);
    FileStorage storage=mock(FileStorage.class);
    FileService legacy=mock(FileService.class);
    PersonalFileService personal=new PersonalFileService(files,id->0,new FileProperties(),storage,legacy,mock(FileShareMapper.class));
    FileShareMapper shares=mock(FileShareMapper.class);
    FileShareService service=new FileShareService(personal,shares);
    @Test void strictOwnershipAppliesEvenWithoutDepartmentScope(){
        FileMeta file=file(4,8);when(files.selectByFileId(4)).thenReturn(file);
        assertThrows(BizException.class,()->personal.requireOwned(4,9));
        assertThrows(BizException.class,()->personal.download(4,9));
        assertThrows(BizException.class,()->personal.delete(4,9));verifyNoInteractions(legacy,storage);
        assertEquals(file,personal.requireOwned(4,8));
        file.setStatus(0);assertThrows(BizException.class,()->personal.requireOwned(4,8));
    }
    @Test void shardContextIsRestoredAfterFailure(){ShardContextHolder.set(7);try{assertThrows(IllegalStateException.class,()->personal.inShard(0,()->{throw new IllegalStateException();}));assertEquals(7,ShardContextHolder.get());}finally{ShardContextHolder.clear();}}
    @Test void rejectsUnboundedPagination(){assertThrows(BizException.class,()->personal.list(9,Integer.MAX_VALUE,20,""));assertThrows(BizException.class,()->personal.list(9,1,101,""));verifyNoInteractions(files);}
    @Test void refusesShareOfAnotherUsersFile(){when(files.selectByFileId(4)).thenReturn(file(4,8));assertThrows(BizException.class,()->service.create(9,4,7,"1234"));verifyNoInteractions(shares);}
    @Test void sharesHaveBoundedExpiryAndSaltedCode(){when(files.selectByFileId(4)).thenReturn(file(4,9));var s=service.create(9,4,7,"1234");assertEquals(43,s.shareId().length());assertTrue(s.protectedByCode());var captor=org.mockito.ArgumentCaptor.forClass(FileShare.class);verify(shares).insert(captor.capture());assertNotEquals("1234",captor.getValue().getCodeHash());assertEquals(FileShareService.hash("1234",s.shareId()),captor.getValue().getCodeHash());assertThrows(BizException.class,()->service.create(9,4,31,""));}
    @Test void expiryRevocationAndDeletedFileAreEnforcedOnEveryRead(){var s=share();when(shares.find(s.getShareId())).thenReturn(s);when(files.selectByFileId(4)).thenReturn(file(4,9));assertEquals(s,service.redeem(s.getShareId(),""));s.setRevoked(1);assertThrows(BizException.class,()->service.redeem(s.getShareId(),""));s.setRevoked(0);s.setExpiresAt(LocalDateTime.now().minusSeconds(1));assertThrows(BizException.class,()->service.redeem(s.getShareId(),""));s.setExpiresAt(LocalDateTime.now().plusDays(1));when(files.selectByFileId(4)).thenReturn(null);assertThrows(BizException.class,()->service.redeem(s.getShareId(),""));}
    @Test void protectedSharesConsumeRateBudgetBeforeCheckingCode(){var s=share();s.setCodeHash(FileShareService.hash("1234",s.getShareId()));when(shares.find(s.getShareId())).thenReturn(s);when(shares.failure(s.getShareId())).thenReturn(1);assertThrows(BizException.class,()->service.redeem(s.getShareId(),"wrong"));verify(shares,never()).resetFailures(anyString());when(shares.failure(s.getShareId())).thenReturn(0);assertThrows(BizException.class,()->service.redeem(s.getShareId(),"1234"));verifyNoInteractions(files);}
    @Test void revokeUsesOwnerPredicate(){assertThrows(BizException.class,()->service.revoke(9,"id"));verify(shares).revoke("id",9);}
    @Test void avatarRejectsNonRasterAndReencodesToSquare() throws Exception {
        var avatars=new AvatarService(legacy);
        assertThrows(BizException.class,()->avatars.upload(new MockMultipartFile("file","evil.svg","image/png","<svg/>".getBytes()),9));
        BufferedImage image=new BufferedImage(40,20,BufferedImage.TYPE_INT_RGB);ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);
        avatars.upload(new MockMultipartFile("file","avatar.png","image/png",out.toByteArray()),9);
        var captor=org.mockito.ArgumentCaptor.forClass(org.springframework.web.multipart.MultipartFile.class);verify(legacy).upload(captor.capture(),eq(9L));var decoded=ImageIO.read(captor.getValue().getInputStream());assertEquals(512,decoded.getWidth());assertEquals(512,decoded.getHeight());assertEquals("image/jpeg",captor.getValue().getContentType());
    }
    private FileMeta file(long id,long owner){FileMeta f=new FileMeta();f.setFileId(id);f.setCreateBy(owner);f.setStatus(1);f.setFileName("test.txt");f.setSize(3L);return f;}
    private FileShare share(){FileShare s=new FileShare();s.setShareId("a".repeat(43));s.setUserId(9L);s.setFileId(4L);s.setRevoked(0);s.setExpiresAt(LocalDateTime.now().plusDays(1));return s;}
}
