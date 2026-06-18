# Quickstart: OSS 批量岗位/角色回查优化

## Local Development Setup

1. Ensure the backend is on branch `001-oss-batch-job-role-sync`.
2. Verify OSS connectivity:
   ```bash
   curl -X POST https://base-oss-test.ehsy.com/oss/admin-web/v1/output/data/getUserJobListByJobNumberList \
     -H "Content-Type: application/json" \
     -d '{"data":["08402","08640"]}'
   ```
3. Run unit tests:
   ```bash
   cd backend
   ./mvnw test -Dtest=JobRoleLookupResolverTest,SystemRoleListMapperTest
   ```
4. Run integration tests (requires Docker for Testcontainers):
   ```bash
   ./mvnw test -Dtest=OrganizationDirectoryHttpGatewayTest
   ```

## Manual Verification

1. Trigger a full organization sync from the admin endpoint or scheduled job.
2. Observe logs for `Batch job/role lookup` entries:
   - `requested=50 returned=50 durationMs=120`
3. Query the local database and verify target-role users have role `sales`.
4. Compare external call count before/after via access logs or metrics.

## Configuration

Add to `application-*.yml`:

```yaml
xiyu:
  integrations:
    organization:
      directory:
        batch-job-role-lookup-path: /oss/admin-web/v1/output/data/getUserJobListByJobNumberList
        batch-query-size: 50
        batch-connect-timeout-ms: 3000
        batch-read-timeout-ms: 10000
```
