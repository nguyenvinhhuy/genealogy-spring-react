package com.genealogy.person.domain;

import com.genealogy.common.model.Gender;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.OptimisticLock;

/** One individual in the gia phả; parentage lives in the family feature, never as a parent id here (§3.1). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "persons")
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Gender gender = Gender.UNKNOWN;

    // Derived (CLAUDE.md §3.4): recomputed by the family feature, never set by hand.
    // Excluded from the version, or every recompute makes an open form stale (§8.12 #2).
    @OptimisticLock(excluded = true)
    private Integer generation;

    @Column(name = "branch_id")
    private Long branchId;

    // Derived (CLAUDE.md §3.4): drives living-person redaction, and is excluded from the version like generation.
    @OptimisticLock(excluded = true)
    @Column(nullable = false)
    private boolean living = true;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Set by the services that edit a person, never by a callback: a derived recompute is not an edit (§8.12 #2).
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Optimistic lock: an edit made from a stale form is refused instead of silently overwriting a newer one.
    @Version
    @Column(nullable = false)
    private long version;

    // Unidirectional: PersonName carries no back-reference, so it stays a plain row keyed by person_id.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false)
    // Batched, or every caller rendering a display name pays a query per person: /quality was 2,001 of them.
    @BatchSize(size = 100)
    private List<PersonName> names = new ArrayList<>();

    /** Stamps both timestamps before the first insert. */
    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Returns the name marked primary.
     *
     * @return the primary name, or empty only for a person with no names at all
     */
    public Optional<PersonName> primaryName() {
        return names.stream().filter(PersonName::isPrimary).findFirst();
    }
}
