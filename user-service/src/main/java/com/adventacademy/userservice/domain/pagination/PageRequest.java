package com.adventacademy.userservice.domain.pagination;

import java.util.Objects;

/**
 * Domain-side page request — captures the slice (page index, size) and the
 * ordering. Constructed from validated controller input; the infrastructure
 * adapter is responsible for translating it into the concrete pagination
 * primitives of its persistence technology.
 */
public final class PageRequest {

    private final int page;
    private final int size;
    private final Sort sort;

    public PageRequest(int page, int size, Sort sort) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be >= 1");
        }
        this.page = page;
        this.size = size;
        this.sort = Objects.requireNonNull(sort, "sort");
    }

    public static PageRequest of(int page, int size, Sort sort) {
        return new PageRequest(page, size, sort);
    }

    public static PageRequest of(int page, int size) {
        return new PageRequest(page, size, Sort.UNSORTED);
    }

    public int page() {
        return page;
    }

    public int size() {
        return size;
    }

    public Sort sort() {
        return sort;
    }
}
