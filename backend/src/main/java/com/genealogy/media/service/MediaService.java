package com.genealogy.media.service;

import com.genealogy.common.model.MediaTargetType;
import com.genealogy.common.model.Role;
import com.genealogy.media.domain.MediaKind;
import com.genealogy.media.dto.request.MediaUpdateRequest;
import com.genealogy.media.dto.response.MediaResponse;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.InputStreamSource;

/** Photo and scan operations (docs/analysis.md F3). */
public interface MediaService {

    /**
     * Returns the storage key of every person's portrait at print size, in one query.
     *
     * @return the key of each portrait's small copy, or of the original when there is none, by person id
     */
    // A read-across projection (§4): the book needs every portrait at once, not one lookup per person.
    Map<Long, String> findPortraitKeys();

    /**
     * Lists the files attached to one record, hiding a living person's and a source's from callers below EDITOR.
     *
     * @param targetType what kind of record
     * @param targetId the record id
     * @param role the calling member's access level
     * @return the files, or nothing when the caller may not see them (§3.6)
     */
    List<MediaResponse> findByTarget(MediaTargetType targetType, Long targetId, Role role);

    /**
     * Counts the files attached to one record.
     *
     * @param targetType what kind of record
     * @param targetId the record id
     * @return how many there are
     */
    int countByTarget(MediaTargetType targetType, Long targetId);

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
    MediaResponse upload(
            MediaTargetType targetType,
            Long targetId,
            MediaKind kind,
            String caption,
            InputStreamSource content,
            long size,
            String filename,
            Long uploaderId);

    /**
     * Edits what is recorded about an already-uploaded file, recording the edit.
     *
     * @param id media id
     * @param request the new values
     * @param actorId id of the member making the change
     * @return the updated file
     */
    MediaResponse update(Long id, MediaUpdateRequest request, Long actorId);

    /**
     * Deletes a file from the database, and from storage once the delete commits, recording why.
     *
     * @param id media id
     * @param actorId id of the member making the change
     * @param changeNote why the file is being deleted, or null
     */
    void delete(Long id, Long actorId, String changeNote);

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
    int reassignTarget(MediaTargetType targetType, Long fromId, Long toId, Long actorId, String changeNote);

    /**
     * Deletes the files of a record that is itself being deleted, their objects once the delete commits.
     *
     * @param targetType what kind of record
     * @param targetId the record id
     * @param actorId the member deleting the record
     * @param changeNote why the record is being deleted
     * @return how many file rows were deleted
     */
    int forgetTarget(MediaTargetType targetType, Long targetId, Long actorId, String changeNote);
}
