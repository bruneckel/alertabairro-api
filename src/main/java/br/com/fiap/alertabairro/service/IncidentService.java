package br.com.fiap.alertabairro.service;

import br.com.fiap.alertabairro.repository.IncidentRepository;
import br.com.fiap.alertabairro.repository.IncidentRepository.IncidentWithAuthor;
import br.com.fiap.alertabairro.web.BusinessErrors.NotFoundException;
import br.com.fiap.alertabairro.web.dto.IncidentResponse;
import br.com.fiap.alertabairro.web.dto.NewIncidentRequest;
import br.com.fiap.alertabairro.web.dto.Pagination;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentService {

  private final IncidentRepository repository;

  public IncidentService(IncidentRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public List<IncidentResponse> list(Pagination page, long currentUserId) {
    return repository.list(page).stream().map(row -> toResponse(row, currentUserId)).toList();
  }

  @Transactional
  public IncidentResponse register(long userId, NewIncidentRequest request) {
    long id = repository.register(userId, request);
    return toResponse(load(id), userId);
  }

  IncidentWithAuthor load(long id) {
    return repository.find(id)
        .orElseThrow(() -> new NotFoundException("ocorrencia nao encontrada"));
  }

  /** The author's id stops here: it is never copied into the response. */
  static IncidentResponse toResponse(IncidentWithAuthor row, long currentUserId) {
    return new IncidentResponse(
        row.id(), row.type(), row.description(), row.latitude(), row.longitude(),
        row.status(), row.priority(), row.reports(), row.neighborhood(), row.photoUri(),
        row.createdAt(), row.userId() == currentUserId);
  }
}
