package com.adventacademy.contentservice.support;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Runs the annotated test as an authenticated content editor.
 * <p>
 * Integration tests drive the API through MockMvc without a real token, so the security context is
 * populated directly instead. The principal is a UUID string, which is what
 * {@code SecurityContextCurrentUserProvider} expects when it resolves the owner of created content.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithContentEditorSecurityContextFactory.class)
public @interface WithContentEditor {

    /** Id of the acting user; it also becomes the owner of anything the test creates. */
    String userId() default "11111111-1111-1111-1111-111111111111";

    /** Roles granted to the acting user, without the {@code ROLE_} prefix. */
    String[] roles() default {"CONTENT_EDITOR"};
}
