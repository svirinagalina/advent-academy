CREATE TABLE scheduled_meetings (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL,
    course_title VARCHAR(255) NOT NULL,
    host_id UUID NOT NULL,
    request_id UUID NOT NULL,
    creation_hash VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    state VARCHAR(20) NOT NULL CHECK (state IN ('PLANNED','ACTIVE','FINISHED','CANCELLED','EXPIRED')),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(host_id,request_id),
    CHECK(ends_at > starts_at)
);
CREATE TABLE scheduled_participants (
    meeting_id UUID NOT NULL REFERENCES scheduled_meetings(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    PRIMARY KEY(meeting_id,user_id)
);
CREATE INDEX idx_scheduled_time ON scheduled_meetings(starts_at,id);
CREATE INDEX idx_scheduled_participant ON scheduled_participants(user_id,meeting_id);
