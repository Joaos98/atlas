package com.joaosousa.atlas.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * SPA fallback for Vue Router history mode: any GET that is not an API call
 * and has no file extension (i.e. a client-side route, not an asset) is
 * forwarded to index.html so deep links like /workouts render the app.
 */
@Component
public class SpaForwardFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if ("GET".equals(request.getMethod())
                && !path.startsWith("/api")
                && !path.contains(".")
                && !"/".equals(path)) {
            request.getRequestDispatcher("/index.html").forward(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
