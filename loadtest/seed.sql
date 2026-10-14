-- Gives the load-test user (register it through POST /auth/register first) 50,000
-- applications spread over two years, so /applications/stats has real work to do.
DELETE FROM applications WHERE user_id = (SELECT id FROM users WHERE email = 'loadtest@example.com');
INSERT INTO applications (user_id, company, role, status, applied_on)
SELECT (SELECT id FROM users WHERE email = 'loadtest@example.com'),
       'Company ' || (g % 5000),
       'Software Engineer',
       (ARRAY['APPLIED','APPLIED','APPLIED','SCREEN','INTERVIEW','OFFER','REJECTED','REJECTED','WITHDRAWN'])[1 + g % 9],
       CURRENT_DATE - (g % 730)
FROM generate_series(1, 50000) AS g;
ANALYZE applications;
