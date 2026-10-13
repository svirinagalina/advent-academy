package com.adventacademy.userservice.infrastructure.workflow;

import com.adventacademy.userservice.domain.exception.InvalidRoleRequestStateException;
import com.adventacademy.userservice.domain.exception.RoleNotFoundException;
import com.adventacademy.userservice.domain.exception.RoleRequestNotFoundException;
import com.adventacademy.userservice.domain.exception.UserNotFoundException;

/**
 * Classifies a job worker failure as permanent (a data/state problem that will
 * never succeed on retry) or transient (worth Zeebe's normal retry budget).
 * <p>
 * A missing user/role/role-request, a role request that is no longer in the
 * state the worker expected, or job variables that are missing/malformed to
 * begin with, cannot be fixed by trying again with the same input — retrying
 * those just delays the inevitable {@code FAILED} outcome and produces
 * misleading retry-count noise in Operate. Everything else (a transient DB
 * hiccup, for example) keeps the existing decrement-then-terminal behavior.
 */
final class PermanentWorkflowFailure {

    private PermanentWorkflowFailure() {
    }

    static boolean isPermanent(Exception e) {
        return e instanceof UserNotFoundException
                || e instanceof RoleNotFoundException
                || e instanceof RoleRequestNotFoundException
                || e instanceof InvalidRoleRequestStateException
                || e instanceof IllegalArgumentException
                || e instanceof NullPointerException;
    }
}
