/*
 * (C) Copyright 2015-2025 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Michael Vachette
 */

package org.nuxeo.audit.advanced;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.audit.api.LogEntry;
import org.nuxeo.audit.api.LogEntryBuilder;
import org.nuxeo.audit.service.AuditRouter;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.impl.ArrayProperty;
import org.nuxeo.ecm.core.api.model.impl.primitives.BlobProperty;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.runtime.api.Framework;

/**
 * Listener that records, in the audit, every modification of a non-system document property.
 * <p>
 * Migrated to the new {@code org.nuxeo.audit.api.LogEntry} / {@code AuditRouter} API in LTS 2025.
 *
 * @since 2025.1
 */
public class AdvancedDocumentAuditListener implements EventListener {

    private static final Logger log = LogManager.getLogger(AdvancedDocumentAuditListener.class);

    private static final List<String> SYSTEM_PROPS = Arrays.asList("dc:created", "dc:creator", "dc:modified",
            "dc:contributors");

    public static final String EVENT_ID = "Property Modification";

    public static final String CATEGORY = "Document";

    public static final String FIELD_NAME = "fieldname";

    public static final String OLD_VALUE = "oldValue";

    public static final String NEW_VALUE = "newValue";

    public static final String EMPTY_VALUE = "EMPTY";

    @Override
    public void handleEvent(Event event) {
        EventContext ectx = event.getContext();
        if (!(ectx instanceof DocumentEventContext docCtx)) {
            return;
        }

        AuditRouter router = Framework.getService(AuditRouter.class);
        if (router == null) {
            log.error("No AuditRouter implementation is available");
            return;
        }

        DocumentModel newDoc = docCtx.getSourceDocument();
        DocumentModel oldDoc = newDoc.getCoreSession().getDocument(newDoc.getRef());

        Context context = new Context(newDoc, oldDoc, event, router);
        processDocument(context);
    }

    protected void processDocument(Context context) {
        List<LogEntry> entries = new ArrayList<>();

        String[] schemas = context.newDoc().getSchemas();
        for (String schema : schemas) {
            Collection<Property> properties = context.newDoc().getPropertyObjects(schema);
            for (Property property : properties) {
                String fieldName = property.getName();
                // skip system properties
                if (SYSTEM_PROPS.contains(fieldName)) {
                    continue;
                }

                if (property.isDirty()) {
                    Property oldProperty = context.oldDoc().getProperty(fieldName);
                    entries.addAll(processProperty(context, oldProperty, property));
                }
            }
        }

        if (!entries.isEmpty()) {
            context.router().routeToBackends(entries);
        }
    }

    protected List<LogEntry> processProperty(Context context, Property oldProperty, Property newProperty) {

        List<LogEntry> entries = new ArrayList<>();

        // Handle Scalar Properties
        if (oldProperty.isScalar()) {
            entries.add(processScalarProperty(context, oldProperty, newProperty));
        }

        // Handle Complex Properties
        if (oldProperty.isComplex() && !oldProperty.isList()) {
            if (oldProperty instanceof BlobProperty) {
                entries.add(processBlobProperty(context, oldProperty, newProperty));
            } else {
                entries.addAll(processComplexProperty(context, oldProperty, newProperty));
            }
        }

        // Handle Scalar List Properties
        if (oldProperty instanceof ArrayProperty) {
            entries.addAll(
                    processScalarList(context, oldProperty.getXPath(), oldProperty.getValue(), newProperty.getValue()));
        }
        return entries;
    }

    protected LogEntry processScalarProperty(Context context, Property oldProperty, Property newProperty) {
        return getEntry(context, oldProperty.getXPath(), oldProperty.getValue(), newProperty.getValue());
    }

