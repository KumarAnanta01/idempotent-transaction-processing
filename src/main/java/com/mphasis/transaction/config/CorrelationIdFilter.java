
package com.mphasis.transaction.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID = "correlationId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String correlationId = request.getHeader("X-Correlation-ID");

        // Generate an ID when the client does not provide one
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        try {
            // Add correlation ID to logging context
            MDC.put(CORRELATION_ID, correlationId);

            // Return the same ID in the response
            response.setHeader("X-Correlation-ID", correlationId);

            filterChain.doFilter(request, response);

        } finally {
            // Always clear MDC to prevent thread reuse leakage
            MDC.remove(CORRELATION_ID);
        }
    }
}
