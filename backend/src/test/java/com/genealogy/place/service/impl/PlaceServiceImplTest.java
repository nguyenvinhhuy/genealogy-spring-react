package com.genealogy.place.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.genealogy.audit.service.AuditService;
import com.genealogy.common.exception.BadRequestException;
import com.genealogy.common.model.AuditAction;
import com.genealogy.common.model.AuditEntityType;
import com.genealogy.common.model.PlaceType;
import com.genealogy.common.util.AdvisoryLock;
import com.genealogy.place.domain.Place;
import com.genealogy.place.dto.request.PlacePath;
import com.genealogy.place.dto.request.PlaceRequest;
import com.genealogy.place.mapper.PlaceMapper;
import com.genealogy.place.repository.PlaceRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for the place hierarchy: what may sit inside what, and how a GEDCOM path becomes rows (§3.7, §8.8). */
@ExtendWith(MockitoExtension.class)
class PlaceServiceImplTest {

    private static final Long ACTOR = 9L;

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private AdvisoryLock advisoryLock;

    private PlaceServiceImpl places;

    // Rows "saved" by a test, so findById can answer what saveAndFlush just wrote.
    private final Map<Long, Place> stored = new HashMap<>();

    // Every row saved, in order, so a test can read back the levels and parents that were chosen.
    private final List<Place> inserted = new ArrayList<>();

    @BeforeEach
    void setUp() {
        places = new PlaceServiceImpl(placeRepository, Mappers.getMapper(PlaceMapper.class), auditService,
                advisoryLock);
        lenient().when(placeRepository.saveAndFlush(any(Place.class))).thenAnswer(call -> {
            Place saved = call.getArgument(0);
            saved.setId(100L + inserted.size());
            inserted.add(saved);
            stored.put(saved.getId(), saved);
            return saved;
        });
        lenient().when(placeRepository.findById(anyLong()))
                .thenAnswer(call -> Optional.ofNullable(stored.get(call.<Long>getArgument(0))));
    }

    /**
     * Stores a place a test starts from.
     *
     * @param id place id
     * @param name its name
     * @param type its level
     * @param parentId its parent, or null
     * @return the entity
     */
    private Place existing(Long id, String name, PlaceType type, Long parentId) {
        Place place = new Place();
        place.setId(id);
        place.setName(name);
        place.setType(type);
        place.setParentId(parentId);
        stored.put(id, place);
        return place;
    }

    /**
     * Builds a request with no coordinates.
     *
     * @param name the place name
     * @param type its level
     * @param parentId its parent, or null
     * @return the request
     */
    private static PlaceRequest request(String name, PlaceType type, Long parentId) {
        return new PlaceRequest(name, type, parentId, null, null, "vì sao", null);
    }

