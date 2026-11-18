package br.com.fiap.alertabairro.service;

import br.com.fiap.alertabairro.repository.CheckinRepository;
import br.com.fiap.alertabairro.repository.HelpRepository;
import br.com.fiap.alertabairro.repository.HelpRepository.HelpWithLocation;
import br.com.fiap.alertabairro.web.BusinessErrors.NotFoundException;
import br.com.fiap.alertabairro.web.dto.HelpRequestResponse;
import br.com.fiap.alertabairro.web.dto.NewHelpRequest;
import br.com.fiap.alertabairro.web.dto.Pagination;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HelpService {

  /** Shown when neither the query nor a check-in gives us anywhere to measure from. */
  static final String UNKNOWN_DISTANCE = "distância desconhecida";

  private final HelpRepository repository;
  private final CheckinRepository checkins;

  public HelpService(HelpRepository repository, CheckinRepository checkins) {
    this.repository = repository;
    this.checkins = checkins;
  }

  @Transactional(readOnly = true)
  public List<HelpRequestResponse> list(
      Pagination page, long currentUserId, Double latitude, Double longitude) {
    Coordinate viewer = resolveViewer(currentUserId, latitude, longitude);
    return repository.list(page).stream()
        .map(row -> toResponse(row, viewer, currentUserId))
        .toList();
  }

  @Transactional
  public HelpRequestResponse create(long userId, NewHelpRequest request) {
    // Stored already coarse: the exact coordinate never reaches the database, so it
    // cannot be recovered later even by someone reading the table directly.
    Coordinate position =
        CoarseLocation.snap(resolveViewer(userId, request.latitude(), request.longitude()));
    Double lat = position == null ? null : position.latitude();
    Double lng = position == null ? null : position.longitude();

    long id = repository.create(userId, request, lat, lng);
    HelpWithLocation created = repository.find(id)
        .orElseThrow(() -> new NotFoundException("pedido nao encontrado"));
    return toResponse(created, position, userId);
  }

  /** The coordinate from the query, else the user's last check-in, else nothing. */
  private Coordinate resolveViewer(long userId, Double latitude, Double longitude) {
    Coordinate given = Coordinate.ofNullable(latitude, longitude);
    return given != null ? given : checkins.lastPosition(userId).orElse(null);
  }

  /**
   * The only place that sees the requester's coordinate, and where it stops. The app gets
   * a distance in text, never a position.
   */
  static HelpRequestResponse toResponse(
      HelpWithLocation row, Coordinate viewer, long currentUserId) {

    String distance = UNKNOWN_DISTANCE;
    if (viewer != null && row.latitude() != null && row.longitude() != null) {
      // Both endpoints on the same grid: moving the probe inside a cell cannot change
      // the answer, so repeated probes add no information to trilaterate with.
      Coordinate from = CoarseLocation.snap(viewer);
      Coordinate to = CoarseLocation.snap(new Coordinate(row.latitude(), row.longitude()));
      distance = Distance.format(
          Distance.km(from.latitude(), from.longitude(), to.latitude(), to.longitude()));
    }

    return new HelpRequestResponse(
        row.id(), row.mode(), row.category(), row.description(), row.urgency(),
        row.status(), distance, row.userId() == currentUserId);
  }

  /** Only the author closes their own request: it goes to the app's history. */
  @Transactional
  public HelpRequestResponse resolve(long id, long currentUserId) {
    HelpWithLocation found = loadOrFail(id);
    requireAuthor(found, currentUserId);
    repository.resolve(id);
    return toResponse(loadOrFail(id), resolveViewer(currentUserId, null, null), currentUserId);
  }

  @Transactional
  public void delete(long id, long currentUserId) {
    requireAuthor(loadOrFail(id), currentUserId);
    repository.delete(id);
  }

  @Transactional
  @PreAuthorize("hasRole('VOLUNTARIO')")
  public int closeOlderThan(int days) {
    return repository.closeOlderThan(days);
  }

  private HelpWithLocation loadOrFail(long id) {
    return repository.find(id)
        .orElseThrow(() -> new NotFoundException("pedido nao encontrado"));
  }

  private static void requireAuthor(HelpWithLocation row, long currentUserId) {
    if (row.userId() != currentUserId) {
      throw new AccessDeniedException("so quem criou pode alterar este item");
    }
  }
}
