package com.genealogy.media.controller;

import com.genealogy.common.model.MediaTargetType;
import com.genealogy.common.security.AuthPrincipal;
import com.genealogy.common.util.ChangeNotes;
import com.genealogy.media.domain.MediaKind;
import com.genealogy.media.dto.request.MediaUpdateRequest;
import com.genealogy.media.dto.response.MediaResponse;
import com.genealogy.media.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Endpoints for photos and scans of the gia phả. */
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    /**
     * Lists the files attached to one record, hiding a living person's and a source's from callers below EDITOR.
     *
     * @param targetType what kind of record
     * @param targetId the record id
     * @param principal the authenticated caller
     * @return the files, in the order the family arranged them
     */
    @GetMapping
    @Operation(summary = "List a record's photos and scans")
    public List<MediaResponse> findByTarget(
            @RequestParam MediaTargetType targetType,
            @RequestParam Long targetId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return mediaService.findByTarget(targetType, targetId, principal.role());
    }

    /**
     * Uploads a photo or scan; restricted to EDITOR and above by the blanket POST rule in SecurityConfig.
     *
     * @param file the uploaded file
     * @param targetType what kind of record it belongs to
     * @param targetId the record id
     * @param kind what the file is, defaults to an ordinary photo
     * @param caption what the family wants written under it, sent as a multipart field
     * @param principal the member uploading it
     * @return the stored file
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a photo or scan")
    public MediaResponse upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam MediaTargetType targetType,
            @RequestParam Long targetId,
            @RequestParam(required = false) MediaKind kind,
            @RequestParam(required = false) @Size(max = MediaUpdateRequest.MAX_CAPTION) String caption,
            @AuthenticationPrincipal AuthPrincipal principal) {
        // The file is handed over as a stream source: getBytes() copied up to 20 MB onto the heap per upload.
        return mediaService.upload(
                targetType, targetId, kind, caption, file, file.getSize(), file.getOriginalFilename(),
                principal.id());
    }

    /**
     * Edits what is recorded about an already-uploaded file.
     *
     * @param id media id
     * @param request the new values
     * @param principal the member making the change
     * @return the updated file
     */
    @PutMapping("/{id}")
    @Operation(summary = "Edit a photo's caption, kind or position")
    public MediaResponse update(
            @PathVariable Long id,
            @Valid @RequestBody MediaUpdateRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return mediaService.update(id, request, principal.id());
    }

    /**
     * Deletes a file; restricted to ADMIN by the blanket DELETE rule in SecurityConfig.
     *
     * @param id media id
     * @param changeNote why the file is being deleted
     * @param principal the member deleting it
     * @return an empty 204 response
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a photo or scan")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestParam(required = false) @Size(max = ChangeNotes.MAX_LENGTH) String changeNote,
            @AuthenticationPrincipal AuthPrincipal principal) {
        mediaService.delete(id, principal.id(), changeNote);
        return ResponseEntity.noContent().build();
    }
}
