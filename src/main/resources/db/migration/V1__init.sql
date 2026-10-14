CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE applications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    company    VARCHAR(200) NOT NULL,
    role       VARCHAR(200) NOT NULL,
    status     VARCHAR(20)  NOT NULL CHECK (status IN ('APPLIED', 'SCREEN', 'INTERVIEW', 'OFFER', 'REJECTED', 'WITHDRAWN')),
    applied_on DATE         NOT NULL,
    url        VARCHAR(2000),
    notes      TEXT,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX applications_user_status ON applications (user_id, status);
CREATE INDEX applications_user_applied ON applications (user_id, applied_on);
