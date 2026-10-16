package com.adventacademy.userservice.application.service.role;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The committed role request itself is the durable queue. A lease is acquired before any network I/O. */
@Service
public class WorkflowStartLease {
    private final JdbcTemplate jdbc;
    public WorkflowStartLease(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public boolean claim(UUID id) {
        return jdbc.update("UPDATE role_requests SET workflow_next_attempt_at=CURRENT_TIMESTAMP+INTERVAL '60 seconds', "
                + "workflow_start_attempts=workflow_start_attempts+1 WHERE id=? AND status='PENDING' "
                + "AND process_instance_key IS NULL AND workflow_next_attempt_at<=CURRENT_TIMESTAMP", id) == 1;
    }

    @Transactional
    public boolean bind(UUID id, long instance) {
        var rows = jdbc.queryForList("SELECT status,process_instance_key FROM role_requests WHERE id=? FOR UPDATE", id);
        if (rows.isEmpty() || !"PENDING".equals(rows.getFirst().get("status"))) { return false; }
        Object current = rows.getFirst().get("process_instance_key");
        if (current != null) { return ((Number) current).longValue() == instance; }
        jdbc.update("UPDATE role_requests SET process_instance_key=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", instance, id);
        return true;
    }
}
