-- Test-only data. This file is loaded from src/test/resources and is never
-- packaged with the production Jar.

INSERT INTO platform.tenant (id, status)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000002', 'ACTIVE');

INSERT INTO platform.external_identity (id, provider, external_subject)
VALUES
    ('00000000-0000-0000-0000-000000000101', 'SUPABASE', 'test-subject-a'),
    ('00000000-0000-0000-0000-000000000102', 'SUPABASE', 'test-subject-b');

INSERT INTO platform.membership (id, identity_id, tenant_id, status, role)
VALUES
    (
        '00000000-0000-0000-0000-000000000201',
        '00000000-0000-0000-0000-000000000101',
        '00000000-0000-0000-0000-000000000001',
        'ACTIVE',
        'TENANT_USER'
    ),
    (
        '00000000-0000-0000-0000-000000000202',
        '00000000-0000-0000-0000-000000000102',
        '00000000-0000-0000-0000-000000000002',
        'ACTIVE',
        'TENANT_USER'
    );

INSERT INTO operations.tenant_settings (tenant_id)
VALUES
    ('00000000-0000-0000-0000-000000000001'),
    ('00000000-0000-0000-0000-000000000002');
