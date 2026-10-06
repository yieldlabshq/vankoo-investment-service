CREATE TABLE auction_marketplace_views (
    auction_id VARCHAR2(36 CHAR) NOT NULL,
    invoice_id VARCHAR2(36 CHAR) NOT NULL,
    mype_id VARCHAR2(36 CHAR) NOT NULL,
    payer_ruc VARCHAR2(20 CHAR) NOT NULL,
    payer_name VARCHAR2(200 CHAR) NOT NULL,
    due_date DATE NOT NULL,
    invoice_amount NUMBER(19, 2) NOT NULL,
    fundable_amount NUMBER(19, 2),
    target_amount NUMBER(19, 2),
    current_funding NUMBER(19, 2) NOT NULL,
    currency VARCHAR2(3 CHAR) NOT NULL,
    investor_tea NUMBER(19, 12),
    investor_term_rate NUMBER(19, 12),
    quoted_term_days NUMBER(10),
    risk_grade VARCHAR2(20 CHAR),
    status VARCHAR2(40 CHAR) NOT NULL,
    green_certified BOOLEAN NOT NULL,
    marketplace_visible BOOLEAN NOT NULL,
    published_at TIMESTAMP(6) WITH TIME ZONE,
    expires_at TIMESTAMP(6) WITH TIME ZONE,
    last_event_sequence NUMBER(19) NOT NULL,
    last_event_id VARCHAR2(36 CHAR) NOT NULL,
    projected_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version NUMBER(19) NOT NULL,
    CONSTRAINT pk_auction_marketplace_views PRIMARY KEY (auction_id),
    CONSTRAINT ck_market_view_currency CHECK (currency IN ('PEN', 'USD')),
    CONSTRAINT ck_market_view_risk CHECK (
        risk_grade IS NULL OR risk_grade IN ('A', 'B', 'C', 'UNDER_EVALUATION')
    ),
    CONSTRAINT ck_market_view_status CHECK (
        status IN ('PENDING_VERIFICATION_RISK', 'DRAFT', 'PUBLISHED', 'FUNDING', 'FULLY_FUNDED', 'CLOSED', 'EXPIRED', 'CANCELLED')
    )
);

CREATE TABLE marketplace_processed_events (
    event_id VARCHAR2(36 CHAR) NOT NULL,
    aggregate_id VARCHAR2(36 CHAR) NOT NULL,
    sequence_no NUMBER(19) NOT NULL,
    event_type VARCHAR2(80 CHAR) NOT NULL,
    schema_version NUMBER(10) NOT NULL,
    occurred_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    payload CLOB NOT NULL,
    status VARCHAR2(20 CHAR) NOT NULL,
    received_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    applied_at TIMESTAMP(6) WITH TIME ZONE,
    deferred_reason VARCHAR2(500 CHAR),
    version NUMBER(19) NOT NULL,
    CONSTRAINT pk_marketplace_processed PRIMARY KEY (event_id),
    CONSTRAINT ck_market_processed_status CHECK (status IN ('RECEIVED', 'DEFERRED', 'APPLIED'))
);

CREATE INDEX ix_market_view_search ON auction_marketplace_views (
    status, currency, green_certified, expires_at, auction_id
);

CREATE INDEX ix_market_inbox_deferred ON marketplace_processed_events (
    aggregate_id, status, sequence_no
);
