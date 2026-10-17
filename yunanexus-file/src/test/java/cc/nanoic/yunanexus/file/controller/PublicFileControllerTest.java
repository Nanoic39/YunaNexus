package cc.nanoic.yunanexus.file.controller;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class PublicFileControllerTest {
 @Test void activeContentNeverRenderedInline(){
  for(String text:new String[]{"<svg onload=alert(1)>","<html>","GIFxxx","RIFFxxxxHTML"})assertNull(PublicFileController.rasterType(text.getBytes(StandardCharsets.US_ASCII)));
 }
 @Test void imageHeadersDetectedIndependentlyOfUntrustedMime(){
  assertEquals("image/png",PublicFileController.rasterType(new byte[]{(byte)137,80,78,71,13,10,26,10}));
  assertEquals("image/jpeg",PublicFileController.rasterType(new byte[]{(byte)255,(byte)216,(byte)255}));
  assertEquals("image/gif",PublicFileController.rasterType("GIF89a".getBytes(StandardCharsets.US_ASCII)));
  assertEquals("image/webp",PublicFileController.rasterType("RIFF0000WEBP".getBytes(StandardCharsets.US_ASCII)));
 }
 @Test void revokedLinkReturnsRealHttp404()throws Exception{
  var service=org.mockito.Mockito.mock(cc.nanoic.yunanexus.file.service.PublicFileService.class);
  org.mockito.Mockito.when(service.open("a".repeat(43))).thenThrow(new cc.nanoic.yunanexus.common.web.exception.BizException(cc.nanoic.yunanexus.common.web.constant.ErrorCode.FILE_NOT_FOUND));
  var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new PublicFileController(service)).build();
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/file/public/assets/"+"a".repeat(43))).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
 }
}
