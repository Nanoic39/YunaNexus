package cc.nanoic.yunanexus.gateway.filter;

import cc.nanoic.yunanexus.common.web.auth.JwtUtil;
import cc.nanoic.yunanexus.common.web.auth.JwtUtil.JwtPayload;
import org.springframework.beans.factory.annotation.Value;
import cc.nanoic.yunanexus.gateway.config.ShardRouteProperties;
import cn.hutool.core.util.HexUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.core.Ordered;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

@Component
public class ShardRoutingFilter implements Filter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ShardRoutingFilter.class);

    private final ShardRouteProperties shardRouteProperties;
    private final DiscoveryClient discoveryClient;
    private final LoadBalancerClient loadBalancerClient;
    private final RestTemplate restTemplate;

    @Value("${yunanexus.web.jwt.secret}")
    private String jwtSecret;

    public ShardRoutingFilter(ShardRouteProperties shardRouteProperties,
            DiscoveryClient discoveryClient,
            LoadBalancerClient loadBalancerClient,
            RestTemplate restTemplate) {
        this.shardRouteProperties = shardRouteProperties;
        this.discoveryClient = discoveryClient;
        this.loadBalancerClient = loadBalancerClient;
        this.restTemplate = restTemplate;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpRes = (HttpServletResponse) response;
        String path = httpReq.getRequestURI();

        ShardRouteProperties.RouteConfig matched = shardRouteProperties.getRoutes().stream()
                .filter(r -> r.matches(path))
                .findFirst().orElse(null);

        if (matched == null) {
            chain.doFilter(request, response);
            return;
        }

        String origin = httpReq.getHeader("Origin");

        if ("OPTIONS".equalsIgnoreCase(httpReq.getMethod())) {
            setCorsHeaders(httpRes, origin);
            httpRes.setHeader("Access-Control-Allow-Methods", "*");
            httpRes.setHeader("Access-Control-Allow-Headers", "*");
            httpRes.setHeader("Access-Control-Max-Age", "3600");
            httpRes.setStatus(200);
            return;
        }

        byte[] globalId = extractGlobalId(httpReq);
        if (globalId == null) {
            setCorsHeaders(httpRes, origin);
            httpRes.sendError(HttpServletResponse.SC_UNAUTHORIZED, "无法确定用户身份");
            return;
        }

        int shardCount = resolveShardCount(matched.getBaseName());
        String serviceName;
        int shard = 0;
        if (shardCount > 0) {
            shard = Math.abs(hashBytes(globalId)) % shardCount;
            serviceName = matched.getBaseName() + "-" + shard;
        } else {
            serviceName = matched.getBaseName();
        }

        ServiceInstance instance = loadBalancerClient.choose(serviceName);
        if (instance == null) {
            setCorsHeaders(httpRes, origin);
            httpRes.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Shard unavailable: " + serviceName);
            return;
        }

        String targetUrl = instance.getUri().toString() + path;
        String query = httpReq.getQueryString();
        if (query != null) {
            targetUrl += "?" + query;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            Enumeration<String> names = httpReq.getHeaderNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                if ("host".equalsIgnoreCase(name) || "connection".equalsIgnoreCase(name)) {
                    continue;
                }
                headers.put(name, Collections.list(httpReq.getHeaders(name)));
            }
            headers.set("X-Shard-Index", String.valueOf(shard));

            byte[] body = StreamUtils.copyToByteArray(httpReq.getInputStream());
            HttpMethod method = HttpMethod.valueOf(httpReq.getMethod());
            ResponseEntity<byte[]> resp = restTemplate.exchange(
                    targetUrl, method, new HttpEntity<>(body, headers), byte[].class);

            setCorsHeaders(httpRes, origin);
            httpRes.setStatus(resp.getStatusCode().value());
            resp.getHeaders().forEach((key, values) -> {
                String lower = key.toLowerCase();
                if ("transfer-encoding".equals(lower)
                        || "connection".equals(lower)
                        || lower.startsWith("access-control-")) {
                    return;
                }
                values.forEach(v -> httpRes.addHeader(key, v));
            });
            if (resp.getBody() != null) {
                httpRes.getOutputStream().write(resp.getBody());
            }
        } catch (Exception e) {
            log.error("Shard forward failed: {} -> {}", path, targetUrl, e);
            if (!httpRes.isCommitted()) {
                setCorsHeaders(httpRes, origin);
                httpRes.sendError(HttpServletResponse.SC_BAD_GATEWAY,
                        "Shard forward failed");
            }
        }
    }

    private int resolveShardCount(String baseName) {
        List<String> services = discoveryClient.getServices();
        return (int) services.stream()
                .filter(name -> name.startsWith(baseName + "-"))
                .count();
    }

    private byte[] extractGlobalId(HttpServletRequest req) {
        String shardKey = req.getHeader("X-Shard-Key");
        if (shardKey != null && !shardKey.isEmpty()) {
            return HexUtil.decodeHex(shardKey);
        }

        String authHeader = req.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7);
                JwtPayload payload = JwtUtil.parseToken(token, jwtSecret.getBytes());
                if (payload != null && payload.globalId != null) {
                    return payload.globalId;
                }
            } catch (Exception e) {
                log.debug("JWT parse failed for shard routing: {}", e.getMessage());
            }
        }

        return null;
    }

    private int hashBytes(byte[] data) {
        int hash = 0;
        for (byte b : data) {
            hash = 31 * hash + (b & 0xFF);
        }
        return Math.abs(hash);
    }

    private void setCorsHeaders(HttpServletResponse httpRes, String origin) {
        if (origin != null && !origin.isEmpty()) {
            httpRes.setHeader("Access-Control-Allow-Origin", origin);
            httpRes.setHeader("Access-Control-Allow-Credentials", "true");
        } else {
            httpRes.setHeader("Access-Control-Allow-Origin", "*");
        }
        httpRes.setHeader("Vary", "Origin");
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}