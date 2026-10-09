package com.genealogy.family.domain;

import com.genealogy.common.model.FamilyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One union between two people. */
// Partners are numbered, not husband/wife, because gender is often absent (§3.1).
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "families")
public class Family {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "partner1_id")
    private Long partner1Id;

    @Column(name = "partner2_id")
    private Long partner2Id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FamilyStatus status = FamilyStatus.MARRIED;

    // Orders one person's unions: vợ cả = 0, vợ thứ = 1, and so on.
    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Optimistic lock: an edit made from a stale form is refused instead of silently overwriting a newer one.
    @Version
    @Column(nullable = false)
    private long version;

    /** Stamps both timestamps before the first insert. */
    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Refreshes the update timestamp before every update. */
    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
