package br.com.criativosia;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.criativosia.config.HttpLoggingFilter;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class HttpLoggingFilterTest {
  @Test
  void preservesBodiesCorrelatesAndOmitsSecrets() throws Exception {
    Logger logger = (Logger) LoggerFactory.getLogger(HttpLoggingFilter.class);
    ListAppender<ILoggingEvent> logs = new ListAppender<>();
    logs.start();
    logger.addAppender(logs);
    MDC.put("requestId", "previous");
    try {
      var request = new MockHttpServletRequest("POST", "/api/produtos");
      request.setQueryString("token=secret-query");
      request.addHeader("Authorization", "Bearer secret-header");
      byte[] body = "secret-body".getBytes(java.nio.charset.StandardCharsets.UTF_8);
      request.setContent(body);
      var response = new MockHttpServletResponse();
      new HttpLoggingFilter()
          .doFilter(
              request,
              response,
              (req, res) -> {
                assertEquals(response.getHeader("X-Request-ID"), MDC.get("requestId"));
                assertArrayEquals(body, req.getInputStream().readAllBytes());
                res.getOutputStream().write(body);
                response.setStatus(201);
              });
      assertArrayEquals(body, response.getContentAsByteArray());
      assertEquals("previous", MDC.get("requestId"));
      assertEquals(2, logs.list.size());
      String output =
          logs.list.stream()
              .map(ILoggingEvent::getFormattedMessage)
              .collect(java.util.stream.Collectors.joining("\n"));
      assertTrue(output.contains("ENTRADA"));
      assertTrue(output.contains("SAIDA"));
      assertTrue(output.contains("status=201"));
      assertFalse(output.contains("secret"));
      logs.list.clear();
      assertThrows(
          ServletException.class,
          () ->
              new HttpLoggingFilter()
                  .doFilter(
                      request,
                      response,
                      (req, res) -> {
                        throw new ServletException("secret-exception");
                      }));
      assertTrue(logs.list.getLast().getFormattedMessage().contains("status=500"));
      assertFalse(logs.list.getLast().getFormattedMessage().contains("secret"));
      assertEquals("previous", MDC.get("requestId"));
    } finally {
      MDC.remove("requestId");
      logger.detachAppender(logs);
      logs.stop();
    }
  }
}
