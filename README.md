nuxeo-advanced-document-audit
=============================


## List of Features (details below)

This plugin provides advanced document audit capabilities to the Nuxeo Platform.

It outputs old/new values for the fields modified.

It is provided as an example, a source of inspiration showing how to add custom entries to the audit log, don't hesitate to fork and tune, depending on your exact needs.

## LTS 2025 Compatibility

This plugin targets Nuxeo LTS 2025 (`2025.1.0-SNAPSHOT`, target platform `[2025.0,2026.99)`).

The Nuxeo audit subsystem was rewritten in LTS 2025; this plugin was migrated to the new `AuditRouter` / `LogEntry.builder()` API. See [Migration-to-LTS_2025.md](./Migration-to-LTS_2025.md) for details.

## Audit Entries

Entries written by this plugin use:

- `eventId = "propertyModification"`
- `category = DocumentEventCategories.EVENT_DOCUMENT_CATEGORY`, which is Nuxeo's default document audit category.

Reusing the default category means the entries aredisplayed and filterable in the Web UI audit table out of the box:

- Web UI's `nuxeo-audit-search` resolves the column label via
  `i18n('eventCategory.' + entry.category)`. The shipped i18n bundles already define `eventCategory.eventDocumentCategory => "Document"` (and translations for every locale), so no custom `messages*.json` contribution is needed for the category.
- The category filter dropdown is fed from the `eventCategories` vocabulary, which seeds `eventDocumentCategory` by default — our entries appear under the existing "Document" filter without any extra directory contribution.

### Web UI translation of the event id

The plugin provides the 2 files expected for translation in the UI, see messages.json and messages.properties in the `resources/web/nuxeo.war` folder.

### Registering the eventId in the "Performed Actions" filter

Shipping the translations is not enough — for the eventId to **appear** in the audit filter dropdown at all, it must exist as a row in the `eventTypes` vocabulary. But:

* Duplicating, in Studio, the eventTpes vocabulary in Studio to override it is a bit risky: If other components add their own entries, they will be lost
* Automatically adding the entry when Nuxeo starts requires what we think is overkill code for this purpose (create a Nuxeo Component, register it, register code for the "afterStart" event, etc.)

So, we suggest this simple approach: A one-shot install step via a single REST call:

```bash
curl -s -u USER:PASSWORD -X POST -H 'Content-Type:application/json' \
    "SERVER/nuxeo/api/v1/directory/eventTypes" \
    -d '{
       "entity-type": "directoryEntry",
       "directoryName": "eventTypes",
       "properties": {
         "id": "propertyModification",
         "label": "Property Modification"
       }
     }'
```

> [!Note]
> Use an Administrator account (the `eventTypes` directory is a system directory).


## Local Build & Deploy / Marketplace Package Deployment

### Build and Deploy Locally

```bash
git clone https://github.com/nuxeo-sandbox/nuxeo-advanced-document-audit
cd nuxeo-advanced-document-audit
mvn clean install
```

To skip unit testing, add `-DskipTests`.

The Marketplace package is generated at:
```
nuxeo-advanced-document-audit-package/target/nuxeo-advanced-document-audit-package-{VERSION}}-*.zip
```

Install it via `nuxeoctl`:
```bash
nuxeoctl mp-install nuxeo-advanced-document-audit-package-{VERSION}.zip
```

### Deploy from Nuxeo Marketplace

This plugin is available as a package on the [Nuxeo Marketplace](https://connect.nuxeo.com/nuxeo/site/marketplace/package/nuxeo-advanced-document-audit), you can just:

```bash
nuxeoctl mp-install nuxeo-advanced-document
```



# Support

**These features are not part of the Nuxeo Production platform.**

These solutions are provided for inspiration and we encourage customers to use them as code samples and learning resources.

This is a moving project (no API maintenance, no deprecation process, etc.) If any of these solutions are found to be useful for the Nuxeo Platform in general, they will be integrated directly into platform, not maintained here.

# License

[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

# About Nuxeo

Nuxeo Platform is an open source highly scalable, cloud-native, enterprise content management product with rich multimedia support, written in Java. Data can be stored in both SQL & NoSQL databases.

The development of the Nuxeo Platform is mostly done by Nuxeo employees with an open development model.

The source code, documentation, roadmap, issue tracker, testing, benchmarks are all public.

More information is available at [Hyland/Nuxeo](https://www.hyland.com/en/solutions/products/nuxeo-platform).
