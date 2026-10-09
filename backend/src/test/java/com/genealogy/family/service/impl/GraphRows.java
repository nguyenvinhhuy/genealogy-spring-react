package com.genealogy.family.service.impl;

import com.genealogy.common.model.RelationType;
import com.genealogy.family.domain.Family;
import com.genealogy.family.domain.FamilyChild;
import com.genealogy.family.repository.FamilyChildRepository;
import com.genealogy.family.repository.FamilyRepository;
import java.util.List;

/** Test fixtures presenting fake unions and child links the way the whole-graph projections read them. */
final class GraphRows {

    private GraphRows() {
    }

    /**
     * Presents unions as the partner rows {@code findAllPartners} returns.
     *
     * @param families the unions
     * @return one row per union
     */
    static List<FamilyRepository.PartnerRow> partners(List<Family> families) {
        return families.stream().map(GraphRows::partnerRow).toList();
    }

    /**
     * Presents child links as the rows {@code findAllLinks} returns.
     *
     * @param links the child links
     * @return one row per link
     */
    static List<FamilyChildRepository.LinkRow> links(List<FamilyChild> links) {
        return links.stream().map(GraphRows::linkRow).toList();
    }

    /**
     * Presents one union as a partner row.
     *
     * @param family the union
     * @return its partner row
     */
    private static FamilyRepository.PartnerRow partnerRow(Family family) {
        return new FamilyRepository.PartnerRow() {
            @Override
            public Long getId() {
                return family.getId();
            }

            @Override
            public Long getPartner1Id() {
                return family.getPartner1Id();
            }

            @Override
            public Long getPartner2Id() {
                return family.getPartner2Id();
            }
        };
    }

    /**
     * Presents one child link as a link row.
     *
     * @param link the child link
     * @return its link row
     */
    private static FamilyChildRepository.LinkRow linkRow(FamilyChild link) {
        return new FamilyChildRepository.LinkRow() {
            @Override
            public Long getFamilyId() {
                return link.getFamilyId();
            }

            @Override
            public Long getChildId() {
                return link.getChildId();
            }

            @Override
            public RelationType getRelationToP1() {
                return link.getRelationToP1();
            }

            @Override
            public RelationType getRelationToP2() {
                return link.getRelationToP2();
            }
        };
    }
}
