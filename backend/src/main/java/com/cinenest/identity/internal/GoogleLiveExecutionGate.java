package com.cinenest.identity.internal;

/** Technical default DENY. Turning this on requires separate S2-B owner authority. */
final class GoogleLiveExecutionGate extends org.springframework.web.filter.OncePerRequestFilter {
    private final boolean enabled;
    GoogleLiveExecutionGate(boolean enabled){this.enabled=enabled;}
    @Override protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response,jakarta.servlet.FilterChain chain) throws java.io.IOException,jakarta.servlet.ServletException {
        String path=request.getServletPath();
        if(!enabled && (path.equals("/oauth2/authorization/google") || path.equals("/login/oauth2/code/google"))) {
            response.setHeader("Cache-Control","no-store");response.setStatus(503);return;
        }
        chain.doFilter(request,response);
    }
}
