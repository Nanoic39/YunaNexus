package cc.nanoic.yunanexus.auth.service;

import cc.nanoic.yunanexus.common.web.common.Result;
import cc.nanoic.yunanexus.common.web.dto.UserCreateDTO;
import cn.hutool.core.util.HexUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class UserRemoteService {

    private static final String GATEWAY_URL = "lb://YunaNexus-Gateway";

    private final RestTemplate restTemplate;

    public UserRemoteService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Result<?> createUser(UserCreateDTO dto) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Shard-Key", dto.getGlobalId());
        HttpEntity<UserCreateDTO> entity = new HttpEntity<>(dto, headers);
        String resp = restTemplate.postForObject(
                GATEWAY_URL + "/internal/user/create", entity, String.class);
        return JSON.parseObject(resp, Result.class);
    }

    public Result<?> cancelUser(byte[] globalId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Shard-Key", HexUtil.encodeHexStr(globalId));
        HttpEntity<byte[]> entity = new HttpEntity<>(globalId, headers);
        String resp = restTemplate.postForObject(
                GATEWAY_URL + "/internal/user/cancel", entity, String.class);
        return JSON.parseObject(resp, Result.class);
    }

    public Result<String> getUuid(byte[] globalId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Shard-Key", HexUtil.encodeHexStr(globalId));
        HttpEntity<byte[]> entity = new HttpEntity<>(globalId, headers);
        String resp = restTemplate.postForObject(
                GATEWAY_URL + "/internal/user/uuid", entity, String.class);
        JSONObject obj = JSON.parseObject(resp);
        Result<String> result = new Result<>();
        result.setCode(obj.getInteger("code"));
        result.setMsg(obj.getString("msg"));
        result.setData(obj.getString("data"));
        return result;
    }
}