package com.cinebook.repository.specification;

import com.cinebook.entity.Cinema;
import com.cinebook.enums.CinemaStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class CinemaSpecification {

    private CinemaSpecification() {
    }

    public static Specification<Cinema> isNotDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Cinema> hasCity(String city) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(city)) {
                return null;
            }
            return cb.equal(cb.lower(root.get("city")), city.trim().toLowerCase());
        };
    }

    public static Specification<Cinema> hasStatus(CinemaStatus status) {
        return (root, query, cb) -> {
            if (status == null) {
                return null;
            }
            return cb.equal(root.get("status"), status);
        };
    }

    public static Specification<Cinema> searchKeyword(String keyword) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) {
                return null;
            }
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("address")), pattern),
                    cb.like(cb.lower(root.get("city")), pattern)
            );
        };
    }
}