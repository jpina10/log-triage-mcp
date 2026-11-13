package dev.logtriage.demo.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads {@code X-Transaction-Id} from the inbound request (or creates one) and
 * puts it in the MDC, so every log line of the request carries it. The
 * outbound side is {@link TransactionIdPropagation}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TransactionIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Transaction-Id";
    public static final String MDC_KEY = "transactionId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String txId = request.getHeader(HEADER);
        if (txId == null || txId.isBlank()) {
            txId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, txId);
        response.setHeader(HEADER, txId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
