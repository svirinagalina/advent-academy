package com.adventacademy.userservice.domain.pagination;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Domain-side sort specification. Decoupled from Spring Data so the
 * application layer can request ordering without leaking infrastructure types.
 */
public final class Sort {

    public static final Sort UNSORTED = new Sort(Collections.emptyList());

    private final List<Order> orders;

    private Sort(List<Order> orders) {
        this.orders = List.copyOf(orders);
    }

    public static Sort by(Order... orders) {
        return new Sort(List.of(orders));
    }

    public static Sort by(List<Order> orders) {
        return new Sort(orders);
    }

    public List<Order> orders() {
        return orders;
    }

    public boolean isUnsorted() {
        return orders.isEmpty();
    }

    public record Order(String field, SortDirection direction) {
        public Order {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(direction, "direction");
        }

        public static Order asc(String field) {
            return new Order(field, SortDirection.ASC);
        }

        public static Order desc(String field) {
            return new Order(field, SortDirection.DESC);
        }
    }
}
