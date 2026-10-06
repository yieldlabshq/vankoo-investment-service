CREATE SEQUENCE investment_outbox_sequence
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

CREATE TABLE investment_outbox_events (
    sequence_no NUMBER(19) NOT NULL,
    event_id VARCHAR2(36 CHAR) NOT NULL,
    aggregate_id VARCHAR2(36 CHAR) NOT NULL,
    event_type VARCHAR2(80 CHAR) NOT NULL,
    schema_version NUMBER(10) NOT NULL,
    binding_name VARCHAR2(100 CHAR) NOT NULL,
    payload CLOB NOT NULL,
    status VARCHAR2(20 CHAR) NOT NULL,
    attempt_count NUMBER(10) DEFAULT 0 NOT NULL,
    next_attempt_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP(6) WITH TIME ZONE,
    last_error VARCHAR2(1000 CHAR),
    version NUMBER(19) DEFAULT 0 NOT NULL,
    CONSTRAINT pk_investment_outbox PRIMARY KEY (sequence_no),
    CONSTRAINT uk_investment_outbox_event UNIQUE (event_id),
    CONSTRAINT ck_investment_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED'))
);

CREATE INDEX ix_investment_outbox_pending
    ON investment_outbox_events (status, next_attempt_at, sequence_no);

CREATE INDEX ix_investment_outbox_cleanup
    ON investment_outbox_events (status, published_at);
