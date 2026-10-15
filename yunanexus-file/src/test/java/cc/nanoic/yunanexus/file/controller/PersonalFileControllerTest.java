package cc.nanoic.yunanexus.file.controller;
import cc.nanoic.yunanexus.file.service.*;
import cc.nanoic.yunanexus.file.domain.*;
import cc.nanoic.yunanexus.common.core.model.PageResult;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.io.ByteArrayInputStream;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** 真正经过 MVC 参数解析，验证无 -parameters 的生产构建仍可调用本人文件接口。 */
class PersonalFileControllerTest {
    PersonalFileService personal=mock(PersonalFileService.class);
    FileService files=mock(FileService.class);
    AvatarService avatars=mock(AvatarService.class);
    MockMvc mvc=MockMvcBuilders.standaloneSetup(new PersonalFileController(personal,files,avatars)).build();
    @Test void multipartHasExplicitFieldBinding() throws Exception {
        mvc.perform(multipart("/api/v1/file/me/upload").file(new MockMultipartFile("file","test.txt","text/plain",new byte[]{1})).header("X-User-Id",9))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(files).upload(any(),eq(9L));
        mvc.perform(multipart("/api/v1/file/me/avatar").file(new MockMultipartFile("file","test.png","image/png",new byte[]{1})).header("X-User-Id",9)).andExpect(status().isOk());
        verify(avatars).upload(any(),eq(9L));
    }
    @Test void queryAndPathVariablesBindWithoutCompilerParameterNames() throws Exception {
        when(personal.list(9,2,20,"report")).thenReturn(PageResult.empty(2,20));
        mvc.perform(get("/api/v1/file/me").queryParam("page","2").queryParam("search","report").header("X-User-Id",9)).andExpect(status().isOk());
        verify(personal).list(9,2,20,"report");
        when(personal.download(12,9)).thenReturn(new FileRange("file.txt","text/plain",1,0,0,false,new ByteArrayInputStream(new byte[]{42})));
        mvc.perform(get("/api/v1/file/me/12/download").header("X-User-Id",9)).andExpect(status().isOk()).andExpect(content().bytes(new byte[]{42}));
        mvc.perform(post("/api/v1/file/me/12/delete").header("X-User-Id",9)).andExpect(status().isOk());verify(personal).delete(12,9);
    }
}
