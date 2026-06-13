package cc.nanoic.yunanexus.gateway.filter;

import cn.hutool.core.util.IdUtil;
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
import java.util.Map;

@Component
public class RouteForwardFilter implements Filter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RouteForwardFilter.class);

    private final DiscoveryClient discoveryClient;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final Map<String, String[]> ROUTE_PATHS = Map.of(
            "YunaNexus-AuthService", new String[]{"/auth", "/login", "/register", "/key", "/oauth"},
            "YunaNexus-UserService", new String[]{"/user", "/me"}
    );

    public RouteForwardFilter(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpRes = (HttpServletResponse) response;

        String origin = httpReq.getHeader("Origin");

        if ("OPTIONS".equalsIgnoreCase(httpReq.getMethod())) {
            setCorsHeaders(httpRes, origin);
            httpRes.setHeader("Access-Control-Allow-Methods", "*");
            httpRes.setHeader("Access-Control-Allow-Headers", "*");
            httpRes.setHeader("Access-Control-Max-Age", "3600");
            httpRes.setStatus(200);
            return;
        }

        String path = httpReq.getRequestURI();
        String serviceName = resolveService(path);
        if (serviceName == null) {
            setCorsHeaders(httpRes, origin);
            httpRes.sendError(HttpServletResponse.SC_NOT_FOUND, "No route for: " + path);
            return;
        }

        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        if (instances.isEmpty()) {
            setCorsHeaders(httpRes, origin);
            httpRes.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Service unavailable: " + serviceName);
            return;
        }
        ServiceInstance instance = instances.get(0);
        String targetUrl = instance.getUri().toString() + path;
        if (httpReq.getQueryString() != null) {
            targetUrl += "?" + httpReq.getQueryString();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            Enumeration<String> headerNames = httpReq.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                if ("host".equalsIgnoreCase(name) || "connection".equalsIgnoreCase(name)) {
                    continue;
                }
                headers.put(name, Collections.list(httpReq.getHeaders(name)));
            }

            String traceId = httpReq.getHeader("X-Trace-Id");
            if (traceId == null || traceId.isEmpty()) {
                traceId = IdUtil.fastSimpleUUID();
            }
            headers.set("X-Trace-Id", traceId);

            byte[] body = StreamUtils.copyToByteArray(httpReq.getInputStream());
            HttpMethod method = HttpMethod.valueOf(httpReq.getMethod());
            ResponseEntity<byte[]> resp = restTemplate.exchange(
                    targetUrl, method, new HttpEntity<>(body, headers), byte[].class);

            setCorsHeaders(httpRes, origin);
            httpRes.setStatus(resp.getStatusCode().value());
            resp.getHeaders().forEach((key, values) -> {
                String lowerKey = key.toLowerCase();
                if ("transfer-encoding".equals(lowerKey)
                        || "connection".equals(lowerKey)
                        || lowerKey.startsWith("access-control-")) {
                    return;
                }
                values.forEach(v -> httpRes.addHeader(key, v));
            });
            if (resp.getBody() != null) {
                httpRes.getOutputStream().write(resp.getBody());
            }
        } catch (Exception e) {
            log.error("Forward failed: {} -> {}", path, targetUrl, e);
            if (!httpRes.isCommitted()) {
                setCorsHeaders(httpRes, origin);
                httpRes.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Gateway forward failed");
            }
        }
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

    private String resolveService(String path) {
        for (Map.Entry<String, String[]> entry : ROUTE_PATHS.entrySet()) {
            for (String prefix : entry.getValue()) {
                if (path.equals(prefix) || path.startsWith(prefix + "/") || path.startsWith(prefix + "?")) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}