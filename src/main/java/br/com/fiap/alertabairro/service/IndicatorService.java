package br.com.fiap.alertabairro.service;

import br.com.fiap.alertabairro.repository.IndicatorRepository;
import br.com.fiap.alertabairro.web.dto.IndicatorsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IndicatorService {

  private final IndicatorRepository repository;

  public IndicatorService(IndicatorRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public IndicatorsResponse read() {
    return repository.read();
  }
}
