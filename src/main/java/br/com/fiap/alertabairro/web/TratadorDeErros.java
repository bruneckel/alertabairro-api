package br.com.fiap.alertabairro.web;

import br.com.fiap.alertabairro.web.ErroDeNegocio.ConflitoException;
import br.com.fiap.alertabairro.web.ErroDeNegocio.CredenciaisInvalidasException;
import br.com.fiap.alertabairro.web.ErroDeNegocio.NaoEncontradoException;
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

/**
 * Traducao unica de excecao para resposta HTTP. O corpo e sempre
 * <code>{"erro": "mensagem"}</code>, que e o formato que o app ja le.
 *
 * <p>O ponto delicado e o erro vindo do Oracle. As rotinas PL/SQL falam com o usuario
 * final por {@code RAISE_APPLICATION_ERROR} entre -20000 e -20999, e isso precisa chegar
 * como <strong>422</strong> com a mensagem repassada. Os demais erros do banco tem politica
 * explicita, porque deixa-los cair no 500 generico esconderia entrada invalida como se
 * fosse defeito do servidor.
 */
@RestControllerAdvice
public class TratadorDeErros {

  private static final Logger log = LoggerFactory.getLogger(TratadorDeErros.class);

  /** Faixa que as rotinas PL/SQL usam para falar com o usuario final. */
  private static final int ORA_NEGOCIO_MIN = 20000;
  private static final int ORA_NEGOCIO_MAX = 20999;
  private static final int ORA_CHAVE_DUPLICADA = 1;

  /** Erros do Oracle que denunciam entrada invalida, nao defeito do servidor. */
  private static final Map<Integer, String> ORA_ENTRADA_INVALIDA = Map.of(
      12899, "valor maior do que a coluna aceita",
      1400,  "campo obrigatorio nao informado",
      1407,  "campo obrigatorio nao informado",
      2290,  "valor fora do dominio aceito",
      1438,  "numero maior do que a coluna aceita",
      1722,  "numero invalido");

  private static ResponseEntity<Map<String, String>> resposta(HttpStatus status, String erro) {
    return ResponseEntity.status(status).body(Map.of("erro", erro));
  }

  // ------------------------------------------------------------------ entrada

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Map<String, String>> validacao(MethodArgumentNotValidException e) {
    String mensagem = e.getBindingResult().getFieldErrors().stream()
        .findFirst()
        .map(erro -> erro.getDefaultMessage())
        .orElse("dados invalidos");
    return resposta(HttpStatus.BAD_REQUEST, mensagem);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  ResponseEntity<Map<String, String>> validacaoDeParametro(HandlerMethodValidationException e) {
    return resposta(HttpStatus.BAD_REQUEST, "parametro invalido");
  }

  /** JSON malformado, tipo errado, enum fora do dominio: e entrada, nao falha interna. */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<Map<String, String>> corpoIlegivel(HttpMessageNotReadableException e) {
    return resposta(HttpStatus.BAD_REQUEST, "corpo da requisicao invalido");
  }

  @ExceptionHandler({MethodArgumentTypeMismatchException.class,
                     MissingServletRequestParameterException.class,
                     IllegalArgumentException.class})
  ResponseEntity<Map<String, String>> parametroInvalido(Exception e) {
    return resposta(HttpStatus.BAD_REQUEST, "parametro invalido");
  }

  // ------------------------------------------------------------------ negocio

  @ExceptionHandler(CredenciaisInvalidasException.class)
  ResponseEntity<Map<String, String>> credenciais(CredenciaisInvalidasException e) {
    return resposta(HttpStatus.UNAUTHORIZED, e.getMessage());
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Map<String, String>> acessoNegado(AccessDeniedException e) {
    return resposta(HttpStatus.FORBIDDEN, "acao nao permitida para o seu perfil");
  }

  @ExceptionHandler(NaoEncontradoException.class)
  ResponseEntity<Map<String, String>> naoEncontrado(NaoEncontradoException e) {
    return resposta(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(ConflitoException.class)
  ResponseEntity<Map<String, String>> conflito(ConflitoException e) {
    return resposta(HttpStatus.CONFLICT, e.getMessage());
  }

  @ExceptionHandler(DuplicateKeyException.class)
  ResponseEntity<Map<String, String>> duplicado(DuplicateKeyException e) {
    return resposta(HttpStatus.CONFLICT, "registro duplicado");
  }

  // -------------------------------------------------------------------- banco

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> banco(DataAccessException e) {
    SQLException sql = procurarSqlException(e);
    if (sql != null) {
      int codigo = Math.abs(sql.getErrorCode());

      // Regra de negocio recusada por uma rotina PL/SQL: a mensagem foi escrita
      // para o usuario final, entao ela segue para o app.
      if (codigo >= ORA_NEGOCIO_MIN && codigo <= ORA_NEGOCIO_MAX) {
        return resposta(HttpStatus.UNPROCESSABLE_ENTITY, mensagemLimpa(sql.getMessage()));
      }
      if (codigo == ORA_CHAVE_DUPLICADA) {
        return resposta(HttpStatus.CONFLICT, "registro duplicado");
      }
      String entrada = ORA_ENTRADA_INVALIDA.get(codigo);
      if (entrada != null) {
        return resposta(HttpStatus.BAD_REQUEST, entrada);
      }
    }
    log.error("falha de acesso a dados", e);
    return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "erro interno no servidor");
  }

  // ----------------------------------------------------------------- fallback

  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, String>> inesperado(Exception e) {
    log.error("erro nao tratado", e);
    return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "erro interno no servidor");
  }

  // ------------------------------------------------------------------ apoio

  private static SQLException procurarSqlException(Throwable e) {
    for (Throwable atual = e; atual != null; atual = atual.getCause()) {
      if (atual instanceof SQLException sql) {
        return sql;
      }
    }
    return null;
  }

  /** "ORA-20002: Abrigo sem vagas suficientes\nORA-06512: ..." -> "Abrigo sem vagas suficientes". */
  static String mensagemLimpa(String mensagemOracle) {
    if (mensagemOracle == null) {
      return "operacao recusada";
    }
    String primeiraLinha = mensagemOracle.split("\\R", 2)[0];
    return primeiraLinha.replaceFirst("^ORA-\\d+:\\s*", "").trim();
  }
}
