package com.genealogy.suggestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One proposed change offered by a member who cannot edit the gia phả directly (docs/analysis.md F17). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "suggestions")
public class Suggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private SuggestionTargetType targetType;

    // Null for a suggested new person, who has no record yet to point at.
    @Column(name = "target_id")
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SuggestionKind kind;

    // The proposed PersonRequest as JSON; null when the suggestion is only a message.
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    // Why the member is asking for this — the part a reviewer actually needs.
    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SuggestionStatus status = SuggestionStatus.PENDING;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Null after a decision only when the member who made it has since been removed (V7: ON DELETE SET NULL).
    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", columnDefinition = "text")
    private String reviewNote;

    @Version
    private long version;

    /** Stamps the creation timestamp before insert. */
    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
