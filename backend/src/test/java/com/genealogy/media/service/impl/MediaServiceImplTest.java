package com.genealogy.media.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.genealogy.audit.service.AuditService;
import com.genealogy.common.exception.BadRequestException;
import com.genealogy.common.exception.ConflictException;
import com.genealogy.common.model.AuditAction;
import com.genealogy.common.model.AuditEntityType;
import com.genealogy.common.model.MediaTargetType;
import com.genealogy.common.model.Role;
import com.genealogy.family.service.FamilyService;
import com.genealogy.grave.service.GraveService;
import com.genealogy.media.domain.Media;
import com.genealogy.media.domain.MediaKind;
import com.genealogy.media.dto.request.MediaUpdateRequest;
import com.genealogy.media.dto.response.MediaResponse;
import com.genealogy.media.mapper.MediaMapper;
import com.genealogy.media.repository.MediaRepository;
import com.genealogy.media.service.ImageSize;
import com.genealogy.media.service.StorageService;
import com.genealogy.media.service.StoredFile;
import com.genealogy.member.service.MemberNameService;
import com.genealogy.person.service.PersonService;
import com.genealogy.source.service.SourceService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Unit tests for photo and scan handling (docs/analysis.md F3). */
@ExtendWith(MockitoExtension.class)
class MediaServiceImplTest {

