# Quickstart: OSS Menu Permission Sync

## Configuration

Add to `application.yml` / environment:

```yaml
xiyu:
  integration:
    organization:
      directory:
        user-menu-tree-path: /sysMenuUrl/getUserMenuTree
        user-menu-tree-system-name: xiyu-bid-poc
        user-menu-tree-retrieval-type: 2
        user-menu-tree-connect-timeout-ms: 3000
        user-menu-tree-read-timeout-ms: 5000
        auto-sync-menu-permissions: false
        menu-code-to-permission-key-mappings:
          projectmanager: project.manager
          bidding: bidding
          dashboard: dashboard
```

## Manual Sync

```bash
curl -X POST https://localhost:8080/api/admin/roles/42/sync-oss-menu-permissions \
  -H "Content-Type: application/json" \
  -d '{"jobNumber":"08402"}'
```

## Enable Auto Sync

Set `xiyu.integration.organization.directory.auto-sync-menu-permissions=true` and run organization sync.
