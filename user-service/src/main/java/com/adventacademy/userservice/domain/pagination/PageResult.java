package com.adventacademy.userservice.domain.pagination;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Domain-side page result — items plus the metadata required to render
 * pagination on the client (totals, has-next / has-previous).
 */
public final class PageResult<T> {

    private final List<T> items;
    private final int page;
    private final int size;
    private final long totalElements;

    public PageResult(List<T> items, int page, int size, long totalElements) {
        this.items = List.copyOf(items);
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
    }

    public List<T> items() {
        return items;
    }

    public int page() {
        return page;
    }

    public int size() {
        return size;
    }

    public long totalElements() {
        return totalElements;
    }

    public int totalPages() {
        if (size == 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalElements / (double) size);
    }

    public boolean hasNext() {
        return page + 1 < totalPages();
    }

    public boolean hasPrevious() {
        return page > 0;
    }

    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        List<R> mappedItems = items.stream()
                .<R>map(mapper)
                .collect(Collectors.toList());

        return new PageResult<>(mappedItems, page, size, totalElements);
    }
}
