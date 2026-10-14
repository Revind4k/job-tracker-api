package dev.erenyasar.jobtracker.application;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "applications")
public class JobApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "applied_on", nullable = false)
    private LocalDate appliedOn;

    private String url;
    private String notes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JobApplication() {}

    public JobApplication(Long userId, String company, String role, Status status, LocalDate appliedOn, String url, String notes) {
        this.userId = userId;
        this.company = company;
        this.role = role;
        this.status = status;
        this.appliedOn = appliedOn;
        this.url = url;
        this.notes = notes;
    }

    @PrePersist
    @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getCompany() { return company; }
    public String getRole() { return role; }
    public Status getStatus() { return status; }
    public LocalDate getAppliedOn() { return appliedOn; }
    public String getUrl() { return url; }
    public String getNotes() { return notes; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setCompany(String company) { this.company = company; }
    public void setRole(String role) { this.role = role; }
    public void setStatus(Status status) { this.status = status; }
    public void setAppliedOn(LocalDate appliedOn) { this.appliedOn = appliedOn; }
    public void setUrl(String url) { this.url = url; }
    public void setNotes(String notes) { this.notes = notes; }
}
