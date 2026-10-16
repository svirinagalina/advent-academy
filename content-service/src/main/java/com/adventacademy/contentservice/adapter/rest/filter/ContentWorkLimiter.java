package com.adventacademy.contentservice.adapter.rest.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bound binary/render allocations even when many clients have valid tokens. */
@Component
@Order(0)
public class ContentWorkLimiter extends OncePerRequestFilter {
    private final Semaphore downloads;
    private final Semaphore authoring;
    public ContentWorkLimiter(@org.springframework.beans.factory.annotation.Value("${content.work.downloads:8}") int downloads,
                              @org.springframework.beans.factory.annotation.Value("${content.work.authoring:2}") int authoring) {
        if(downloads<1 || authoring<1) { throw new IllegalArgumentException("Content concurrency must be positive"); }
        this.downloads=new Semaphore(downloads);this.authoring=new Semaphore(authoring);
    }
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/assets/") && !request.getRequestURI().startsWith("/api/lessons/")
                && !request.getRequestURI().equals("/api/markdown/preview");
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        Semaphore slots=request.getRequestURI().startsWith("/api/assets/") ? downloads : authoring;
        if(!slots.tryAcquire()) {response.setStatus(429);response.setHeader("Retry-After","1");return;}
        try {chain.doFilter(request,response);} finally {slots.release();}
    }
}
