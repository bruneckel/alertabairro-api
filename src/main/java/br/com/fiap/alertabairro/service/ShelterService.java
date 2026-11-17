package br.com.fiap.alertabairro.service;

import br.com.fiap.alertabairro.repository.ShelterRepository;
import br.com.fiap.alertabairro.web.BusinessErrors.NotFoundException;
import br.com.fiap.alertabairro.web.dto.ShelterResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShelterService {

  private final ShelterRepository repository;

  public ShelterService(ShelterRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public List<ShelterResponse> list() {
    return repository.list();
  }

  /** Null when no shelter has a vacancy: the app shows "nenhum abrigo com vagas". */
  @Transactional(readOnly = true)
  public ShelterResponse nearest(double latitude, double longitude) {
    return repository.findNearest(latitude, longitude).orElse(null);
  }

  @Transactional
  @PreAuthorize("hasRole('VOLUNTARIO')")
  public ShelterResponse changeOccupancy(long shelterId, int amount) {
    repository.controlSpots(shelterId, amount);
    return repository.find(shelterId)
        .orElseThrow(() -> new NotFoundException("abrigo nao encontrado"));
  }
}
