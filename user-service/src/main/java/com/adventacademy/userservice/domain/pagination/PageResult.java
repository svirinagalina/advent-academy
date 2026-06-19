package com.adventacademy.userservice.domain.pagination;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.function.Function;

/**
 * Domain-side page result — items plus the metadata required to render
 * pagination on the client (totals, has-next / has-previous).
 *
 * <p>Modelled as a record so Jackson has standard accessors to serialize;
 * the derived pagination metadata is exposed explicitly via {@link JsonProperty}.
 */
public record PageResult<T>(List<T> items, int page, int size, long totalElements) {

    public PageResult {
        items = List.copyOf(items);
    }

    @JsonProperty("totalPages")
    public int totalPages() {
        if (size == 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalElements / (double) size);
    }

    @JsonProperty("hasNext")
    public boolean hasNext() {
        return page + 1 < totalPages();
    }

    @JsonProperty("hasPrevious")
    public boolean hasPrevious() {
        return page > 0;
    }

    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        List<R> mappedItems = items.stream()
                .<R>map(mapper)
                .toList();

        return new PageResult<>(mappedItems, page, size, totalElements);
    }
}
