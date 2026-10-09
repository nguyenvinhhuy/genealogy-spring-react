package com.genealogy.person.controller;

import com.genealogy.common.security.AuthPrincipal;
import com.genealogy.common.util.ChangeNotes;
import com.genealogy.person.dto.request.PersonRequest;
import com.genealogy.person.dto.response.PersonDetailResponse;
import com.genealogy.person.dto.response.PersonNodeResponse;
import com.genealogy.person.dto.response.PersonView;
import com.genealogy.person.service.PersonService;
import com.genealogy.purge.service.PurgeService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for persons recorded in the gia phả; listing and searching live at `/api/v1/search/persons`. */
@RestController
@RequestMapping("/api/v1/persons")
@RequiredArgsConstructor
@Validated
public class PersonController {

    // A page links a few dozen people at most; the cap stops one request pulling the whole clan's names.
    static final int MAX_NODES = 200;

    private final PersonService personService;
    private final PurgeService purgeService;

    /**
     * Returns one person, redacted when the caller may not see a living person's details (§3.6).
     *
     * @param id person id
     * @param principal the authenticated caller
     * @return the full person, or the redacted view
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get one person")
    public PersonView getById(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal principal) {
        return personService.getById(id, principal.role());
    }

    /**
     * Returns the name, sex, đời and living flag of several people at once, for pages that link to many.
     *
     * @param ids the people to look up, at most {@value #MAX_NODES}
     * @return one node per person found, in no particular order
     */
    // Only fields a redacted person also carries (§3.6), so it is safe for every role, as the tree already is.
    @GetMapping("/nodes")
    @Operation(summary = "Get several people as name-and-đời nodes")
    public List<PersonNodeResponse> findNodes(@RequestParam @Size(max = MAX_NODES) List<Long> ids) {
        return personService.findNodes(ids);
    }

    /**
     * Creates a person together with their names.
     *
     * @param request the person to create
     * @param principal the authenticated caller
     * @return the created person
     */
    @PostMapping
    @Operation(summary = "Create a person")
    public ResponseEntity<PersonDetailResponse> create(
            @Valid @RequestBody PersonRequest request, @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(personService.create(request, principal.id()));
    }

    /**
     * Replaces a person's fields and name list.
     *
     * @param id person id
     * @param request the new values
     * @param principal the authenticated caller
     * @return the updated person
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update a person")
    public PersonDetailResponse update(
            @PathVariable Long id,
            @Valid @RequestBody PersonRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return personService.update(id, request, principal.id());
    }

    /**
     * Deletes a person together with every row that names them.
     *
     * @param id person id
     * @param changeNote why the person is being deleted, for the change history
     * @param principal the authenticated caller
     * @return an empty 204 response
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a person")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestParam(required = false) @Size(max = ChangeNotes.MAX_LENGTH) String changeNote,
            @AuthenticationPrincipal AuthPrincipal principal) {
        // Through `purge`, not `personService.delete`: four tables name a person with no FK to cascade.
        purgeService.purgePerson(id, principal.id(), changeNote);
        return ResponseEntity.noContent().build();
    }
}
