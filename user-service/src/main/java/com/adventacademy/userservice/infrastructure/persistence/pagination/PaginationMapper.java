package com.adventacademy.userservice.infrastructure.persistence.pagination;

import com.adventacademy.userservice.domain.exception.InvalidSortFieldException;
import com.adventacademy.userservice.domain.pagination.PageRequest;
import com.adventacademy.userservice.domain.pagination.Sort;
import com.adventacademy.userservice.domain.pagination.SortDirection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;

import java.util.Set;

/**
 * Translates the domain {@link PageRequest} into Spring Data's {@link Pageable},
 * enforcing each adapter's sort-field whitelist so the controller can't push
 * arbitrary entity columns into the ORDER BY clause.
 */
public final class PaginationMapper {

    private PaginationMapper() {
    }

    public static Pageable toSpringPageable(PageRequest req, Set<String> allowedSortFields) {
        return org.springframework.data.domain.PageRequest.of(
                req.page(),
                req.size(),
                toSpringSort(req.sort(), allowedSortFields));
    }

    public static org.springframework.data.domain.Sort toSpringSort(Sort sort, Set<String> allowedSortFields) {
        if (sort.isUnsorted()) {
            return org.springframework.data.domain.Sort.unsorted();
        }
        for (Sort.Order order : sort.orders()) {
            if (!allowedSortFields.contains(order.field())) {
                throw new InvalidSortFieldException(order.field(), allowedSortFields);
            }
        }
        return org.springframework.data.domain.Sort.by(
                sort.orders().stream()
                        .map(o -> new org.springframework.data.domain.Sort.Order(
                                o.direction() == SortDirection.ASC ? Direction.ASC : Direction.DESC,
                                o.field()))
                        .toList());
    }
}
