package cc.nanoic.yunanexus.file.service;
import cc.nanoic.yunanexus.file.mapper.PublicFileMapper;
import cc.nanoic.yunanexus.file.entity.FileMeta;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.ArgumentMatchers;
import java.util.function.Supplier;

class PublicFileServiceTest {
    PersonalFileService personal=mock(PersonalFileService.class);
    PublicFileMapper mapper=mock(PublicFileMapper.class);
    PublicFileService service=new PublicFileService(personal,mapper);
    PublicFileServiceTest(){when(personal.inShard(eq(0),ArgumentMatchers.<Supplier<Object>>any())).thenAnswer(i->((Supplier<?>)i.getArgument(1)).get());}
    @Test void defaultsPrivateAndOwnerMustExplicitlyPublish(){
        assertFalse(service.visibility(1,2).publicAccess());
        verify(personal).requireOwned(1,2);
        verify(mapper,never()).publish(anyLong(),anyLong(),anyString());
    }
    @Test void foreignOwnerCannotPublishOrRevoke(){
        when(personal.requireOwned(1,3)).thenThrow(new BizException(cc.nanoic.yunanexus.common.web.constant.ErrorCode.FILE_NOT_FOUND));
        assertThrows(BizException.class,()->service.set(1,3,true));
        assertThrows(BizException.class,()->service.set(1,3,false));
        verifyNoInteractions(mapper);
    }
    @Test void publicLinkHasNoOwnerParameterAndRechecksDeletedMetadata(){
        String token="a".repeat(43);
        when(mapper.resolve(token)).thenReturn(new PublicFileMapper.Publication(1,2,token));
        when(personal.download(1,2)).thenThrow(new BizException(cc.nanoic.yunanexus.common.web.constant.ErrorCode.FILE_NOT_FOUND));
        assertThrows(BizException.class,()->service.open(token));
        verify(personal).download(1,2);
    }
    @Test void revokedOrMalformedLinkFailsClosed(){
        assertThrows(BizException.class,()->service.open("../private"));
        verifyNoInteractions(mapper);
        assertThrows(BizException.class,()->service.open("a".repeat(43)));
        verify(personal,never()).download(anyLong(),anyLong());
    }
    @Test void revokeRemovesPublishedMapping(){service.set(1,2,false);verify(mapper).revoke(1,2);}
}
