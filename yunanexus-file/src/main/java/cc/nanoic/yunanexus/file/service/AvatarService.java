package cc.nanoic.yunanexus.file.service;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.file.domain.FileView;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Iterator;

/** 仅接收受限大小的静态栅格头像，解码前限制像素并重新编码，剥离元数据。 */
@Service
public class AvatarService {
    private final FileService files;
    public AvatarService(FileService files) { this.files=files; }
    public FileView upload(MultipartFile file,long userId) {
        if(file==null || file.isEmpty() || file.getSize()>5*1024*1024)
            throw invalid();
        try(var input=new MemoryCacheImageInputStream(file.getInputStream())) {
            Iterator<ImageReader> readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext()) throw invalid();
            ImageReader reader=readers.next();
            try {
                if(!java.util.Set.of("png","jpeg").contains(reader.getFormatName().toLowerCase(java.util.Locale.ROOT))) throw invalid();
                reader.setInput(input,true,true);
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1 || height<1 || width>8192 || height>8192 || (long)width*height>16_000_000) throw invalid();
                BufferedImage source=reader.read(0), target=new BufferedImage(512,512,BufferedImage.TYPE_INT_RGB);
                Graphics2D g=target.createGraphics();
                try {
                    g.setColor(Color.WHITE); g.fillRect(0,0,512,512);
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    int side=Math.min(width,height),x=(width-side)/2,y=(height-side)/2;
                    g.drawImage(source,0,0,512,512,x,y,x+side,y+side,null);
                } finally { g.dispose(); }
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                ImageIO.write(target,"jpg",out);
                return files.upload(new AvatarFile(out.toByteArray()),userId);
            } finally { reader.dispose(); }
        } catch(IOException e) { throw invalid(); }
    }
    private BizException invalid() { return new BizException(ErrorCode.BAD_REQUEST,"请选择不超过 5MB、1600 万像素的 JPEG 或 PNG 图片"); }
    private record AvatarFile(byte[] bytes) implements MultipartFile {
        public String getName(){return "file";}
        public String getOriginalFilename(){return "avatar.jpg";}
        public String getContentType(){return "image/jpeg";}
        public boolean isEmpty(){return bytes.length==0;}
        public long getSize(){return bytes.length;}
        public byte[] getBytes(){return bytes;}
        public InputStream getInputStream(){return new ByteArrayInputStream(bytes);}
        public void transferTo(File file) throws IOException {throw new IOException("Unsupported destination");}
    }
}
