package br.com.fiap.alertabairro.config;

import br.com.fiap.alertabairro.seguranca.Autenticado;
import java.util.List;
import org.springframework.core.MethodParameter;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Permite que um controller declare {@code Autenticado autenticado} como parametro.
 *
 * <p>Sem isto, cada handler repetiria a extracao do {@code sub} e da claim {@code perfil}
 * do token -- e a camada web passaria a conhecer o formato do JWT.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new HandlerMethodArgumentResolver() {

      @Override
      public boolean supportsParameter(MethodParameter parametro) {
        return Autenticado.class.equals(parametro.getParameterType());
      }

      @Override
      public Object resolveArgument(MethodParameter parametro, ModelAndViewContainer mav,
          NativeWebRequest requisicao, WebDataBinderFactory binder) {
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao instanceof JwtAuthenticationToken token
            && token.getToken() instanceof Jwt jwt) {
          return Autenticado.de(jwt);
        }
        // Rota protegida sempre passa pelo filtro antes do controller, entao
        // chegar aqui sem token significa rota publica pedindo Autenticado.
        throw new IllegalStateException(
            "rota sem autenticacao declarou o parametro Autenticado");
      }
    });
  }
}
