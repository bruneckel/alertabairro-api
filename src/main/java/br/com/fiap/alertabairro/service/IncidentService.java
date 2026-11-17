package br.com.fiap.alertabairro.service;

import br.com.fiap.alertabairro.repository.IncidentRepository;
import br.com.fiap.alertabairro.repository.IncidentRepository.IncidentWithAuthor;
import br.com.fiap.alertabairro.security.AuthenticatedUser;
import br.com.fiap.alertabairro.web.BusinessErrors.ConflictException;
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

  /**
   * Records the confirmation and bumps the counter inside ONE transaction.
   *
   * <p>This is the defect inherited from the previous back end: there, the two steps were
   * separate commits, so a failure in the second left the user having spent their single
   * confirmation with the counter unchanged, and no way to undo it. The row is read with
   * FOR UPDATE so two readers cannot both pass the checks below.
   */
  @Transactional
  public IncidentResponse confirm(long id, AuthenticatedUser current) {
    IncidentWithAuthor incident = repository.findForUpdate(id)
        .orElseThrow(() -> new NotFoundException("ocorrencia nao encontrada"));

    if ("resolvida".equals(incident.status())) {
      throw new ConflictException("esta ocorrencia ja foi resolvida");
    }
    if (incident.userId() == current.id()) {
      throw new ConflictException("voce registrou esta ocorrencia, ela ja conta como um relato");
    }
    if (!repository.registerConfirmation(id, current.id())) {
      throw new ConflictException("voce ja confirmou esta ocorrencia");
    }

    repository.incrementReports(id);
    return toResponse(load(id), current.id());
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
