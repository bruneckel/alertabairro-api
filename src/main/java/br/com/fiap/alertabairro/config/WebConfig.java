package br.com.fiap.alertabairro.config;

import br.com.fiap.alertabairro.security.AuthenticatedUser;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets a controller declare {@code AuthenticatedUser} as a parameter, so the web layer
 * never has to know the token's shape.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new HandlerMethodArgumentResolver() {

      @Override
      public boolean supportsParameter(MethodParameter parameter) {
        return AuthenticatedUser.class.equals(parameter.getParameterType());
      }

      @Override
      public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
          NativeWebRequest request, WebDataBinderFactory binder) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token
            && token.getToken() instanceof Jwt jwt) {
          return AuthenticatedUser.from(jwt);
        }
        throw new IllegalStateException("public route declared an AuthenticatedUser parameter");
      }
    });
  }
}
