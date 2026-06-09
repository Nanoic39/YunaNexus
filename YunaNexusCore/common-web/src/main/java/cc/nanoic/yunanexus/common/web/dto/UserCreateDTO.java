package cc.nanoic.yunanexus.common.web.dto;

import lombok.Data;

@Data
public class UserCreateDTO {
    private String uuid;
    // 如果传byte[]的话会被JSON转化为base64
    // 所以这里直接当作字符串传递
    private String globalId;
    private String nickname;
    private String gender;
}
