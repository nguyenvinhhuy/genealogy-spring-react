package com.genealogy.book.service;

/** Renders the gia phả as a printable book (docs/analysis.md F14). */
public interface BookService {

    /**
     * Renders the gia phả as a PDF, laid out đời by đời.
     *
     * @param rootPersonId only this person and their descendants, or null for the whole clan
     * @return the PDF bytes
     */
    byte[] render(Long rootPersonId);
}
