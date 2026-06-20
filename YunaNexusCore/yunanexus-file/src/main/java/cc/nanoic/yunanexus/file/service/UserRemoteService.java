package cc.nanoic.yunanexus.file.service;

import cn.hutool.core.util.HexUtil;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class UserRemoteService {

    private static final String GATEWAY_URL = "lb://YunaNexus-Gateway";

    private final RestTemplate restTemplate;

    public UserRemoteService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void updateAvatar(byte[] globalId, String avatarUuid) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Shard-Key", HexUtil.encodeHexStr(globalId));

        Map<String, String> body = new HashMap<>();
        body.put("globalId", HexUtil.encodeHexStr(globalId));
        body.put("avatarUuid", avatarUuid);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
        restTemplate.exchange(GATEWAY_URL + "/internal/user/avatar",
                HttpMethod.PUT, entity, String.class);
    }
}