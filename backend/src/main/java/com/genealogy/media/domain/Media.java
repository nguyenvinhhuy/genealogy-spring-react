package com.genealogy.media.domain;

import com.genealogy.common.model.MediaTargetType;
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

/** One photo or scan attached to a person, a union, a grave or a source (docs/analysis.md F3). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "media")
public class Media {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    private MediaTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaKind kind = MediaKind.PHOTO;

    // What the storage provider calls this object; opaque to everything outside that provider.
    @Column(name = "storage_key", nullable = false, columnDefinition = "text")
    private String storageKey;

    // A stored small copy for galleries; null when the provider sizes on the fly or the file cannot be scaled.
    @Column(name = "thumbnail_key", columnDefinition = "text")
    private String thumbnailKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, columnDefinition = "text")
    private String filename;

    @Column(columnDefinition = "text")
    private String caption;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private long version;

    /** Stamps the upload timestamp before insert. */
    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
