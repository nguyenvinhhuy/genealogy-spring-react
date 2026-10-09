package com.genealogy.grave.repository;

import com.genealogy.common.model.GraveKind;
import com.genealogy.grave.domain.Grave;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for graves. */
public interface GraveRepository extends JpaRepository<Grave, Long> {

    /**
     * Finds the grave recorded for one person.
     *
     * @param personId the person id
     * @return the grave, or empty when none is recorded
     */
    Optional<Grave> findByPersonId(Long personId);

    /**
     * Lists every grave that has coordinates, for plotting on a map.
     *
     * @return the located graves
     */
    List<Grave> findByLatitudeIsNotNull();

    /**
     * Counts the graves recorded at one place.
     *
     * @param placeId the place id
     * @return how many graves name it
     */
    long countByPlaceId(Long placeId);

    /**
     * Finds which of a batch of people have a grave of one kind recorded.
     *
     * @param kind the kind of grave
     * @param personIds the people to check
     * @return the ids of those who do
     */
    @Query("SELECT g.personId FROM Grave g WHERE g.kind = :kind AND g.personId IN :personIds")
    List<Long> findPersonIdsWithKind(@Param("kind") GraveKind kind, @Param("personIds") Collection<Long> personIds);

    /**
     * Lists every person with a grave of one kind recorded.
     *
     * @param kind the kind of grave
     * @return their ids
     */
    @Query("SELECT g.personId FROM Grave g WHERE g.kind = :kind")
    List<Long> findAllPersonIdsWithKind(@Param("kind") GraveKind kind);
}
