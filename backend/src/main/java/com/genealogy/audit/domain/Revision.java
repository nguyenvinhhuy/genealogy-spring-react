package com.genealogy.audit.domain;

import com.genealogy.common.model.AuditAction;
import com.genealogy.common.model.AuditEntityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One recorded change to a genealogical record (CLAUDE.md §3.8). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "revisions")
public class Revision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 40)
    private AuditEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AuditAction action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_data")
    private String beforeData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_data")
    private String afterData;

    @Column(name = "changed_by")
    private Long changedBy;

    // "On what basis": the gia phả chữ Hán, a grandmother's account, a birth certificate.
    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    /** Stamps the change timestamp before insert. */
    @PrePersist
    void onCreate() {
        this.changedAt = Instant.now();
    }
}
