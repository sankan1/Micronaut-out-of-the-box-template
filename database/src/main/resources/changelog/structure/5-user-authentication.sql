--liquibase formatted sql

--changeset sander:create-user-authentication
CREATE TABLE system.user_authentication (
    user_authentication_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id                BIGINT NOT NULL,
    auth_method            VARCHAR(20) NOT NULL,
    session_id             UUID NOT NULL,
    access_token           TEXT,
    refresh_token          TEXT,
    token_expiration       TIMESTAMP WITH TIME ZONE,
    session_expiration     TIMESTAMP WITH TIME ZONE NOT NULL,
    last_activity          TIMESTAMP WITH TIME ZONE,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT user_authentication__pkey PRIMARY KEY (user_authentication_id),
    CONSTRAINT user_authentication__user_id__fkey FOREIGN KEY (user_id)
        REFERENCES system.user (user_id) ON DELETE CASCADE,
    CONSTRAINT user_authentication__session_id__uidx UNIQUE (session_id),
    CONSTRAINT user_authentication__user_id__uidx UNIQUE (user_id)
);
--rollback DROP TABLE system.user_authentication;
