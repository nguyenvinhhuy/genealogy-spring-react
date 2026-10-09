package com.genealogy.person.domain;

import com.genealogy.common.model.PersonNameType;
import com.genealogy.common.util.NameDisplay;
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

/** One of a person's names, split into surname / middle / given (CLAUDE.md 5.2). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "person_names")
public class PersonName {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PersonNameType type = PersonNameType.BIRTH;

    @Column(length = 50)
    private String surname;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "given_name", nullable = false, length = 50)
    private String givenName;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

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

    /**
     * Renders the name surname-first, the way Vietnamese names are always written.
     *
     * @return the display name
     */
    public String display() {
        return NameDisplay.of(surname, middleName, givenName);
    }
}
