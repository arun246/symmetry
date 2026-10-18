package io.symmetry.contract;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * A stable, envelope-shaped page of results. Returning this instead of Spring
 * Data's {@code PageImpl} avoids the "serializing PageImpl is unstable" warning and
 * keeps the JSON contract explicit for the UI.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
