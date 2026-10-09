package com.genealogy.event.controller;

import com.genealogy.common.model.EventSubjectType;
import com.genealogy.common.security.AuthPrincipal;
import com.genealogy.common.util.ChangeNotes;
import com.genealogy.common.util.VietnamTime;
import com.genealogy.event.dto.request.EventRequest;
import com.genealogy.event.dto.response.AnniversaryResponse;
import com.genealogy.event.dto.response.EventResponse;
import com.genealogy.event.service.EventService;
import com.genealogy.purge.service.PurgeService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for life and union events. */
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Validated
public class EventController {

    private static final String DEFAULT_WINDOW_DAYS = "60";

    private final EventService eventService;
    private final PurgeService purgeService;

    /**
     * Lists the events of one subject, oldest first, hiding a living person's from callers below EDITOR.
     *
     * @param subjectType whether the subject is a person or a union
     * @param subjectId the subject id
     * @param principal the authenticated caller
     * @return the events
     */
    @GetMapping
    @Operation(summary = "List a subject's events")
    public List<EventResponse> findBySubject(
            @RequestParam EventSubjectType subjectType,
            @RequestParam Long subjectId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return eventService.findBySubject(subjectType, subjectId, principal.role());
    }

    /**
     * Lists the ngày giỗ falling within the next stretch of days.
     *
     * @param from the first day to consider, defaults to today
     * @param days how many days ahead to look
     * @return the upcoming anniversaries, soonest first
     */
    @GetMapping("/anniversaries")
    @Operation(summary = "List upcoming ngày giỗ")
    public List<AnniversaryResponse> upcomingAnniversaries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(defaultValue = DEFAULT_WINDOW_DAYS) @Min(1) @Max(400) int days) {
        return eventService.upcomingAnniversaries(from == null ? VietnamTime.today() : from, days);
    }

    /**
     * Returns one event.
     *
     * @param id event id
     * @param principal the authenticated caller
     * @return the event
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get one event")
    public EventResponse getById(
            @PathVariable Long id, @AuthenticationPrincipal AuthPrincipal principal) {
        return eventService.getById(id, principal.role());
    }

    /**
     * Creates an event.
     *
     * @param request the event to create
     * @param principal the authenticated caller
     * @return the created event
     */
    @PostMapping
    @Operation(summary = "Create an event")
    public ResponseEntity<EventResponse> create(
            @Valid @RequestBody EventRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.create(request, principal.id()));
    }

    /**
     * Updates an event.
     *
     * @param id event id
     * @param request the new values
     * @param principal the authenticated caller
     * @return the updated event
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update an event")
    public EventResponse update(
            @PathVariable Long id,
            @Valid @RequestBody EventRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return eventService.update(id, request, principal.id());
    }

    /**
     * Deletes an event.
     *
     * @param id event id
     * @param changeNote why the event is being deleted, for the change history
     * @param principal the authenticated caller
     * @return an empty 204 response
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an event")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestParam(required = false) @Size(max = ChangeNotes.MAX_LENGTH) String changeNote,
            @AuthenticationPrincipal AuthPrincipal principal) {
        // Through purge, not eventService.delete: a citation names an event by id with no foreign key.
        purgeService.purgeEvent(id, principal.id(), changeNote);
        return ResponseEntity.noContent().build();
    }
}
