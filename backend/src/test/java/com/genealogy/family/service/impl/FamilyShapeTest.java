package com.genealogy.family.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.genealogy.family.dto.response.FamilyChildResponse;
import com.genealogy.family.dto.response.FamilyResponse;
import com.genealogy.person.dto.response.PersonNodeResponse;
import com.genealogy.person.dto.response.PersonRedactedResponse;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pins what `/families` and `/persons/nodes` hand every role, a MEMBER included (CLAUDE.md §3.6). */
class FamilyShapeTest {

    /**
     * Lists a record's component names.
     *
     * @param type the record class
     * @return the component names, in declaration order
     */
    private static String[] componentsOf(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toArray(String[]::new);
    }

    @Test
    @DisplayName("a union carries structure only, never a date or a note, because /families is not redacted per role")
    void unionIsStructureOnly() {
        // Status and relation type are structure a MEMBER may see (decided 2026-09-23); a ngày cưới is an event.
        assertThat(componentsOf(FamilyResponse.class))
                .containsExactly("id", "partner1Id", "partner2Id", "status", "orderIndex", "children", "version");
        assertThat(componentsOf(FamilyChildResponse.class))
                .containsExactly("id", "childId", "relationToP1", "relationToP2", "birthOrder");
    }

    @Test
    @DisplayName("a person node carries nothing the redacted view of a living person does not")
    void nodeIsNoWiderThanTheRedactedView() {
        // /persons/nodes answers every role for any id, so a field here that the redacted view lacks is a leak.
        assertThat(componentsOf(PersonRedactedResponse.class)).contains(componentsOf(PersonNodeResponse.class));
    }
}
