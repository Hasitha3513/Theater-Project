package com.cinenest.identity.internal;

/** Technical DENY by default;configuration flags never establish owner approval. */
final class FacebookLiveExecutionGate extends org.springframework.web.filter.OncePerRequestFilter {
    private final boolean enabled;
    FacebookLiveExecutionGate(boolean enabled){this.enabled=enabled;}
    @Override protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response,jakarta.servlet.FilterChain chain)throws java.io.IOException,jakarta.servlet.ServletException{
        String path=request.getServletPath();
        if(!enabled && (path.equals("/oauth2/authorization/facebook") || path.equals("/login/oauth2/code/facebook"))){response.setHeader("Cache-Control","no-store");response.setStatus(503);return;}
        chain.doFilter(request,response);
    }
}