    protected List<LogEntry> processScalarList(Context context, String fieldName, Object oldValue, Object newValue) {
        List<LogEntry> entries = new ArrayList<>();

        List<Serializable> oldList = null;
        if (oldValue != null) {
            oldList = Arrays.asList((Serializable[]) oldValue);
        }
        List<Serializable> newList = Arrays.asList((Serializable[]) newValue);

        String normalized = normalizeFieldName(fieldName);

        // get Added Values
        List<Serializable> added = new ArrayList<>(newList);
        if (oldList != null) {
            added.removeAll(oldList);
        }
        for (Serializable addedValue : added) {
            LogEntryBuilder builder = newEntryBuilder(context, normalized, null, addedValue);
            builder.comment(normalized + " : Added " + formatPropertyValue(addedValue));
            entries.add(builder.build());
        }

        // get Removed Value
        if (oldList != null) {
            List<Serializable> removed = new ArrayList<>(oldList);
            removed.removeAll(newList);
            for (Serializable removedValue : removed) {
                LogEntryBuilder builder = newEntryBuilder(context, normalized, removedValue, null);
                builder.comment(normalized + " : Removed " + formatPropertyValue(removedValue));
                entries.add(builder.build());
            }
        }

        return entries;
    }

    protected List<LogEntry> processComplexProperty(Context context, Property oldProperty, Property newProperty) {
        List<LogEntry> entries = new ArrayList<>();
        Iterator<Property> dirtyProperties = newProperty.getDirtyChildren();
        while (dirtyProperties.hasNext()) {
            Property dirtyProperty = dirtyProperties.next();
            entries.addAll(processProperty(context, oldProperty.get(dirtyProperty.getName()), dirtyProperty));
        }
        return entries;
    }

    protected LogEntry processBlobProperty(Context context, Property oldProperty, Property newProperty) {
        Blob oldBlob = (Blob) oldProperty.getValue();
        String oldFilename = oldBlob != null ? oldBlob.getFilename() : null;
        Blob newBlob = (Blob) newProperty.getValue();
        String newFilename = newBlob != null ? newBlob.getFilename() : null;
        return getEntry(context, oldProperty.getXPath(), oldFilename, newFilename);
    }

    protected LogEntry getEntry(Context context, String fieldName, Serializable oldValue, Serializable newValue) {
        String normalized = normalizeFieldName(fieldName);
        LogEntryBuilder builder = newEntryBuilder(context, normalized, oldValue, newValue);
        String formattedOld = formatPropertyValue(oldValue);
        String formattedNew = formatPropertyValue(newValue);
        builder.comment(normalized + " : " + formattedOld + " -> " + formattedNew);
        return builder.build();
    }

    protected LogEntryBuilder newEntryBuilder(Context context, String fieldName, Serializable oldValue,
            Serializable newValue) {
        Event event = context.event();
        DocumentModel doc = context.newDoc();

        return LogEntry.builder(EVENT_ID, new Date(event.getTime()))
                       .category(CATEGORY)
                       .docUUID(doc.getRef())
                       .docPath(doc.getPathAsString())
                       .docType(doc.getType())
                       .docLifeCycle(doc.getCurrentLifeCycleState())
                       .principalName(doc.getCoreSession().getPrincipal().getName())
                       .repositoryId(doc.getRepositoryName())
                       .extended(FIELD_NAME, fieldName)
                       .extended(OLD_VALUE, formatPropertyValue(oldValue))
                       .extended(NEW_VALUE, formatPropertyValue(newValue));
    }

    protected String formatPropertyValue(Serializable value) {
        if (value instanceof Calendar calendar) {
            String pattern = "MM/dd/yyyy";
            SimpleDateFormat format = new SimpleDateFormat(pattern);
            return format.format(calendar.getTime());
        } else if (value != null) {
            return value.toString();
        } else {
            return EMPTY_VALUE;
        }
    }

    protected String normalizeFieldName(String fieldName) {
        if (fieldName.startsWith("/")) {
            return fieldName.substring(1);
        } else {
            return fieldName;
        }
    }

    /**
     * Per-event processing context.
     *
     * @since 2025.1
     */
    protected record Context(DocumentModel newDoc, DocumentModel oldDoc, Event event, AuditRouter router) {
    }

}
