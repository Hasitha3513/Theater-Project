CREATE SCHEMA cinenest_rooms;
CREATE TABLE cinenest_rooms.venue (
 venue_id UUID PRIMARY KEY, code VARCHAR(64) NOT NULL UNIQUE CHECK(code ~ '^[A-Z0-9_-]+$'),
 display_name VARCHAR(200) NOT NULL CHECK(length(trim(display_name))>0), timezone TEXT NOT NULL,
 cleaning_buffer_minutes INTEGER CHECK(cleaning_buffer_minutes>=0),
 post_close_cleaning_approved BOOLEAN NOT NULL DEFAULT FALSE, operational_approval_reference TEXT,
 enabled BOOLEAN NOT NULL DEFAULT FALSE, version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK(NOT post_close_cleaning_approved OR length(trim(operational_approval_reference))>0 AND operational_approval_reference IS NOT NULL)
);
CREATE TABLE cinenest_rooms.venue_operating_window (
 venue_id UUID NOT NULL REFERENCES cinenest_rooms.venue, weekday INTEGER NOT NULL CHECK(weekday BETWEEN 1 AND 7),
 opening_minute INTEGER NOT NULL, closing_minute INTEGER NOT NULL,
 PRIMARY KEY(venue_id,weekday), CHECK(0<=opening_minute AND opening_minute<closing_minute AND closing_minute<=1440)
);
CREATE TABLE cinenest_rooms.room (
 room_id UUID PRIMARY KEY, venue_id UUID NOT NULL REFERENCES cinenest_rooms.venue,
 code VARCHAR(64) NOT NULL CHECK(code ~ '^[A-Z0-9_-]+$'), name VARCHAR(200) NOT NULL CHECK(length(trim(name))>0),
 description VARCHAR(2000) NOT NULL DEFAULT '', capacity INTEGER NOT NULL CHECK(capacity>0), enabled BOOLEAN NOT NULL DEFAULT FALSE,
 version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(venue_id,code)
);
CREATE TABLE cinenest_rooms.room_facility (
 room_id UUID NOT NULL REFERENCES cinenest_rooms.room, code VARCHAR(64) NOT NULL CHECK(code ~ '^[A-Z0-9_-]+$'),
 label VARCHAR(300) NOT NULL CHECK(length(trim(label))>0), sort_order INTEGER NOT NULL CHECK(sort_order>=0), PRIMARY KEY(room_id,code)
);
CREATE TABLE cinenest_rooms.room_image (
 image_id UUID PRIMARY KEY, room_id UUID NOT NULL REFERENCES cinenest_rooms.room,
 asset_path TEXT NOT NULL CHECK(asset_path !~ '(^/|\.\.|:|\\)'), alt_text VARCHAR(300) NOT NULL CHECK(length(trim(alt_text))>0),
 sort_order INTEGER NOT NULL CHECK(sort_order>=0), UNIQUE(room_id,asset_path)
);
CREATE TABLE cinenest_rooms.configuration_audit (
 event_id UUID PRIMARY KEY, actor_id UUID, venue_id UUID, resource_type VARCHAR(16) NOT NULL,
 resource_id UUID, action VARCHAR(24) NOT NULL, outcome VARCHAR(8) NOT NULL CHECK(outcome IN ('ALLOW','DENY')),
 old_version BIGINT, new_version BIGINT, changed_fields TEXT NOT NULL,
 occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, correlation_id UUID NOT NULL
);
CREATE INDEX room_venue_idx ON cinenest_rooms.room(venue_id,code,room_id);
CREATE INDEX room_capacity_idx ON cinenest_rooms.room(capacity);
CREATE INDEX facility_code_idx ON cinenest_rooms.room_facility(code,room_id);
CREATE INDEX configuration_audit_venue_idx ON cinenest_rooms.configuration_audit(venue_id,occurred_at,event_id);
