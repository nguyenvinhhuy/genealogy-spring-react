package com.genealogy.media.service.impl;

import com.genealogy.audit.service.AuditService;
import com.genealogy.common.exception.BadRequestException;
import com.genealogy.common.exception.ConflictException;
import com.genealogy.common.exception.NotFoundException;
import com.genealogy.common.model.AuditAction;
import com.genealogy.common.model.AuditEntityType;
import com.genealogy.common.model.MediaTargetType;
import com.genealogy.common.model.Role;
import com.genealogy.common.util.Blank;
import com.genealogy.common.util.StaleEdit;
import com.genealogy.family.service.FamilyService;
import com.genealogy.grave.service.GraveService;
import com.genealogy.media.domain.Media;
import com.genealogy.media.domain.MediaKind;
import com.genealogy.media.dto.request.MediaUpdateRequest;
import com.genealogy.media.dto.response.MediaResponse;
import com.genealogy.media.dto.response.MediaSnapshot;
import com.genealogy.media.mapper.MediaMapper;
import com.genealogy.media.repository.MediaRepository;
import com.genealogy.media.service.ImageSize;
import com.genealogy.media.service.MediaService;
import com.genealogy.media.service.StorageService;
import com.genealogy.media.service.StoredFile;
import com.genealogy.member.service.MemberNameService;
import com.genealogy.person.service.PersonService;
import com.genealogy.source.service.SourceService;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Default {@link MediaService}. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaServiceImpl implements MediaService {

    // An A3 scan of a gia phả page is genuinely large; a phone photo of one is larger still.
    public static final long MAX_BYTES = 20L * 1024 * 1024;

    private static final String DEFAULT_FILENAME = "tệp";

    private final MediaRepository mediaRepository;
    private final MediaMapper mediaMapper;
    private final StorageService storageService;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final PersonService personService;
    private final FamilyService familyService;
    private final GraveService graveService;
    private final SourceService sourceService;
    private final MemberNameService memberNameService;


    /**
     * Returns the storage key of every person's portrait at print size, in one query.
     *
     * @return the key of each portrait's small copy, or of the original when there is none, by person id
     */
    @Override
    public Map<Long, String> findPortraitKeys() {
        // uq_media_one_portrait allows one per record; the merge function only stops toMap throwing if that broke.
        return mediaRepository
                .findByTargetTypeAndKindOrderByIdAsc(MediaTargetType.PERSON, MediaKind.PORTRAIT)
                .stream()
                .collect(Collectors.toMap(Media::getTargetId, MediaServiceImpl::smallestKey, (first, second) -> first));
    }

    /**
     * Lists the files attached to one record, hiding a living person's and a source's from callers below EDITOR.
     *
     * @param targetType what kind of record
     * @param targetId the record id
     * @param role the calling member's access level
     * @return the files, or nothing when the caller may not see them (§3.6)
     */
    @Override
    public List<MediaResponse> findByTarget(MediaTargetType targetType, Long targetId, Role role) {
        // §3.6 holds back a living person's portrait, and a signed URL is a bearer link once handed out.
        if (!Role.maySeeLivingDetails(role) && hiddenBelowEditor(targetType, targetId)) {
            return List.of();
        }
        List<Media> files =
                mediaRepository.findByTargetTypeAndTargetIdOrderBySortOrderAscIdAsc(targetType, targetId);
        Map<Long, String> uploaders = memberNameService.findNames(
                files.stream().map(Media::getUploadedBy).filter(Objects::nonNull).distinct().toList());
        // Never Map.get(null): uploaded_by is ON DELETE SET NULL, and findNames may answer with a Map.of() (§9).
        return files.stream()
                .map(media -> toResponse(media,
                        media.getUploadedBy() == null ? null : uploaders.get(media.getUploadedBy())))
                .toList();
    }

    /**
     * Counts the files attached to one record.
     *
     * @param targetType what kind of record
     * @param targetId the record id
     * @return how many there are
     */
    @Override
    public int countByTarget(MediaTargetType targetType, Long targetId) {
        return mediaRepository.countByTargetTypeAndTargetId(targetType, targetId);
    }

    /**
     * Stores an uploaded file and attaches it to a record, recording the upload.
     *
     * @param targetType what kind of record
     * @param targetId the record id
     * @param kind what the file is
     * @param caption what the family wants written under it
     * @param content the file, readable more than once
     * @param size how many bytes it has
     * @param filename what the file was called
     * @param uploaderId id of the member uploading it
     * @return the stored file
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MediaResponse upload(
            MediaTargetType targetType,
            Long targetId,
            MediaKind kind,
            String caption,
            InputStreamSource content,
            long size,
            String filename,
            Long uploaderId) {

        // Outside any transaction: holding a connection and a row lock through the network upload starves the pool.
        requireTargetExists(targetType, targetId);
        MediaKind resolved = kind == null ? MediaKind.PHOTO : kind;
        String type = detectedType(content, size);
        requirePortraitAllowed(resolved, targetType, type);

        StoredFile stored = storageService.upload(content, size, type);
        Media saved;
        try {
            saved = transactionTemplate.execute(status -> {
                // Asked again: a purge or merge may have removed the record while the file was in flight (§8.12 #11).
                requireTargetExists(targetType, targetId);
                return insert(targetType, targetId, resolved, caption, type, size, filename, uploaderId, stored);
            });
        } catch (RuntimeException e) {
            // The row never committed, so nothing can name these objects any more (§8.9 #5).
            deleteQuietly(keysOf(stored.storageKey(), stored.thumbnailKey()));
            if (e instanceof DataIntegrityViolationException) {
                throw new ConflictException(
                        "Vừa có ảnh chân dung khác được đặt cho người này. Hãy tải lại rồi thử lại.");
            }
            throw e;
        }
        // Outside the try: the row is committed, so a failure reading names back must not delete its objects.
        return toResponse(Objects.requireNonNull(saved), uploaderName(saved));
    }

    /**
     * Edits what is recorded about an already-uploaded file, recording the edit.
     *
     * @param id media id
     * @param request the new values
     * @param actorId id of the member making the change
     * @return the updated file
     */
    @Override
    @Transactional
    public MediaResponse update(Long id, MediaUpdateRequest request, Long actorId) {
        Media media = require(id);
        StaleEdit.refuseIfStale(request.version(), media.getVersion(), AuditEntityType.MEDIA);
        MediaSnapshot before = snapshot(media);

        if (request.kind() == MediaKind.PORTRAIT && media.getKind() != MediaKind.PORTRAIT) {
            requirePortraitAllowed(MediaKind.PORTRAIT, media.getTargetType(), media.getContentType());
            demoteExistingPortrait(media.getTargetType(), media.getTargetId(), actorId, request.changeNote());
        }
        if (request.kind() != null) {
            media.setKind(request.kind());
        }
        if (request.sortOrder() != null) {
            media.setSortOrder(request.sortOrder());
        }
        if (request.caption() != null) {
            // Null keeps it, blank clears it: setting a portrait sends only `kind` and used to erase this.
            media.setCaption(Blank.toNull(request.caption()));
        }
        mediaRepository.flush();
        auditService.record(AuditEntityType.MEDIA, id, AuditAction.UPDATE, before, snapshot(media), actorId,
                request.changeNote());
        return toResponse(media, uploaderName(media));
    }

    /**
     * Deletes a file from the database, and from storage once the delete commits, recording why.
     *
     * @param id media id
     * @param actorId id of the member making the change
     * @param changeNote why the file is being deleted, or null
     */
    @Override
    @Transactional
    public void delete(Long id, Long actorId, String changeNote) {
        Media media = require(id);
        MediaSnapshot before = snapshot(media);
        mediaRepository.delete(media);
        auditService.record(AuditEntityType.MEDIA, id, AuditAction.DELETE, before, null, actorId, changeNote);
        deleteObjectsAfterCommit(keysOf(media.getStorageKey(), media.getThumbnailKey()));
    }

    /**
     * Moves every file off one record onto another, for a merge, recording each move.
     *
     * @param targetType what kind of record is being merged
     * @param fromId the record being absorbed
     * @param toId the record being kept
     * @param actorId the member running the merge
     * @param changeNote the merge's reason
     * @return how many files were repointed
     */
    @Override
    @Transactional
    public int reassignTarget(MediaTargetType targetType, Long fromId, Long toId, Long actorId, String changeNote) {
        List<Media> moving =
                mediaRepository.findByTargetTypeAndTargetIdOrderBySortOrderAscIdAsc(targetType, fromId);
        if (moving.isEmpty()) {
            return 0;
        }

        // uq_media_one_portrait allows one, and a merge does not override the survivor's own choice.
        boolean targetHasPortrait = mediaRepository
                .findFirstByTargetTypeAndTargetIdAndKind(targetType, toId, MediaKind.PORTRAIT)
                .isPresent();
        int next = mediaRepository.findMaxSortOrder(targetType, toId) + 1;
        List<MediaSnapshot> before = moving.stream().map(this::snapshot).toList();
        for (Media media : moving) {
            if (targetHasPortrait && media.getKind() == MediaKind.PORTRAIT) {
                media.setKind(MediaKind.PHOTO);
            }
            media.setTargetId(toId);
            media.setSortOrder(next++);
        }
        mediaRepository.flush();
        for (int i = 0; i < moving.size(); i++) {
            Media media = moving.get(i);
            auditService.record(AuditEntityType.MEDIA, media.getId(), AuditAction.UPDATE, before.get(i),
                    snapshot(media), actorId, changeNote);
        }
        return moving.size();
    }

    /**
     * Deletes the files of a record that is itself being deleted, their objects once the delete commits.
     *
     * @param targetType what kind of record
     * @param targetId the record id
     * @param actorId the member deleting the record
     * @param changeNote why the record is being deleted
     * @return how many file rows were deleted
     */
    @Override
    @Transactional
    public int forgetTarget(MediaTargetType targetType, Long targetId, Long actorId, String changeNote) {
        List<Media> doomed =
                mediaRepository.findByTargetTypeAndTargetIdOrderBySortOrderAscIdAsc(targetType, targetId);
        if (doomed.isEmpty()) {
            return 0;
        }
        mediaRepository.deleteAll(doomed);
        mediaRepository.flush();
        doomed.forEach(media -> auditService.record(AuditEntityType.MEDIA, media.getId(), AuditAction.DELETE,
                snapshot(media), null, actorId, changeNote));
        // The objects go too, or the bucket keeps paying for photos no row can name any more.
        deleteObjectsAfterCommit(doomed.stream()
                .flatMap(media -> keysOf(media.getStorageKey(), media.getThumbnailKey()).stream())
                .toList());
        return doomed.size();
    }

    /**
     * Writes the row for an object already in storage, demoting the old portrait first, and records it.
     *
     * @param targetType what kind of record
     * @param targetId the record id
     * @param kind what the file is
     * @param caption what the family wants written under it
     * @param type the media type read from the bytes
     * @param size how many bytes it has
     * @param filename what the file was called
     * @param uploaderId id of the member uploading it
     * @param stored where storage put it
     * @return the saved row
     */
    private Media insert(
            MediaTargetType targetType,
            Long targetId,
            MediaKind kind,
            String caption,
            String type,
            long size,
            String filename,
            Long uploaderId,
            StoredFile stored) {

        if (kind == MediaKind.PORTRAIT) {
            demoteExistingPortrait(targetType, targetId, uploaderId, null);
        }
        Media media = new Media();
        media.setTargetType(targetType);
        media.setTargetId(targetId);
        media.setKind(kind);
        media.setStorageKey(stored.storageKey());
        media.setThumbnailKey(stored.thumbnailKey());
        media.setContentType(type);
        media.setSizeBytes(size);
        media.setFilename(filename == null || filename.isBlank() ? DEFAULT_FILENAME : filename.strip());
        media.setCaption(Blank.toNull(caption));
        media.setSortOrder(mediaRepository.findMaxSortOrder(targetType, targetId) + 1);
        media.setUploadedBy(uploaderId);
        Media saved = mediaRepository.saveAndFlush(media);
        auditService.record(AuditEntityType.MEDIA, saved.getId(), AuditAction.CREATE, null, snapshot(saved),
                uploaderId, null);
        return saved;
    }

    /**
     * Rejects an upload that is empty or too large, and reads its real type from its first bytes.
     *
     * @param content the file
     * @param size how many bytes it has
     * @return the media type the bytes say it is
     */
    private static String detectedType(InputStreamSource content, long size) {
        if (content == null || size <= 0) {
            throw new BadRequestException("Tệp rỗng");
        }
        if (size > MAX_BYTES) {
            throw new BadRequestException("Tệp quá lớn, tối đa 20 MB");
        }
        byte[] head;
        try (InputStream in = content.getInputStream()) {
            head = in.readNBytes(FileTypes.HEAD_BYTES);
        } catch (IOException e) {
            throw new BadRequestException("Không đọc được tệp tải lên");
        }
        // The header is whatever the browser guessed: a renamed .exe arrived as image/jpeg (§8.9 #8).
        return FileTypes.detect(head).orElseThrow(() ->
                new BadRequestException("Chỉ nhận ảnh JPEG, PNG, WebP, GIF, HEIC hoặc tệp PDF"));
    }

    /**
     * Rejects a portrait on anything but a person, or in a format a browser and the book cannot both show.
     *
     * @param kind what the file is to be
     * @param targetType what kind of record it hangs off
     * @param type the file's media type
     */
    private static void requirePortraitAllowed(MediaKind kind, MediaTargetType targetType, String type) {
        if (kind != MediaKind.PORTRAIT) {
            return;
        }
        if (targetType != MediaTargetType.PERSON) {
            throw new BadRequestException("Chỉ một người mới có ảnh chân dung");
        }
        if (!FileTypes.isPortraitType(type)) {
            throw new BadRequestException("Ảnh chân dung phải là JPEG, PNG hoặc GIF");
        }
    }

    /**
     * Turns the record's current portrait into an ordinary photo, recording the change.
     *
     * @param targetType what kind of record
     * @param targetId the record id
     * @param actorId the member choosing the new portrait
     * @param changeNote why, or null
     */
    private void demoteExistingPortrait(MediaTargetType targetType, Long targetId, Long actorId, String changeNote) {
        // The old one is demoted rather than the new one refused: changing portrait is a normal wish.
        mediaRepository
                .findFirstByTargetTypeAndTargetIdAndKind(targetType, targetId, MediaKind.PORTRAIT)
                .ifPresent(existing -> {
                    MediaSnapshot before = snapshot(existing);
                    existing.setKind(MediaKind.PHOTO);
                    auditService.record(AuditEntityType.MEDIA, existing.getId(), AuditAction.UPDATE, before,
                            snapshot(existing), actorId, changeNote);
                });
        mediaRepository.flush();
    }

    /**
     * Deletes stored objects once the surrounding transaction has committed, or at once outside one.
     *
     * @param keys the storage keys to delete
     */
    private void deleteObjectsAfterCommit(List<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(keys);
            return;
        }
        // A rollback brings the rows back, and a row whose object was already deleted is a photo lost for good.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(keys);
            }
        });
    }

    /**
     * Deletes stored objects one by one, logging rather than throwing when one will not go.
     *
     * @param keys the storage keys to delete
     */
    private void deleteQuietly(List<String> keys) {
        for (String key : keys) {
            try {
                storageService.delete(key);
            } catch (RuntimeException e) {
                // The row is gone either way; an orphan object costs space, a thrown error would cost the edit.
                log.warn("Could not delete stored object {}", key, e);
            }
        }
    }

    /**
     * Rejects a file attached to a record that does not exist.
     *
     * @param targetType what kind of record
     * @param targetId the record id
     */
    private void requireTargetExists(MediaTargetType targetType, Long targetId) {
        boolean exists = targetId != null && switch (targetType) {
            case PERSON -> personService.exists(targetId);
            case FAMILY -> familyService.exists(targetId);
            case GRAVE -> graveService.exists(targetId);
            case SOURCE -> sourceService.exists(targetId);
        };
        if (!exists) {
            throw new BadRequestException("Không có " + AuditEntityType.nounOf(targetType) + " với id " + targetId);
        }
    }

    /**
     * Reports whether a file's target is held back from callers below EDITOR.
     *
     * @param targetType what kind of record
     * @param targetId the record id
     * @return true when the record involves someone living
     */
    private boolean hiddenBelowEditor(MediaTargetType targetType, Long targetId) {
        // A wedding photo of a living couple is the same private detail as a portrait of one of them.
        return switch (targetType) {
            case PERSON -> personService.isLiving(targetId);
            case FAMILY -> familyService.involvesLiving(targetId);
            // The grave feature's own guard, not a copy: a sinh phần is a plot built for someone still alive.
            case GRAVE -> graveService.involvesLiving(targetId);
            // A scan is the evidence a citation rests on, and a MEMBER already sees the citation (§8.12 D3).
            case SOURCE -> false;
        };
    }

    /**
     * Returns the key of the smallest stored copy of a file.
     *
     * @param media the file
     * @return the thumbnail's key, or the original's when there is none
     */
    private static String smallestKey(Media media) {
        return media.getThumbnailKey() != null ? media.getThumbnailKey() : media.getStorageKey();
    }

    /**
     * Lists the non-null keys among a file's stored copies.
     *
     * @param storageKey the original's key
     * @param thumbnailKey the small copy's key, or null
     * @return the keys to delete
     */
    private static List<String> keysOf(String storageKey, String thumbnailKey) {
        return Stream.of(storageKey, thumbnailKey).filter(Objects::nonNull).toList();
    }

    /**
     * Looks up the name of a file's uploader.
     *
     * @param media the file
     * @return the name, or null when the member is gone or unrecorded
     */
    private String uploaderName(Media media) {
        // Never Map.get(null) on a Map.of(): uploaded_by is ON DELETE SET NULL (§9).
        if (media.getUploadedBy() == null) {
            return null;
        }
        return memberNameService.findNames(List.of(media.getUploadedBy())).get(media.getUploadedBy());
    }

    /**
     * Captures what the trail keeps of a file.
     *
     * @param media the file
     * @return its snapshot
     */
    private MediaSnapshot snapshot(Media media) {
        return mediaMapper.toSnapshot(media);
    }

    /**
     * Loads a file or fails.
     *
     * @param id media id
     * @return the file
     */
    private Media require(Long id) {
        return mediaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không có tệp với id " + id));
    }

    /**
     * Converts a stored file to its response, signing a URL for the original and for its small copy.
     *
     * @param media the entity
     * @param uploadedByName the uploader's name, or null
     * @return the response DTO
     */
    private MediaResponse toResponse(Media media, String uploadedByName) {
        String url = storageService.resolveUrl(media.getStorageKey(), ImageSize.ORIGINAL);
        String thumbnailUrl = storageService.resolveUrl(smallestKey(media), ImageSize.THUMBNAIL);
        return mediaMapper.toResponse(media, url, thumbnailUrl, uploadedByName);
    }
}
