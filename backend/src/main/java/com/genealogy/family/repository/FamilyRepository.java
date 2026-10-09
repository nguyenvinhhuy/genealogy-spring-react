package com.genealogy.family.repository;

import com.genealogy.family.domain.Family;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for unions. */
public interface FamilyRepository extends JpaRepository<Family, Long> {

    /**
     * Lists the unions a person belongs to, ordered vợ cả first.
     *
     * @param partner1Id the person as first partner
     * @param partner2Id the same person as second partner
     * @return the person's unions
     */
    List<Family> findByPartner1IdOrPartner2IdOrderByOrderIndexAsc(Long partner1Id, Long partner2Id);

    /**
     * Loads every union in which any of these people is a partner, in one query.
     *
     * @param personIds the people to look for
     * @return the matching unions
     */
    @Query("SELECT f FROM Family f WHERE f.partner1Id IN :personIds OR f.partner2Id IN :personIds")
    List<Family> findByAnyPartnerIn(@Param("personIds") Collection<Long> personIds);

    /**
     * Reads every union's partners straight from the table, never from entities already in the session.
     *
     * @return one row per union
     */
    @Query("SELECT f.id AS id, f.partner1Id AS partner1Id, f.partner2Id AS partner2Id FROM Family f")
    List<PartnerRow> findAllPartners();

    /**
     * One union's partners, as the whole-graph walks read them.
     */
    interface PartnerRow {

        /**
         * Returns the union id.
         *
         * @return the id
         */
        Long getId();

        /**
         * Returns the first partner.
         *
         * @return the partner id, or null
         */
        Long getPartner1Id();

        /**
         * Returns the second partner.
         *
         * @return the partner id, or null
         */
        Long getPartner2Id();
    }
}
