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
}
