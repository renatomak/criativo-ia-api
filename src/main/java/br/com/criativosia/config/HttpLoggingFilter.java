package br.com.criativosia.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Logs metadata without buffering uploads, downloads or response bodies. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpLoggingFilter extends OncePerRequestFilter {
  private static final Logger LOG = LoggerFactory.getLogger(HttpLoggingFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String previous = MDC.get("requestId");
    String id = UUID.randomUUID().toString();
    String method = safe(request.getMethod());
    String path = safe(request.getRequestURI());
    long start = System.nanoTime();
    boolean failed = false;
    MDC.put("requestId", id);
    response.setHeader("X-Request-ID", id);
    LOG.info(
        "HTTP ENTRADA | id={} | metodo={} | rota={} | bytesEntrada={}",
        id,
        method,
        path,
        request.getContentLengthLong());
    try {
      chain.doFilter(request, response);
    } catch (IOException | ServletException | RuntimeException ex) {
      failed = true;
      throw ex;
    } finally {
      int status = failed ? 500 : response.getStatus();
      long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
      String pattern = "HTTP SAIDA | id={} | metodo={} | rota={} | status={} | duracaoMs={}";
      if (status >= 500) LOG.error(pattern, id, method, path, status, duration);
      else if (status >= 400) LOG.warn(pattern, id, method, path, status, duration);
      else LOG.info(pattern, id, method, path, status, duration);
      if (previous == null) MDC.remove("requestId");
      else MDC.put("requestId", previous);
    }
  }

  private static String safe(String value) {
    if (value == null) return "-";
    return value.substring(0, Math.min(value.length(), 512)).replaceAll("[\\p{Cntrl}]", "_");
  }
}