    @Test
    @DisplayName("a place is refused inside a narrower one: a tỉnh cannot sit inside a xã")
    void refusesAParentNarrowerThanTheChild() {
        existing(1L, "Xã A", PlaceType.WARD, null);

        assertThatThrownBy(() -> places.create(request("Tỉnh B", PlaceType.PROVINCE, 1L), ACTOR))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("rộng hơn");
        verify(placeRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("an OTHER level fits anywhere, because tổng, phủ and trấn have no modern rank")
    void otherFitsAnywhere() {
        existing(1L, "Xã A", PlaceType.WARD, null);

        places.create(request("Tổng Hoằng", PlaceType.OTHER, 1L), ACTOR);

        assertThat(inserted).singleElement().satisfies(place -> assertThat(place.getParentId()).isEqualTo(1L));
    }

    @Test
    @DisplayName("a lone coordinate is refused in Vietnamese before the database constraint trips")
    void refusesALoneCoordinate() {
        PlaceRequest lone = new PlaceRequest("Xã A", PlaceType.WARD, null, new BigDecimal("21.02"), null, null, null);

        assertThatThrownBy(() -> places.create(lone, ACTOR))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("vĩ độ");
    }

    @Test
    @DisplayName("creating a place takes the tree lock and records it in the audit trail")
    void createLocksAndAudits() {
        places.create(request("Tỉnh C", PlaceType.PROVINCE, null), ACTOR);

        verify(advisoryLock).lock(AdvisoryLock.PLACE_TREE);
        verify(auditService).record(any(AuditEntityType.class), any(), any(AuditAction.class), any(), any(), any(),
                any());
    }

    @Test
    @DisplayName("a place cannot be moved inside itself or one of its own descendants")
    void refusesACycle() {
        existing(1L, "Tỉnh C", PlaceType.PROVINCE, null);
        existing(2L, "Huyện D", PlaceType.DISTRICT, 1L);
        when(placeRepository.findIdsWithDescendants(1L)).thenReturn(List.of(1L, 2L));

        assertThatThrownBy(() -> places.update(1L, request("Tỉnh C", PlaceType.OTHER, 2L), ACTOR))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("chính nó");
    }

    @Test
    @DisplayName("a place cannot be demoted below a child that would no longer fit beneath it")
    void refusesToNarrowBelowAChild() {
        existing(1L, "Tỉnh C", PlaceType.PROVINCE, null);
        Place district = existing(2L, "Huyện D", PlaceType.DISTRICT, 1L);
        when(placeRepository.findByParentId(1L)).thenReturn(List.of(district));

        // A xã holding a huyện is the inversion §3.7's hierarchy exists to prevent (§8.12 #7).
        assertThatThrownBy(() -> places.update(1L, request("Tỉnh C", PlaceType.WARD, null), ACTOR))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Huyện D");
    }

    @Test
    @DisplayName("a place with smaller places beneath it is not deleted, and the refusal names it")
    void refusesToDeleteAParent() {
        existing(1L, "Tỉnh C", PlaceType.PROVINCE, null);
        when(placeRepository.countByParentId(1L)).thenReturn(2L);

        assertThatThrownBy(() -> places.delete(1L, ACTOR, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Tỉnh C")
                .hasMessageContaining("2 nơi chốn con");
        verify(placeRepository, never()).delete(any());
    }

    @Test
    @DisplayName("a four-part path with no FORM is read as xã, huyện, tỉnh, quốc gia, widest created first")
    void readsAFullPathPositionally() {
        places.findOrCreatePath(new PlacePath(List.of("Xã A", "Huyện B", "Tỉnh C", "Việt Nam"), List.of(),
                new BigDecimal("21.02"), new BigDecimal("105.8")), ACTOR);

        assertThat(inserted).extracting(Place::getType)
                .containsExactly(PlaceType.COUNTRY, PlaceType.PROVINCE, PlaceType.DISTRICT, PlaceType.WARD);
        // Coordinates belong to the most specific place only: the whole of Việt Nam is not at 21.02, 105.8.
        assertThat(inserted.getFirst().getLatitude()).isNull();
        assertThat(inserted.getLast().getLatitude()).isEqualByComparingTo("21.02");
        assertThat(inserted.getLast().getParentId()).isEqualTo(inserted.get(2).getId());
    }

    @Test
    @DisplayName("a short path with no FORM guesses no level: a two-part path is not a tỉnh in a country")
    void doesNotGuessAShortPath() {
        places.findOrCreatePath(new PlacePath(List.of("Hoằng Lộc", "Thanh Hoá"), List.of(), null, null), ACTOR);

        // Indexing from the front made "Thanh Hoá" a COUNTRY; from the back it would be a guess all the same.
        assertThat(inserted).extracting(Place::getType).containsOnly(PlaceType.OTHER);
    }

    @Test
    @DisplayName("a FORM names each level, and one that does not fit under its parent is stored as OTHER")
    void usesTheFormAndFallsBackOnAMisfit() {
        places.findOrCreatePath(new PlacePath(List.of("Thôn E", "Xã A", "Tỉnh C"),
                List.of(PlaceType.VILLAGE, PlaceType.COUNTRY, PlaceType.PROVINCE), null, null), ACTOR);

        // Widest first: Tỉnh C as given, then a COUNTRY that cannot sit inside a tỉnh, then the thôn.
        assertThat(inserted).extracting(Place::getType)
                .containsExactly(PlaceType.PROVINCE, PlaceType.OTHER, PlaceType.VILLAGE);
    }

    @Test
    @DisplayName("a bare name the clan already holds is reused, not created a second time at the top")
    void reusesABareName() {
        when(placeRepository.findTopLevelIdsByName("Hà Nội")).thenReturn(List.of());
        when(placeRepository.findIdsByName("Hà Nội")).thenReturn(List.of(3L));

        Optional<Long> found =
                places.findOrCreatePath(new PlacePath(List.of(" Hà Nội "), List.of(), null, null), ACTOR);

        assertThat(found).contains(3L);
        assertThat(inserted).isEmpty();
        verify(advisoryLock).lock(AdvisoryLock.PLACE_TREE);
    }

    @Test
    @DisplayName("an over-long component is cut to the column rather than failing the insert")
    void cutsAnOverlongName() {
        places.findOrCreatePath(new PlacePath(List.of("X".repeat(500)), List.of(), null, null), ACTOR);

        assertThat(inserted.getFirst().getName()).hasSize(PlaceRequest.MAX_NAME);
    }
}
