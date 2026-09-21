-- Test-only identity data. This file is never packaged in the production Jar.

INSERT INTO platform.external_identity (id, provider, external_subject, status)
VALUES
    ('00000000-0000-0000-0000-000000000103', 'SUPABASE', 'blocked-subject', 'BLOCKED');

INSERT INTO platform.membership (id, identity_id, tenant_id, status, role, revoked_at)
VALUES
    (
        '00000000-0000-0000-0000-000000000203',
        '00000000-0000-0000-0000-000000000103',
        '00000000-0000-0000-0000-000000000001',
        'REVOKED',
        'TENANT_USER',
        CURRENT_TIMESTAMP
    ),
    (
        '00000000-0000-0000-0000-000000000204',
        '00000000-0000-0000-0000-000000000103',
        '00000000-0000-0000-0000-000000000001',
        'PENDING',
        'TENANT_USER',
        NULL
    );
