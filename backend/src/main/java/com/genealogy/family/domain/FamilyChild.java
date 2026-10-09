package com.genealogy.family.domain;

import com.genealogy.common.model.RelationType;
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
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Links a child into a family, recording the relation to each partner separately (CLAUDE.md 3.1). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "family_children")
public class FamilyChild {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_to_p1", nullable = false, length = 20)
    private RelationType relationToP1 = RelationType.BIRTH;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_to_p2", nullable = false, length = 20)
    private RelationType relationToP2 = RelationType.BIRTH;

    // Con trưởng = 1, con thứ = 2, and null when the record does not say (§5.3).
    @Column(name = "birth_order")
    private Integer birthOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
