package br.com.fiap.alertabairro.web;

import br.com.fiap.alertabairro.web.BusinessErrors.ConflictException;
import br.com.fiap.alertabairro.web.BusinessErrors.InvalidCredentialsException;
import br.com.fiap.alertabairro.web.BusinessErrors.NotFoundException;
import java.sql.SQLException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Single translation from exception to HTTP. Body is always {@code {"erro": "..."}}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** Range the PL/SQL routines use to speak to the end user. */
  private static final int ORA_BUSINESS_MIN = 20000;
  private static final int ORA_BUSINESS_MAX = 20999;
  private static final int ORA_DUPLICATE_KEY = 1;

  /**
   * Oracle errors that mean bad input, not a server defect. Without this policy they would
   * all fall into the generic 500 and hide invalid input as if it were our fault.
   */
  private static final Map<Integer, String> ORA_BAD_INPUT = Map.of(
      12899, "valor maior do que a coluna aceita",
      1400, "campo obrigatorio nao informado",
      1407, "campo obrigatorio nao informado",
      2290, "valor fora do dominio aceito",
      1438, "numero maior do que a coluna aceita",
      1722, "numero invalido");

  private static ResponseEntity<Map<String, String>> body(HttpStatus status, String error) {
    return ResponseEntity.status(status).body(Map.of("erro", error));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Map<String, String>> invalidBody(MethodArgumentNotValidException e) {
    String message = e.getBindingResult().getFieldErrors().stream()
        .findFirst()
        .map(error -> error.getDefaultMessage())
        .orElse("dados invalidos");
    return body(HttpStatus.BAD_REQUEST, message);
  }

  @ExceptionHandler({HandlerMethodValidationException.class,
                     MethodArgumentTypeMismatchException.class,
                     MissingServletRequestParameterException.class,
                     IllegalArgumentException.class})
  ResponseEntity<Map<String, String>> invalidParameter(Exception e) {
    String message = e instanceof IllegalArgumentException ? e.getMessage() : "parametro invalido";
    return body(HttpStatus.BAD_REQUEST, message);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<Map<String, String>> unreadableBody(HttpMessageNotReadableException e) {
    return body(HttpStatus.BAD_REQUEST, "corpo da requisicao invalido");
  }

  /** An unknown path is a 404, not the catch-all 500. */
  @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
  ResponseEntity<Map<String, String>> unknownRoute(Exception e) {
    return body(HttpStatus.NOT_FOUND, "rota nao encontrada");
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  ResponseEntity<Map<String, String>> credentials(InvalidCredentialsException e) {
    return body(HttpStatus.UNAUTHORIZED, e.getMessage());
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Map<String, String>> accessDenied(AccessDeniedException e) {
    return body(HttpStatus.FORBIDDEN, "acao nao permitida para o seu perfil");
  }

  @ExceptionHandler(NotFoundException.class)
  ResponseEntity<Map<String, String>> notFound(NotFoundException e) {
    return body(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(ConflictException.class)
  ResponseEntity<Map<String, String>> conflict(ConflictException e) {
    return body(HttpStatus.CONFLICT, e.getMessage());
  }

  @ExceptionHandler(DuplicateKeyException.class)
  ResponseEntity<Map<String, String>> duplicate(DuplicateKeyException e) {
    return body(HttpStatus.CONFLICT, "registro duplicado");
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> database(DataAccessException e) {
    SQLException sql = findSqlException(e);
    if (sql != null) {
      int code = Math.abs(sql.getErrorCode());

      // Business rule refused by a PL/SQL routine: the message was written for the
      // end user, so it goes through to the app.
      if (code >= ORA_BUSINESS_MIN && code <= ORA_BUSINESS_MAX) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, cleanMessage(sql.getMessage()));
      }
      if (code == ORA_DUPLICATE_KEY) {
        return body(HttpStatus.CONFLICT, "registro duplicado");
      }
      String badInput = ORA_BAD_INPUT.get(code);
      if (badInput != null) {
        return body(HttpStatus.BAD_REQUEST, badInput);
      }
    }
    log.error("data access failure", e);
    return body(HttpStatus.INTERNAL_SERVER_ERROR, "erro interno no servidor");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, String>> unexpected(Exception e) {
    log.error("unhandled error", e);
    return body(HttpStatus.INTERNAL_SERVER_ERROR, "erro interno no servidor");
  }

  private static SQLException findSqlException(Throwable e) {
    for (Throwable current = e; current != null; current = current.getCause()) {
      if (current instanceof SQLException sql) {
        return sql;
      }
    }
    return null;
  }

  /** "ORA-20002: Abrigo sem vagas\nORA-06512: ..." becomes "Abrigo sem vagas". */
  static String cleanMessage(String oracleMessage) {
    if (oracleMessage == null) {
      return "operacao recusada";
    }
    return oracleMessage.split("\\R", 2)[0].replaceFirst("^ORA-\\d+:\\s*", "").trim();
  }
}
