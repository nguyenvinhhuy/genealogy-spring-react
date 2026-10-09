package com.genealogy.media.mapper;

import com.genealogy.media.domain.Media;
import com.genealogy.media.dto.response.MediaResponse;
import com.genealogy.media.dto.response.MediaSnapshot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/** Maps media entities to their DTOs. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MediaMapper {

    /**
     * Converts a stored file to its response, with the URLs and the uploader's name the caller resolved.
     *
     * @param media the entity
     * @param url where a browser can fetch the original
     * @param thumbnailUrl where a browser can fetch a small copy
     * @param uploadedByName the uploader's name, or null
     * @return the response DTO
     */
    @Mapping(target = "url", source = "url")
    @Mapping(target = "thumbnailUrl", source = "thumbnailUrl")
    @Mapping(target = "uploadedByName", source = "uploadedByName")
    MediaResponse toResponse(Media media, String url, String thumbnailUrl, String uploadedByName);

    /**
     * Captures what the audit trail keeps of a file, which leaves out its storage keys and URLs.
     *
     * @param media the entity
     * @return its snapshot
     */
    MediaSnapshot toSnapshot(Media media);
}
