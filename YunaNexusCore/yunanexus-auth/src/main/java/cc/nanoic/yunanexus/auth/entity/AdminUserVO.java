package cc.nanoic.yunanexus.auth.entity;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AdminUserVO {
    private String globalId;
    private String uuid;
    private String username;
    private String email;
    private String phone;
    private Integer status;
    private String createdAt;
    private String nickname;
    private String avatarUuid;
    private String gender;
    private String birthday;
    private String bio;
    private List<Map<String, Object>> roles;
}
