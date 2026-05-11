# Migration to Nuxeo LTS 2025

This document summarizes the changes made to upgrade `nuxeo-advanced-document-audit` from LTS 2023 to LTS 2025. The main driver was the rewrite of the Nuxeo audit subsystem.

## Versions & build

- Parent `nuxeo-parent` → `2025.18.0`
- Plugin → `2025.1.0-SNAPSHOT`
- Target platform → `[2025.0,2026.99)`
- Test deps: `nuxeo-platform-audit-api` replaced by `nuxeo-platform-audit-core` + `nuxeo-platform-audit-test`

## Listener migration (`AdvancedDocumentAuditListener`)

**Why:** legacy `AuditLogger.addLogEntries(...)` reaches the new `AbstractAuditBackend`, which casts entries to the new `org.nuxeo.audit.api.LogEntry` → `ClassCastException` at runtime.

Before:
```java
LogEntry entry = logger.newLogEntry();
entry.setEventId(EVENT_ID);
entry.setDocUUID(doc.getRef());
entry.setExtendedInfos(Map.of(FIELD_NAME, ExtendedInfoImpl.createExtendedInfo(name)));
logger.addLogEntries(List.of(entry));
```

After:
```java
LogEntry entry = LogEntry.builder(EVENT_ID, new Date(event.getTime()))
                         .docUUID(doc.getRef())
                         .extended(FIELD_NAME, name)
                         .build();
auditRouter.routeToBackends(List.of(entry));
```

Side notes: logging switched to Log4j2; inner `Context` is now a `record`.

## New `OSGI-INF/audit-contrib.xml`

**Why:** in LTS 2025 `AuditRouter` only persists entries whose `eventId` matches a configured route — unregistered events are silently dropped (no exception, no log).

```xml
<extension target="org.nuxeo.audit.service.AuditComponent" point="routes">
  <route name="advanced-document-audit">
    <backend name="default" />
    <event name="Property Modification" />
  </route>
</extension>
```

The new file is also declared in `MANIFEST.MF` (`Nuxeo-Component`).

## Test changes

Switched to `org.nuxeo.audit.api.LogEntry`.

**Why:** legacy `getExtendedInfos()` now throws `UnsupportedOperationException` on new entries.

```java
// before
entry.getExtendedInfos().get(FIELD_NAME).getValue(String.class)
// after
entry.getExtended().get(FIELD_NAME)
```

Custom `AuditFeature` now wraps the built-in `org.nuxeo.audit.test.AuditFeature` (in-memory backend by default). `AuditFeature` must precede `AutomationFeature` in `@Features` so `AuditCleanerFeature` initializes before the core session.

## Test resources

- New `pageprovider-contrib.xml` using `org.nuxeo.audit.provider.AuditPageProvider` with NXQL syntax (legacy HQL provider was removed).
- `log4j.xml` → `log4j2.xml`.
- Deleted: `audit-contrib.xml` (test), `jndi.properties`, old `log4j.xml`.

## Package descriptor

- `package.xml` `target-platform` → `[2025.0,2026.99)`.
