package br.com.fiap.alertabairro.service;

import br.com.fiap.alertabairro.repository.UserRepository;
import br.com.fiap.alertabairro.repository.UserRepository.UserWithHash;
import br.com.fiap.alertabairro.security.TokenService;
import br.com.fiap.alertabairro.web.BusinessErrors.ConflictException;
import br.com.fiap.alertabairro.web.BusinessErrors.InvalidCredentialsException;
import br.com.fiap.alertabairro.web.dto.LoginRequest;
import br.com.fiap.alertabairro.web.dto.SessionResponse;
import br.com.fiap.alertabairro.web.dto.SignUpRequest;
import br.com.fiap.alertabairro.web.dto.UserResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private final UserRepository repository;
  private final PasswordEncoder encoder;
  private final TokenService tokens;

  /**
   * Throwaway hash used when the e-mail does not exist. Without it, login would answer
   * immediately for an unknown e-mail and after a bcrypt round for a wrong password, and
   * the timing difference would tell a stranger who has an account.
   */
  private final String throwawayHash;

  public UserService(UserRepository repository, PasswordEncoder encoder, TokenService tokens) {
    this.repository = repository;
    this.encoder = encoder;
    this.tokens = tokens;
    this.throwawayHash = encoder.encode("password-belonging-to-nobody");
  }

  @Transactional
  public SessionResponse signUp(SignUpRequest request) {
    if (repository.findByEmail(request.email()).isPresent()) {
      throw new ConflictException("e-mail ja cadastrado");
    }

    UserResponse user;
    try {
      user = repository.create(
          request.name(), request.email(), request.neighborhood(), request.role(),
          encoder.encode(request.password()));
    } catch (DuplicateKeyException race) {
      // Two simultaneous sign-ups with the same e-mail both pass the check above, so the
      // database's uniqueness is what decides.
      throw new ConflictException("e-mail ja cadastrado");
    }

    return new SessionResponse(tokens.generate(user.id(), user.role()), user);
  }

  @Transactional(readOnly = true)
  public SessionResponse authenticate(LoginRequest request) {
    UserWithHash found = repository.findByEmail(request.email()).orElse(null);

    String hash = found == null ? throwawayHash : found.passwordHash();
    boolean passwordMatches = encoder.matches(request.password(), hash);

    if (found == null || !passwordMatches) {
      throw new InvalidCredentialsException();
    }

    UserResponse user = found.withoutHash();
    return new SessionResponse(tokens.generate(user.id(), user.role()), user);
  }
}
