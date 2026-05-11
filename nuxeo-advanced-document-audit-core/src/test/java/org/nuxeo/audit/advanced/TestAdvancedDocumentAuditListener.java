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

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.audit.api.LogEntry;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

@RunWith(FeaturesRunner.class)
// AuditFeature must come before CoreFeature (pulled by AutomationFeature) per LTS 2025
// AuditCleanerFeature ordering requirement.
@Features({ AuditFeature.class, AutomationFeature.class })
@Deploy("nuxeo-advanced-document-audit-core:pageprovider-contrib.xml")
@Deploy("nuxeo-advanced-document-audit-core:schema-contrib.xml")
public class TestAdvancedDocumentAuditListener {

    private static final String PAGE_PROVIDER_NAME = "GetAllEntriesByEvent";

    private static final String NEW_STRING_VALUE = "testNewValue";

    @Inject
    protected CoreSession session;

    @Inject
    protected PageProviderService pps;

    @Inject
    protected TransactionalFeature txFeature;

    @Before
    public void onceExecutedBeforeAll() {
        Assert.assertNotNull(pps);
    }

    @Test
    public void testStringModification() throws IOException, OperationException {
        DocumentModel doc = session.createDocumentModel("/", "File", "File");
        doc = session.createDocument(doc);
        doc.setPropertyValue("dc:title", NEW_STRING_VALUE);
        session.saveDocument(doc);
        session.save();
        List<LogEntry> entries = getEntries(doc);
        Assert.assertEquals("Audit contains one entry", 1, entries.size());
        LogEntry entry = entries.get(0);
        checkEntry(entry, "dc:title", AdvancedDocumentAuditListener.EMPTY_VALUE, NEW_STRING_VALUE);
    }

    @Test
    public void testMultiStringModification() throws IOException, OperationException {
        DocumentModel doc = session.createDocumentModel("/", "File", "File");
        doc.setPropertyValue("dc:subjects", new String[] { "science" });
        doc = session.createDocument(doc);
        doc.setPropertyValue("dc:subjects", new String[] { "art" });
        session.saveDocument(doc);
        session.save();
        List<LogEntry> entries = getEntries(doc);
        Assert.assertEquals("Audit contains two entries", 2, entries.size());
    }

    @Test
    public void testStringInComplexModification() throws IOException, OperationException {
        DocumentModel doc = session.createDocumentModel("/", "TEST", "TEST");
        doc = session.createDocument(doc);
        HashMap<String, Serializable> complexValue = new HashMap<>();
        complexValue.put("string", NEW_STRING_VALUE);
        doc.setPropertyValue("test:complex", complexValue);
        session.saveDocument(doc);
        session.save();
        List<LogEntry> entries = getEntries(doc);
        Assert.assertEquals("Audit contains one entry", 1, entries.size());
        LogEntry entry = entries.get(0);
        checkEntry(entry, "test:complex/string", AdvancedDocumentAuditListener.EMPTY_VALUE, NEW_STRING_VALUE);
    }

    @Test
    public void testStringListInComplexModification() throws IOException, OperationException {
        DocumentModel doc = session.createDocumentModel("/", "TEST", "TEST");
        doc = session.createDocument(doc);

        HashMap<String, Serializable> complexValue = new HashMap<>();
        complexValue.put("stringlist", new String[] { NEW_STRING_VALUE });
        doc.setPropertyValue("test:complex", complexValue);

        session.saveDocument(doc);
        session.save();

        List<LogEntry> entries = getEntries(doc);
        Assert.assertEquals("Audit contains one entry", 1, entries.size());
        LogEntry entry = entries.get(0);
        checkEntry(entry, "test:complex/stringlist", AdvancedDocumentAuditListener.EMPTY_VALUE, NEW_STRING_VALUE);
    }

    @Test
    public void testBlobPropertyModification() throws IOException, OperationException {
        DocumentModel doc = session.createDocumentModel("/", "File", "File");
        doc = session.createDocument(doc);
        Blob blob = new FileBlob(new File(getClass().getResource("/files/text.txt").getPath()));
        doc.setPropertyValue("file:content", (Serializable) blob);
        session.saveDocument(doc);
        session.save();
        List<LogEntry> entries = getEntries(doc);
        Assert.assertEquals("Audit contains one entry", 1, entries.size());
        LogEntry entry = entries.get(0);
        checkEntry(entry, "content", AdvancedDocumentAuditListener.EMPTY_VALUE, "text.txt");
    }

    private void checkEntry(LogEntry entry, String fieldname, String expectedOldValue, String expectedNewValue) {
        Assert.assertEquals(fieldname, entry.getExtended().get(AdvancedDocumentAuditListener.FIELD_NAME));
        Assert.assertEquals(expectedNewValue, entry.getExtended().get(AdvancedDocumentAuditListener.NEW_VALUE));
        Assert.assertEquals(expectedOldValue, entry.getExtended().get(AdvancedDocumentAuditListener.OLD_VALUE));
    }

    @SuppressWarnings("unchecked")
    private List<LogEntry> getEntries(DocumentModel doc) {
        // Wait for asynchronous audit writes to complete before querying.
        txFeature.nextTransaction();
        PageProvider<?> pp = pps.getPageProvider(PAGE_PROVIDER_NAME, null, 5L, 0L, new HashMap<>(),
                AdvancedDocumentAuditListener.EVENT_ID, doc.getId());
        Assert.assertNotNull("Page Provider is missing", pp);
        return (List<LogEntry>) pp.getCurrentPage();
    }

}
