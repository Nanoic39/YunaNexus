package cc.nanoic.yunanexus.auth.security;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.net.InetAddress;

/** Forwarded headers are only accepted from explicitly trusted network peers. */
public final class ClientIpResolver {
    private ClientIpResolver() {}

    public static String resolve(HttpServletRequest request, String trustedCidrs) {
        String remote = request.getRemoteAddr();
        boolean trusted = false;
        for (String cidr : trustedCidrs.split(",")) {
            if (!cidr.isBlank() && new IpAddressMatcher(cidr.trim()).matches(remote)) {
                trusted = true;
                break;
            }
        }
        if (!trusted) return remote;
        String value = request.getHeader("X-Real-IP");
        if (value == null
                || value.length() > 45
                || !value.matches("[0-9a-fA-F:.]+")
                || (!value.contains(".") && !value.contains(":"))) return remote;
        try {
            return InetAddress.getByName(value).getHostAddress();
        } catch (Exception e) {
            return remote;
        }
    }
}
