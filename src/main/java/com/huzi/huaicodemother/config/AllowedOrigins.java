package com.huzi.huaicodemother.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** CORS 与写接口来源检查共享白名单，不接受通配符或主机后缀匹配。 */
@Component
public class AllowedOrigins {

    public static final String LOCAL_DEFAULTS = "http://localhost:5173,http://127.0.0.1:5173,"
            + "http://localhost:4173,http://127.0.0.1:4173";

    private final Set<String> origins;

    public AllowedOrigins(@Value("${app.allowed-origins:" + LOCAL_DEFAULTS + "}") String configuredOrigins) {
        Set<String> allowed = new LinkedHashSet<>();
        Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .forEach(value -> {
                    String normalized = normalize(value, false);
                    if (normalized == null) {
                        throw new IllegalArgumentException("app.allowed-origins 必须是明确的 HTTP/HTTPS 来源");
                    }
                    allowed.add(normalized);
                });
        origins = Set.copyOf(allowed);
    }

    public String[] getOrigins() {
        return origins.toArray(String[]::new);
    }

    public boolean isAllowed(String header, boolean referer, HttpServletRequest request) {
        String normalized = normalize(header, referer);
        return normalized != null && (origins.contains(normalized) || normalized.equals(requestOrigin(request)));
    }

    /** Origin 必须只有来源，Referer 可以包含页面路径；两者都禁止用户凭据。 */
    private static String normalize(String value, boolean referer) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            return null;
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || host.isBlank() || uri.isOpaque()
                    || uri.getRawUserInfo() != null || uri.getPort() > 65535 || uri.getPort() == 0) {
                return null;
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return null;
            }
            if (!referer && ((uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || uri.getRawQuery() != null || uri.getRawFragment() != null)) {
                return null;
            }
            int defaultPort = "https".equals(scheme) ? 443 : 80;
            int port = uri.getPort();
            return scheme + "://" + host.toLowerCase(Locale.ROOT)
                    + (port == -1 || port == defaultPort ? "" : ":" + port);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private String requestOrigin(HttpServletRequest request) {
        try {
            // 只使用 servlet 请求的实际来源，不主动信任 X-Forwarded-*。
            URI uri = new URI(request.getScheme(), null, request.getServerName(), request.getServerPort(),
                    null, null, null);
            return normalize(uri.toString(), false);
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
