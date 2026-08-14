package com.adventacademy.contentservice.adapter.rest.exception;

import java.util.Set;

/**
 * 422 body for lesson Markdown that references assets which do not exist,
 * belong to a different lesson, or are not valid asset identifiers.
 * <p>
 * Each entry is the identifier part of the offending {@code asset:<id>} link,
 * so the author can locate it in the source Markdown.
 */
public record AssetLinkErrorResponse(String message, Set<String> invalidAssetIds) {
}
