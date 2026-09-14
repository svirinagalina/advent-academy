package com.adventacademy.userservice.infrastructure.persistence.id;

import org.hibernate.annotations.IdGeneratorType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * UUID primary key that keeps an identifier assigned by the domain model and only generates one
 * when none is set. The domain creates aggregates with their id ({@code new User()} picks a
 * random UUID), while test fixtures and legacy code still build entities without one.
 */
@IdGeneratorType(AssignedOrRandomUuidGenerator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface AssignedOrRandomUuid {
}
