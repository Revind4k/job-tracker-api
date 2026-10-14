package dev.erenyasar.jobtracker.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ApplicationRepository extends JpaRepository<JobApplication, Long> {
    Page<JobApplication> findByUserId(Long userId, Pageable page);
    Page<JobApplication> findByUserIdAndStatus(Long userId, Status status, Pageable page);
    Optional<JobApplication> findByIdAndUserId(Long id, Long userId);

    interface StatusCount { Status getStatus(); long getCount(); }
    interface WeekCount { LocalDate getWeek(); long getCount(); }

    @Query("select a.status as status, count(a) as count from JobApplication a where a.userId = ?1 group by a.status")
    List<StatusCount> countByStatus(Long userId);

    @Query(value = """
            select date_trunc('week', applied_on)::date as week, count(*) as count
            from applications where user_id = ?1 and applied_on >= ?2
            group by 1 order by 1""", nativeQuery = true)
    List<WeekCount> countByWeek(Long userId, LocalDate since);
}
