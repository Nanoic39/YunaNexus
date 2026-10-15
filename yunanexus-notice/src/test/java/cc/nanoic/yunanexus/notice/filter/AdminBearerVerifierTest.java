package cc.nanoic.yunanexus.notice.filter;
import cc.nanoic.yunanexus.common.web.security.AdminBearerVerifier;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class AdminBearerVerifierTest {
    @Test
    void validatesAuthoritativeBooleanAndFailsClosed() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/", exchange -> {
            String auth=exchange.getRequestHeaders().getFirst("Authorization");
            int status="Bearer revoked".equals(auth) ? 401 : 200;
            String json="Bearer root".equals(auth) ? "{\"code\":200,\"data\":true}"
                : "Bearer ordinary".equals(auth) ? "{\"code\":200,\"data\":false}"
                : "{\"code\":500,\"data\":true}";
            byte[] bytes=json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status,bytes.length);
            try(var out=exchange.getResponseBody()){out.write(bytes);}
        });
        server.start();
        try {
            var verifier=new AdminBearerVerifier("http://127.0.0.1:"+server.getAddress().getPort()+"/");
            assertEquals(200,verifier.verify("Bearer root"));
            assertEquals(403,verifier.verify("Bearer ordinary"));
            assertEquals(401,verifier.verify("Bearer revoked"));
            assertEquals(503,verifier.verify("Bearer malformed"));
            assertEquals(401,verifier.verify(null));
            assertEquals(503,new AdminBearerVerifier("").verify("Bearer root"));
        } finally {server.stop(0);}
    }
}
