package com.ledger.gateway.filter;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

@Component
public class MdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // Add a unique traceId to the MDC for each request
            MDC.put("traceId", UUID.randomUUID().toString());
            filterChain.doFilter(request, response);
        } finally {
            // Ensure the MDC is cleared after the request is processed
            MDC.clear();
        }
    }
}