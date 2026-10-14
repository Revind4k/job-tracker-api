package dev.erenyasar.jobtracker.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/applications")
public class ApplicationController {
    public record CreateRequest(
            @NotBlank @Size(max = 200) String company,
            @NotBlank @Size(max = 200) String role,
            Status status,
            @NotNull @PastOrPresent LocalDate appliedOn,
            @Size(max = 2000) String url,
            String notes) {}

    /** PATCH body: every field optional; only the ones sent change. */
    public record UpdateRequest(
            @Size(min = 1, max = 200) String company,
            @Size(min = 1, max = 200) String role,
            Status status,
            @PastOrPresent LocalDate appliedOn,
            @Size(max = 2000) String url,
            String notes) {}

    public record View(Long id, String company, String role, Status status, LocalDate appliedOn, String url,
                       String notes, Instant updatedAt) {
        static View of(JobApplication a) {
            return new View(a.getId(), a.getCompany(), a.getRole(), a.getStatus(), a.getAppliedOn(), a.getUrl(),
                    a.getNotes(), a.getUpdatedAt());
        }
    }

    public record PageView(java.util.List<View> items, int page, int size, long total) {}

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    private static Long uid(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    @GetMapping
    public PageView list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Status status,
                         @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        PageRequest req = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100), Sort.by(Sort.Direction.DESC, "appliedOn", "id"));
        Page<JobApplication> p = service.list(uid(jwt), status, req);
        return new PageView(p.map(View::of).getContent(), p.getNumber(), p.getSize(), p.getTotalElements());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public View create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRequest r) {
        Long userId = uid(jwt);
        return View.of(service.create(userId, new JobApplication(userId, r.company(), r.role(),
                r.status() == null ? Status.APPLIED : r.status(), r.appliedOn(), r.url(), r.notes())));
    }

    @GetMapping("/{id}")
    public View get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return View.of(service.get(uid(jwt), id));
    }

    @PatchMapping("/{id}")
    public View update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody UpdateRequest r) {
        return View.of(service.update(uid(jwt), id, a -> {
            if (r.company() != null) a.setCompany(r.company());
            if (r.role() != null) a.setRole(r.role());
            if (r.status() != null) a.setStatus(r.status());
            if (r.appliedOn() != null) a.setAppliedOn(r.appliedOn());
            if (r.url() != null) a.setUrl(r.url());
            if (r.notes() != null) a.setNotes(r.notes());
        }));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.delete(uid(jwt), id);
    }

    @GetMapping("/stats")
    public ApplicationService.Stats stats(@AuthenticationPrincipal Jwt jwt) {
        return service.stats(uid(jwt));
    }
}
