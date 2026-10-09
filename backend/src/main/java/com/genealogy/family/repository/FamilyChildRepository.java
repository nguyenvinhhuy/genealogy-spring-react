package com.genealogy.family.repository;

import com.genealogy.common.model.RelationType;
import com.genealogy.family.domain.FamilyChild;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Data access for child links. */
public interface FamilyChildRepository extends JpaRepository<FamilyChild, Long> {

    /**
     * Lists the children of one union, con trưởng first.
     *
     * @param familyId the family id
     * @return the child links
     */
    List<FamilyChild> findByFamilyIdOrderByBirthOrderAsc(Long familyId);

    /**
     * Lists the unions a person appears in as a child.
     *
     * @param childId the person id
     * @return the child links
     */
    List<FamilyChild> findByChildId(Long childId);

    /**
     * Finds one child link.
     *
     * @param familyId the family id
     * @param childId the person id
     * @return the link, or empty when the child is not in that union
     */
    Optional<FamilyChild> findByFamilyIdAndChildId(Long familyId, Long childId);

    /**
     * Loads the child links of many unions in one query.
     *
     * @param familyIds the unions to load
     * @return their child links
     */
    List<FamilyChild> findByFamilyIdInOrderByBirthOrderAsc(Collection<Long> familyIds);

    /**
     * Loads the links naming any of these people as a child, in one query.
     *
     * @param childIds the people to look for
     * @return the matching links
     */
    List<FamilyChild> findByChildIdIn(Collection<Long> childIds);

    /**
     * Reads every child link straight from the table, never from entities already in the session.
     *
     * @return one row per link
     */
    @Query("SELECT c.familyId AS familyId, c.childId AS childId, c.relationToP1 AS relationToP1,"
            + " c.relationToP2 AS relationToP2 FROM FamilyChild c")
    List<LinkRow> findAllLinks();

    /**
     * One child link, as the whole-graph walks read it.
     */
    interface LinkRow {

        /**
         * Returns the union the child is linked into.
         *
         * @return the family id
         */
        Long getFamilyId();

        /**
         * Returns the linked child.
         *
         * @return the person id
         */
        Long getChildId();

        /**
         * Returns how the child relates to the union's first partner.
         *
         * @return the relation type
         */
        RelationType getRelationToP1();

        /**
         * Returns how the child relates to the union's second partner.
         *
         * @return the relation type
         */
        RelationType getRelationToP2();
    }
}
