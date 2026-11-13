package dev.logtriage.demo.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * Logs every inbound request with its body, the way many dev environments do.
 * Bodies carry (fake) personal data on purpose: the log-triage tool has to
 * redact it. Admin and actuator calls are not logged, so a fault switch never
 * shows up in the logs the agent reads.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("inbound");
    private static final int BODY_LIMIT = 4096;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/admin") || uri.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var wrapped = new ContentCachingRequestWrapper(request, BODY_LIMIT);
        long start = System.nanoTime();
        try {
            chain.doFilter(wrapped, response);
        } finally {
            String body = new String(wrapped.getContentAsByteArray(), StandardCharsets.UTF_8);
            log.info("HTTP {} {} -> {} in {}ms body={}", request.getMethod(), request.getRequestURI(),
                    response.getStatus(), (System.nanoTime() - start) / 1_000_000,
                    body.isEmpty() ? "-" : body);
        }
    }
}
