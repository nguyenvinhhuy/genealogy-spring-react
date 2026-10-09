package com.genealogy.common.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Turns a client's paging request into one the server controls: a capped size and a fixed order. */
// Four services each copied this cap; a client sort reached SQL raw and an unknown field was a 500 (§8.9 #11).
public final class PageRequests {

    // The largest page any list endpoint hands out.
    private static final int MAX_PAGE_SIZE = 100;

    /** Not instantiable. */
    private PageRequests() {
    }

    /**
     * Keeps the client's page number and size, capped, and drops any sort it asked for.
     *
     * @param pageable what the client asked for
     * @return the page to fetch, unsorted, for a query that orders itself
     */
    public static Pageable capped(Pageable pageable) {
        return capped(pageable, Sort.unsorted());
    }

    /**
     * Keeps the client's page number and size, capped, and replaces any sort with the one given.
     *
     * @param pageable what the client asked for
     * @param order the only order the endpoint serves
     * @return the page to fetch
     */
    public static Pageable capped(Pageable pageable, Sort order) {
        // Unpaged means "everything", which is exactly what the cap exists to refuse; it reads as the first page.
        if (pageable.isUnpaged()) {
            return PageRequest.of(0, MAX_PAGE_SIZE, order);
        }
        return PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE), order);
    }
}
