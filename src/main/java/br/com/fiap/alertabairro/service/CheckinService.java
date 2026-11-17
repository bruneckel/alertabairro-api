package br.com.fiap.alertabairro.service;

import br.com.fiap.alertabairro.repository.CheckinRepository;
import br.com.fiap.alertabairro.web.dto.CheckinRequest;
import br.com.fiap.alertabairro.web.dto.CheckinResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckinService {

  private final CheckinRepository repository;

  public CheckinService(CheckinRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public CheckinResponse register(long userId, CheckinRequest request) {
    long id = repository.register(
        userId, request.status(), request.latitude(), request.longitude());
    return new CheckinResponse(id, request.status());
  }
}