    private static final Long PERSON_ID = 3L;
    private static final Long UPLOADER = 9L;
    private static final String NOTE = "Ảnh chụp nhầm người";
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] HEIC = "\0\0\0\u0018ftypheic\0\0\0\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] EXE = "MZ\u0090\0\u0003\0\0\0".getBytes(StandardCharsets.ISO_8859_1);

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private AuditService auditService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private PersonService personService;

    @Mock
    private FamilyService familyService;

    @Mock
    private GraveService graveService;

    @Mock
    private SourceService sourceService;

    @Mock
    private MemberNameService memberService;

    private MediaServiceImpl media;

    private final List<Media> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        media = new MediaServiceImpl(
                mediaRepository, Mappers.getMapper(MediaMapper.class), storageService, auditService,
                transactionTemplate, personService, familyService, graveService, sourceService, memberService);

        lenient().when(mediaRepository.findByTargetTypeAndTargetIdOrderBySortOrderAscIdAsc(any(), anyLong()))
                .thenReturn(stored);
        lenient().when(mediaRepository.findMaxSortOrder(any(), anyLong())).thenReturn(-1);
        lenient().when(mediaRepository.saveAndFlush(any(Media.class))).thenAnswer(call -> {
            Media saved = call.getArgument(0);
            saved.setId(50L);
            return saved;
        });
        lenient().when(storageService.resolveUrl(anyString(), eq(ImageSize.ORIGINAL)))
                .thenAnswer(call -> "https://example/" + call.getArgument(0));
        lenient().when(storageService.resolveUrl(anyString(), eq(ImageSize.THUMBNAIL)))
                .thenAnswer(call -> "https://example/small/" + call.getArgument(0));
        lenient().when(storageService.upload(any(), anyLong(), anyString()))
                .thenReturn(new StoredFile("object-key", "object-key-thumb.jpg"));
        lenient().when(transactionTemplate.execute(any())).thenAnswer(call ->
                call.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        lenient().when(memberService.findNames(any())).thenReturn(Map.of(UPLOADER, "Nguyễn Văn Trưởng"));
        lenient().when(personService.exists(PERSON_ID)).thenReturn(true);
    }

    /** Guards against a failed transaction-synchronization test leaving the ThreadLocal set for the next one. */
    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /**
     * Builds a stored file.
     *
     * @param id media id
     * @param kind what the file is
     * @param sortOrder its position
     * @return the entity
     */
    private static Media file(Long id, MediaKind kind, int sortOrder) {
        Media item = new Media();
        item.setId(id);
        item.setTargetType(MediaTargetType.PERSON);
        item.setTargetId(PERSON_ID);
        item.setKind(kind);
        item.setStorageKey("key-" + id);
        item.setContentType("image/jpeg");
        item.setSizeBytes(4);
        item.setFilename("anh.jpg");
        item.setSortOrder(sortOrder);
        return item;
    }

    /**
     * Uploads bytes to the test person.
     *
     * @param kind what the file is, or null
     * @param bytes the file
     * @return the stored file
     */
    private MediaResponse upload(MediaKind kind, byte[] bytes) {
        return media.upload(MediaTargetType.PERSON, PERSON_ID, kind, null, new ByteArrayResource(bytes),
                bytes.length, "anh.jpg", UPLOADER);
    }

    @Test
    @DisplayName("each accepted format is recognised from its bytes, whatever the file is called")
    void detectsTheTypeFromTheBytes() {
        // Every one is named anh.jpg: the name and the declared header are the client's claim, not the file.
        assertThat(upload(null, JPEG).contentType()).isEqualTo("image/jpeg");
        assertThat(upload(null, PNG).contentType()).isEqualTo("image/png");
        assertThat(upload(null, PDF).contentType()).isEqualTo("application/pdf");
        assertThat(upload(null, WEBP).contentType()).isEqualTo("image/webp");
        assertThat(upload(null, HEIC).contentType()).isEqualTo("image/heic");
    }

    @Test
    @DisplayName("a file whose bytes are not an accepted format is refused before anything is stored")
    void refusesOtherTypes() {
        assertThatThrownBy(() -> upload(null, EXE)).isInstanceOf(BadRequestException.class);
        verify(storageService, never()).upload(any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("an empty or oversized file is refused before anything is stored")
    void refusesEmptyAndOversized() {
        assertThatThrownBy(() -> media.upload(MediaTargetType.PERSON, PERSON_ID, null, null,
                        new ByteArrayResource(new byte[0]), 0, "a.jpg", UPLOADER))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> media.upload(MediaTargetType.PERSON, PERSON_ID, null, null,
                        new ByteArrayResource(JPEG), 21L * 1024 * 1024, "a.jpg", UPLOADER))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("20 MB");
        verify(storageService, never()).upload(any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("a file attached to a record that does not exist is refused, naming the kind in Vietnamese")
    void refusesUnknownTarget() {
        when(sourceService.exists(4L)).thenReturn(false);

        assertThatThrownBy(() -> media.upload(MediaTargetType.SOURCE, 4L, MediaKind.SCAN, null,
                        new ByteArrayResource(PDF), PDF.length, "trang1.pdf", UPLOADER))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("nguồn");
        verify(storageService, never()).upload(any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("a portrait on anything but a person is refused")
    void refusesAPortraitOffAPerson() {
        when(familyService.exists(7L)).thenReturn(true);

        assertThatThrownBy(() -> media.upload(MediaTargetType.FAMILY, 7L, MediaKind.PORTRAIT, null,
                        new ByteArrayResource(JPEG), JPEG.length, "cuoi.jpg", UPLOADER))
                .isInstanceOf(BadRequestException.class);
        verify(storageService, never()).upload(any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("a portrait in a format the book cannot embed is refused")
    void refusesAPortraitTheBookCannotDraw() {
        // A HEIC portrait showed nowhere in a browser and was dropped from the book without a word (§8.9 #9).
        assertThatThrownBy(() -> upload(MediaKind.PORTRAIT, HEIC)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> upload(MediaKind.PORTRAIT, PDF)).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("a new portrait demotes the old one, because the schema allows exactly one")
    void demotesTheOldPortrait() {
        Media old = file(1L, MediaKind.PORTRAIT, 0);
        when(mediaRepository.findFirstByTargetTypeAndTargetIdAndKind(
                        MediaTargetType.PERSON, PERSON_ID, MediaKind.PORTRAIT))
                .thenReturn(Optional.of(old));

        upload(MediaKind.PORTRAIT, JPEG);

        // uq_media_one_portrait would reject the insert otherwise; picking a new portrait is normal.
        assertThat(old.getKind()).isEqualTo(MediaKind.PHOTO);
        verify(auditService).record(eq(AuditEntityType.MEDIA), eq(1L), eq(AuditAction.UPDATE), any(), any(),
                eq(UPLOADER), isNull());
    }

    @Test
    @DisplayName("an upload is recorded in the trail, with its uploader named and both URLs signed")
    void recordsTheUpload() {
        MediaResponse response = upload(null, JPEG);

        verify(auditService).record(eq(AuditEntityType.MEDIA), eq(50L), eq(AuditAction.CREATE), isNull(), any(),
                eq(UPLOADER), isNull());
        assertThat(response.uploadedByName()).isEqualTo("Nguyễn Văn Trưởng");
        assertThat(response.url()).isEqualTo("https://example/object-key");
        assertThat(response.thumbnailUrl()).isEqualTo("https://example/small/object-key-thumb.jpg");
    }

    @Test
    @DisplayName("a new file goes to the end of the gallery")
    void appendsToTheGallery() {
        when(mediaRepository.findMaxSortOrder(MediaTargetType.PERSON, PERSON_ID)).thenReturn(4);

        assertThat(upload(null, JPEG).sortOrder()).isEqualTo(5);
    }

    @Test
    @DisplayName("an object whose row never commits is deleted again, original and thumbnail both")
    void cleansUpWhenTheRowFails() {
        when(mediaRepository.saveAndFlush(any(Media.class)))
                .thenThrow(new DataIntegrityViolationException("uq_media_one_portrait"));

        // Two concurrent portraits: one row loses the unique index, and its object used to stay for ever (§8.9 #5).
        assertThatThrownBy(() -> upload(MediaKind.PORTRAIT, JPEG)).isInstanceOf(ConflictException.class);
        verify(storageService).delete("object-key");
        verify(storageService).delete("object-key-thumb.jpg");
    }

    @Test
    @DisplayName("any other failure after the upload also deletes the object and is rethrown as it was")
    void cleansUpOnAnyFailure() {
        when(mediaRepository.saveAndFlush(any(Media.class))).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> upload(null, JPEG)).isInstanceOf(IllegalStateException.class);
        verify(storageService).delete("object-key");
    }

    @Test
    @DisplayName("a MEMBER gets no photos of a living person")
    void hidesLivingPersonPhotos() {
        when(personService.isLiving(PERSON_ID)).thenReturn(true);

        // A signed URL is a bearer link: once handed out it needs no login, so it must not be handed out.
        assertThat(media.findByTarget(MediaTargetType.PERSON, PERSON_ID, Role.MEMBER)).isEmpty();
        verify(mediaRepository, never())
                .findByTargetTypeAndTargetIdOrderBySortOrderAscIdAsc(any(), anyLong());
    }

    @Test
    @DisplayName("a MEMBER gets a deceased person's photos, with who uploaded them")
    void showsDeceasedPersonPhotos() {
        when(personService.isLiving(PERSON_ID)).thenReturn(false);
        Media portrait = file(1L, MediaKind.PORTRAIT, 0);
        portrait.setUploadedBy(UPLOADER);
        stored.add(portrait);
        stored.add(file(2L, MediaKind.PHOTO, 1));

        List<MediaResponse> files = media.findByTarget(MediaTargetType.PERSON, PERSON_ID, Role.MEMBER);

        assertThat(files).hasSize(2);
        assertThat(files.get(0).uploadedByName()).isEqualTo("Nguyễn Văn Trưởng");
        // The second has no recorded uploader, which must not become Map.get(null) (§9).
        assertThat(files.get(1).uploadedByName()).isNull();
    }

    @Test
    @DisplayName("a MEMBER gets no photos attached to the union of a living couple")
    void hidesLivingCouplePhotos() {
        when(familyService.involvesLiving(9L)).thenReturn(true);

        // Attaching the wedding photo to the union rather than to either partner must not open a side door.
        assertThat(media.findByTarget(MediaTargetType.FAMILY, 9L, Role.MEMBER)).isEmpty();
    }

    @Test
    @DisplayName("a MEMBER gets a deceased couple's wedding photos")
    void showsDeceasedCouplePhotos() {
        when(familyService.involvesLiving(9L)).thenReturn(false);
        stored.add(file(1L, MediaKind.PHOTO, 0));

        assertThat(media.findByTarget(MediaTargetType.FAMILY, 9L, Role.MEMBER)).hasSize(1);
    }

    @Test
    @DisplayName("a MEMBER gets no photos of a sinh phần, the plot of someone still alive")
    void hidesLivingPersonGravePhotos() {
        // The grave feature's own guard answers; media no longer resolves the owner itself (§9).
        when(graveService.involvesLiving(8L)).thenReturn(true);

        assertThat(media.findByTarget(MediaTargetType.GRAVE, 8L, Role.MEMBER)).isEmpty();
    }

    @Test
    @DisplayName("a MEMBER gets a source's scans: the old gia phả is the evidence a citation rests on (§8.12 D3)")
    void showsSourceScansToMembers() {
        stored.add(file(1L, MediaKind.SCAN, 0));

        assertThat(media.findByTarget(MediaTargetType.SOURCE, 4L, Role.MEMBER)).hasSize(1);
    }

    @Test
    @DisplayName("an EDITOR gets a source's scans and a living person's photos")
    void showsEverythingToEditors() {
        stored.add(file(1L, MediaKind.PORTRAIT, 0));

        assertThat(media.findByTarget(MediaTargetType.PERSON, PERSON_ID, Role.EDITOR)).hasSize(1);
        assertThat(media.findByTarget(MediaTargetType.SOURCE, 4L, Role.EDITOR)).hasSize(1);
        verify(personService, never()).isLiving(any());
    }

    @Test
    @DisplayName("a portrait is read at print size: the thumbnail when there is one, the original when not")
    void portraitKeysPreferTheThumbnail() {
        Media withThumb = file(1L, MediaKind.PORTRAIT, 0);
        withThumb.setThumbnailKey("key-1-thumb.jpg");
        Media withoutThumb = file(2L, MediaKind.PORTRAIT, 0);
        withoutThumb.setTargetId(4L);
        when(mediaRepository.findByTargetTypeAndKindOrderByIdAsc(MediaTargetType.PERSON, MediaKind.PORTRAIT))
                .thenReturn(List.of(withThumb, withoutThumb));

        assertThat(media.findPortraitKeys()).containsEntry(PERSON_ID, "key-1-thumb.jpg").containsEntry(4L, "key-2");
    }

    @Test
    @DisplayName("deleting a file removes both stored copies and records why")
    void deletesTheStoredObject() {
        Media item = file(1L, MediaKind.PHOTO, 0);
        item.setThumbnailKey("key-1-thumb.jpg");
        when(mediaRepository.findById(1L)).thenReturn(Optional.of(item));

        media.delete(1L, UPLOADER, NOTE);

        verify(mediaRepository).delete(item);
        verify(storageService).delete("key-1");
        verify(storageService).delete("key-1-thumb.jpg");
        verify(auditService).record(eq(AuditEntityType.MEDIA), eq(1L), eq(AuditAction.DELETE), any(), isNull(),
                eq(UPLOADER), eq(NOTE));
    }

    @Test
    @DisplayName("inside a transaction, the stored object is deleted only after it commits")
    void deletesTheStoredObjectAfterCommitOnly() {
        stored.add(file(1L, MediaKind.PHOTO, 0));

        TransactionSynchronizationManager.initSynchronization();
        try {
            media.forgetTarget(MediaTargetType.PERSON, PERSON_ID, UPLOADER, NOTE);
            // A rollback must not have deleted the file: the row can still come back, but the object cannot.
            verify(storageService, never()).delete(any());

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(storageService).delete("key-1");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("forgetting a record's files records each one deleted")
    void forgetTargetIsAudited() {
        stored.add(file(1L, MediaKind.PHOTO, 0));
        stored.add(file(2L, MediaKind.PHOTO, 1));

        assertThat(media.forgetTarget(MediaTargetType.PERSON, PERSON_ID, UPLOADER, NOTE)).isEqualTo(2);
        verify(auditService).record(eq(AuditEntityType.MEDIA), eq(2L), eq(AuditAction.DELETE), any(), isNull(),
                eq(UPLOADER), eq(NOTE));
    }

    @Test
    @DisplayName("a merge moves files after the survivor's own, keeps the survivor's portrait, and records each move")
    void reassignKeepsTheSurvivorsPortrait() {
        Media moving = file(1L, MediaKind.PORTRAIT, 0);
        stored.add(moving);
        when(mediaRepository.findFirstByTargetTypeAndTargetIdAndKind(MediaTargetType.PERSON, 4L, MediaKind.PORTRAIT))
                .thenReturn(Optional.of(file(2L, MediaKind.PORTRAIT, 0)));
        when(mediaRepository.findMaxSortOrder(MediaTargetType.PERSON, 4L)).thenReturn(6);

        assertThat(media.reassignTarget(MediaTargetType.PERSON, PERSON_ID, 4L, UPLOADER, NOTE)).isEqualTo(1);

        assertThat(moving.getTargetId()).isEqualTo(4L);
        assertThat(moving.getKind()).isEqualTo(MediaKind.PHOTO);
        assertThat(moving.getSortOrder()).isEqualTo(7);
        verify(auditService).record(eq(AuditEntityType.MEDIA), eq(1L), eq(AuditAction.UPDATE), any(), any(),
                eq(UPLOADER), eq(NOTE));
    }

    @Test
    @DisplayName("setting a photo as the portrait does not erase its caption")
    void keepsTheCaptionWhenOnlyTheKindChanges() {
        Media item = file(1L, MediaKind.PHOTO, 0);
        item.setCaption("Trang 3 gia phả chữ Hán");
        when(mediaRepository.findById(1L)).thenReturn(Optional.of(item));

        // The UI sends only `kind` here, and assigning caption unconditionally wiped what the family wrote.
        MediaResponse response =
                media.update(1L, new MediaUpdateRequest(MediaKind.PORTRAIT, null, null, null, null), UPLOADER);

        assertThat(response.caption()).isEqualTo("Trang 3 gia phả chữ Hán");
        assertThat(response.kind()).isEqualTo(MediaKind.PORTRAIT);
    }

    @Test
    @DisplayName("a PDF cannot be made the portrait after the fact either")
    void refusesAPdfPortraitOnEdit() {
        Media item = file(1L, MediaKind.SCAN, 0);
        item.setContentType("application/pdf");
        when(mediaRepository.findById(1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> media.update(
                        1L, new MediaUpdateRequest(MediaKind.PORTRAIT, null, null, null, null), UPLOADER))
                .isInstanceOf(BadRequestException.class);
        assertThat(item.getKind()).isEqualTo(MediaKind.SCAN);
    }

    @Test
    @DisplayName("a blank caption clears it, so there is still a way to take one back")
    void blankCaptionClearsIt() {
        Media item = file(1L, MediaKind.PHOTO, 0);
        item.setCaption("Sai rồi");
        when(mediaRepository.findById(1L)).thenReturn(Optional.of(item));

        assertThat(media.update(1L, new MediaUpdateRequest(null, "  ", null, null, null), UPLOADER).caption())
                .isNull();
    }

    @Test
    @DisplayName("an edit made from a stale form is refused")
    void refusesAStaleEdit() {
        Media item = file(1L, MediaKind.PHOTO, 0);
        item.setVersion(3);
        when(mediaRepository.findById(1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> media.update(1L, new MediaUpdateRequest(null, "Mới", null, null, 2L), UPLOADER))
                .isInstanceOf(ConflictException.class);
        assertThat(item.getCaption()).isNull();
    }

    @Test
    @DisplayName("editing a caption leaves the stored object alone and records the edit with its reason")
    void editsMetadataOnly() {
        Media item = file(1L, MediaKind.PHOTO, 0);
        when(mediaRepository.findById(1L)).thenReturn(Optional.of(item));

        MediaResponse response = media.update(
                1L, new MediaUpdateRequest(null, " Cụ chụp năm 1960 ", 2, "Theo lời bác cả", 0L), UPLOADER);

        assertThat(response.caption()).isEqualTo("Cụ chụp năm 1960");
        assertThat(response.sortOrder()).isEqualTo(2);
        assertThat(response.kind()).isEqualTo(MediaKind.PHOTO);
        verify(storageService, never()).delete(any());
        verify(storageService, never()).upload(any(), anyLong(), anyString());
        verify(auditService).record(eq(AuditEntityType.MEDIA), eq(1L), eq(AuditAction.UPDATE), any(), any(),
                eq(UPLOADER), eq("Theo lời bác cả"));
    }
}
