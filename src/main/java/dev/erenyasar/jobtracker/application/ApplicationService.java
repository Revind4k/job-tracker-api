package dev.erenyasar.jobtracker.application;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ApplicationService {
    public record WeekPoint(LocalDate week, long count) implements Serializable {}

    /** Cached in Redis per user; every write for that user evicts it. */
    public record Stats(long total, Map<Status, Long> byStatus, double responseRate, List<WeekPoint> last12Weeks)
            implements Serializable {}

    private final ApplicationRepository repo;

    public ApplicationService(ApplicationRepository repo) {
        this.repo = repo;
    }

    public Page<JobApplication> list(Long userId, Status status, Pageable page) {
        return status == null ? repo.findByUserId(userId, page) : repo.findByUserIdAndStatus(userId, status, page);
    }

    public JobApplication get(Long userId, Long id) {
        // Scoped by user: another user's id looks exactly like a missing one.
        return repo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "application not found"));
    }

    @Transactional
    @CacheEvict(cacheNames = "stats", key = "#userId")
    public JobApplication create(Long userId, JobApplication a) {
        return repo.save(a);
    }

    @Transactional
    @CacheEvict(cacheNames = "stats", key = "#userId")
    public JobApplication update(Long userId, Long id, java.util.function.Consumer<JobApplication> changes) {
        JobApplication a = get(userId, id);
        changes.accept(a);
        return repo.save(a);
    }

    @Transactional
    @CacheEvict(cacheNames = "stats", key = "#userId")
    public void delete(Long userId, Long id) {
        repo.delete(get(userId, id));
    }

    @Cacheable(cacheNames = "stats", key = "#userId")
    public Stats stats(Long userId) {
        Map<Status, Long> by = new EnumMap<>(Status.class);
        for (Status s : Status.values()) by.put(s, 0L);
        repo.countByStatus(userId).forEach(c -> by.put(c.getStatus(), c.getCount()));
        long total = by.values().stream().mapToLong(Long::longValue).sum();
        // A "response" is anything past APPLIED other than the candidate withdrawing.
        long responded = total - by.get(Status.APPLIED) - by.get(Status.WITHDRAWN);
        double rate = total == 0 ? 0 : (double) responded / total;
        List<WeekPoint> weeks = repo.countByWeek(userId, LocalDate.now().minusWeeks(12)).stream()
                .map(w -> new WeekPoint(w.getWeek(), w.getCount()))
                .toList();
        return new Stats(total, by, Math.round(rate * 1000) / 1000.0, weeks);
    }
}
