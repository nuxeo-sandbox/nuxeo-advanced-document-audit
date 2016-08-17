/*
 * (C) Copyright 2015-2016 Nuxeo SA (http://nuxeo.com/) and others.
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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DataModel;
import org.nuxeo.ecm.core.api.DataModelMap;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.impl.ArrayProperty;
import org.nuxeo.ecm.core.api.model.impl.primitives.BlobProperty;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.audit.api.AuditLogger;
import org.nuxeo.ecm.platform.audit.api.ExtendedInfo;
import org.nuxeo.ecm.platform.audit.api.LogEntry;
import org.nuxeo.runtime.api.Framework;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;


public class AdvancedDocumentAuditListener implements EventListener {

    private static final Log log = LogFactory.getLog(AdvancedDocumentAuditListener.class);

    private static final List<String> SYSTEM_PROPS = Arrays.asList(
            "dc:created", "dc:creator", "dc:modified", "dc:contributors");

    public static final String EVENT_ID = "Property Modification";
    public static final String FIELD_NAME = "fieldname";
    public static final String OLD_VALUE = "oldValue";
    public static final String NEW_VALUE = "newValue";
    public static final String EMPTY_VALUE = "EMPTY";

    @Override
    public void handleEvent(Event event) {
        EventContext ectx = event.getContext();
        if (!(ectx instanceof DocumentEventContext)) {
            return;
        }

        AuditLogger logger = Framework.getLocalService(AuditLogger.class);
        if (logger == null) {
            log.error("No AuditLogger implementation is available");
            return;
        }

        DocumentEventContext docCtx = (DocumentEventContext) ectx;
        DocumentModel newDoc = docCtx.getSourceDocument();

        DocumentModel oldDoc = newDoc.getCoreSession().getDocument(newDoc.getRef());

        Context context = new Context(newDoc, oldDoc, event, logger);
        processDocument(context);
    }


    protected void processDocument(Context context) {
        List<LogEntry> entries = new ArrayList<>();

        DataModelMap dataModels = context.newDoc.getDataModels();
        for (DataModel model : dataModels.values()) {
            if (model.isDirty()) {
                entries.addAll(processModel(context, model));
            }
        }

        if (entries.size() > 0) context.logger.addLogEntries(entries);
    }


    protected List<LogEntry> processModel(Context context, DataModel newModel) {

        Collection<String> dirtyFieldNames = newModel.getDirtyFields();
        List<LogEntry> entries = new ArrayList<>();

        for (String fieldName : dirtyFieldNames) {
            //skip system properties
            if (SYSTEM_PROPS.contains(fieldName)) continue;

            Property oldProperty = context.oldDoc.getProperty(fieldName);
            Property newProperty = context.newDoc.getProperty(fieldName);
            entries.addAll(processProperty(context, oldProperty, newProperty));
        }

        return entries;
    }


    protected List<LogEntry> processProperty(Context context, Property oldProperty, Property newProperty) {

        List<LogEntry> entries = new ArrayList<>();

        //Handle Scalar Properties
        if (oldProperty.isScalar()) {
            entries.add(
                    processScalarProperty(context, oldProperty, newProperty));
        }

        //Handle Complex Properties
        if (oldProperty.isComplex() && !oldProperty.isList()) {
            if (oldProperty instanceof BlobProperty) {
                entries.add(processBlobProperty(context, oldProperty, newProperty));
            } else {
                entries.addAll(
                        processComplexProperty(context, oldProperty, newProperty));
            }
        }

        //Handle Scalar List Properties
        if (oldProperty instanceof ArrayProperty) {
            entries.addAll(
                    processScalarList(
                            context, oldProperty.getPath(), oldProperty.getValue(), newProperty.getValue()));
        }

        return entries;
    }


    protected LogEntry processScalarProperty(Context context, Property oldProperty, Property newProperty) {
        return getEntry(context, oldProperty.getPath(), oldProperty.getValue(), newProperty.getValue());
    }


    protected List<LogEntry> processScalarList(Context context, String fieldName, Object oldValue, Object newValue) {
        List<LogEntry> entries = new ArrayList<>();

        List<Serializable> oldList = Arrays.asList((Serializable[]) oldValue);
        List<Serializable> newList = Arrays.asList((Serializable[]) newValue);

        //get Added Values
        List<Serializable> added = new ArrayList<>(newList);
        added.removeAll(oldList);
        for (Serializable addedValue : added) {
            LogEntry entry = getEntry(context, fieldName, null, addedValue);
            entry.setComment(fieldName + " : Added " + formatPropertyValue(addedValue));
            entries.add(entry);
        }

        //get Removed Value
        List<Serializable> removed = new ArrayList<>(oldList);
        removed.removeAll(newList);
        for (Serializable removedValue : removed) {
            LogEntry entry = getEntry(context, fieldName, removedValue, null);
            entry.setComment(fieldName + " : Removed " + formatPropertyValue(removedValue));
            entries.add(entry);
        }

        return entries;
    }


    protected List<LogEntry> processComplexProperty(Context context, Property oldProperty, Property newProperty) {
        List<LogEntry> entries = new ArrayList<>();
        Iterator<Property> dirtyProperties = newProperty.getDirtyChildren();
        while (dirtyProperties.hasNext()) {
            Property dirtyProperty = dirtyProperties.next();
            entries.addAll(
                    processProperty(context, oldProperty.get(dirtyProperty.getName()), dirtyProperty));
        }
        return entries;
    }


    protected LogEntry processBlobProperty(Context context, Property oldProperty, Property newProperty) {
        Blob oldBlob = (Blob) oldProperty.getValue();
        String oldFilename = oldBlob != null ? oldBlob.getFilename() : null;
        Blob newBlob = (Blob) newProperty.getValue();
        String newFilename = newBlob != null ? newBlob.getFilename() : null;
        return getEntry(context, oldProperty.getPath(), oldFilename, newFilename);
    }


    protected LogEntry getEntry(Context context, String fieldName, Serializable oldValue, Serializable newValue) {

        AuditLogger logger = context.logger;
        Event event = context.event;
        DocumentModel doc = context.newDoc;

        LogEntry entry = logger.newLogEntry();
        entry.setEventId(EVENT_ID);
        entry.setCategory("Document");
        entry.setEventDate(new Date(event.getTime()));
        entry.setDocUUID(doc.getRef());
        entry.setDocLifeCycle(doc.getCurrentLifeCycleState());
        entry.setPrincipalName(doc.getCoreSession().getPrincipal().getName());
        entry.setRepositoryId(doc.getRepositoryName());

        Map<String, ExtendedInfo> extended = new HashMap<>();
        if (fieldName.startsWith("/")) fieldName = fieldName.substring(1);
        extended.put(FIELD_NAME, logger.newExtendedInfo(fieldName));

        String formatedOldValue = formatPropertyValue(oldValue);
        String formatedNewValue = formatPropertyValue(newValue);

        extended.put(OLD_VALUE, logger.newExtendedInfo(formatedOldValue));
        extended.put(NEW_VALUE, logger.newExtendedInfo(formatedNewValue));
        entry.setExtendedInfos(extended);

        entry.setComment(fieldName + " : " + formatedOldValue + " -> " + formatedNewValue);
        return entry;
    }


    protected String formatPropertyValue(Serializable value) {
        if (value instanceof Calendar) {
            Calendar calendar = (Calendar) value;
            String pattern = "MM/dd/yyyy";
            SimpleDateFormat format = new SimpleDateFormat(pattern);
            return format.format(calendar.getTime());
        } else if (value != null) {
            return value.toString();
        } else {
            return EMPTY_VALUE;
        }
    }


    class Context {

        private DocumentModel newDoc;
        private DocumentModel oldDoc;
        private Event event;
        private AuditLogger logger;

        public Context(DocumentModel newDoc, DocumentModel oldDoc, Event event, AuditLogger logger) {
            this.newDoc = newDoc;
            this.oldDoc = oldDoc;
            this.event = event;
            this.logger = logger;
        }
    }


}
