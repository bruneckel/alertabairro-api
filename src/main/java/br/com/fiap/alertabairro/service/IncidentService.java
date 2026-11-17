package br.com.fiap.alertabairro.service;

import br.com.fiap.alertabairro.repository.IncidentRepository;
import br.com.fiap.alertabairro.repository.IncidentRepository.IncidentWithAuthor;
import br.com.fiap.alertabairro.security.AuthenticatedUser;
import org.springframework.security.access.AccessDeniedException;
import br.com.fiap.alertabairro.web.BusinessErrors.ConflictException;
import br.com.fiap.alertabairro.web.BusinessErrors.NotFoundException;
import br.com.fiap.alertabairro.web.dto.IncidentResponse;
import br.com.fiap.alertabairro.web.dto.NewIncidentRequest;
import br.com.fiap.alertabairro.web.dto.Pagination;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
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

  /**
   * Status only moves forward: aberta -> em_atendimento -> resolvida.
   *
   * <p>Reads with FOR UPDATE before deciding, which fixes the inherited defect 5.3(e):
   * there, two volunteers could both pass the "is it still open?" check and both win.
   */
  @Transactional
  @PreAuthorize("hasRole('VOLUNTARIO')")
  public IncidentResponse attend(long id, AuthenticatedUser current) {
    IncidentWithAuthor incident = lockOrFail(id);
    if (!"aberta".equals(incident.status())) {
      throw new ConflictException("so ocorrencias abertas podem ser assumidas");
    }
    repository.changeStatus(id, "em_atendimento");
    return toResponse(load(id), current.id());
  }

  @Transactional
  @PreAuthorize("hasRole('VOLUNTARIO')")
  public IncidentResponse resolve(long id, AuthenticatedUser current) {
    IncidentWithAuthor incident = lockOrFail(id);
    if ("resolvida".equals(incident.status())) {
      throw new ConflictException("esta ocorrencia ja foi resolvida");
    }
    repository.changeStatus(id, "resolvida");
    return toResponse(load(id), current.id());
  }

  /** Only the author, for the case of a mistaken report. */
  @Transactional
  public void delete(long id, AuthenticatedUser current) {
    IncidentWithAuthor incident = load(id);
    if (incident.userId() != current.id()) {
      throw new AccessDeniedException("so quem criou pode excluir este item");
    }
    repository.delete(id);
  }

  private IncidentWithAuthor lockOrFail(long id) {
    return repository.findForUpdate(id)
        .orElseThrow(() -> new NotFoundException("ocorrencia nao encontrada"));
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
