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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.audit.api.LogEntry;
import org.nuxeo.audit.test.AuditFeature;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.SortInfo;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderDefinition;
import org.nuxeo.ecm.platform.query.api.PageProviderService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import jakarta.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.nuxeo.audit.api.LogEntryConstants.LOG_ID;

@RunWith(FeaturesRunner.class)
@Features({AuditFeature.class, AutomationFeature.class})
@Deploy({
    "nuxeo-advanced-document-audit-core",
    "nuxeo-advanced-document-audit-core:schema-contrib.xml"
})
public class TestAdvancedDocumentAuditListener {

    private static final String NEW_STRING_VALUE = "testNewValue";

    @Inject
    CoreSession session;

    @Inject
    PageProviderService pps;

    @Before
    public void onceExecutedBeforeAll() {
        Assert.assertNotNull(pps);
    }

    @Test
    public void testStringModification() {
        DocumentModel doc = session.createDocumentModel("/", "File", "File");
        doc = session.createDocument(doc);
        doc.setPropertyValue("dc:title", NEW_STRING_VALUE);
        session.saveDocument(doc);
        session.save();
        List<LogEntry> entries = getEntries(doc);
        Assert.assertEquals("Audit contains one entry", 1, entries.size());
        LogEntry entry = entries.getFirst();
        checkEntry(entry, "dc:title",
                AdvancedDocumentAuditListener.EMPTY_VALUE, NEW_STRING_VALUE);
    }

    @Test
    public void testMultiStringModification() {
        DocumentModel doc = session.createDocumentModel("/", "File", "File");
        doc.setPropertyValue("dc:subjects", new String[]{"science"});
        doc = session.createDocument(doc);
        doc.setPropertyValue("dc:subjects", new String[]{"art"});
        session.saveDocument(doc);
        session.save();
        List<LogEntry> entries = getEntries(doc);
        Assert.assertEquals("Audit contains two entries", 2, entries.size());
    }

    @Test
    public void testStringInComplexModification() {
        DocumentModel doc = session.createDocumentModel("/", "TEST", "TEST");
        doc = session.createDocument(doc);
        HashMap<String, Serializable> complexValue = new HashMap<>();
        complexValue.put("string", NEW_STRING_VALUE);
        doc.setPropertyValue("test:complex", complexValue);
        session.saveDocument(doc);
        session.save();
        List<LogEntry> entries = getEntries(doc);
        Assert.assertEquals("Audit contains one entry", 1, entries.size());
        LogEntry entry = entries.getFirst();
        checkEntry(entry, "test:complex/string",
                AdvancedDocumentAuditListener.EMPTY_VALUE, NEW_STRING_VALUE);
    }

    @Test
    public void testStringListInComplexModification() {
        DocumentModel doc = session.createDocumentModel("/", "TEST", "TEST");
        doc = session.createDocument(doc);

        HashMap<String, Serializable> complexValue = new HashMap<>();
        complexValue.put("stringlist", new String[]{NEW_STRING_VALUE});
        doc.setPropertyValue("test:complex", complexValue);

        session.saveDocument(doc);
        session.save();

        List<LogEntry> entries = getEntries(doc);
        Assert.assertEquals("Audit contains one entry", 1, entries.size());
        LogEntry entry = entries.getFirst();
        checkEntry(entry, "test:complex/stringlist",
                AdvancedDocumentAuditListener.EMPTY_VALUE, NEW_STRING_VALUE);
    }


    @Test
    public void testBlobPropertyModification() {
        DocumentModel doc = session.createDocumentModel("/", "File", "File");
        doc = session.createDocument(doc);
        Blob blob = new FileBlob(new File(getClass().getResource("/files/text.txt").getPath()));
        doc.setPropertyValue("file:content", (Serializable) blob);
        session.saveDocument(doc);
        session.save();
        List<LogEntry> entries = getEntries(doc);
        Assert.assertEquals("Audit contains one entry", 1, entries.size());
        LogEntry entry = entries.getFirst();
        checkEntry(entry, "content", AdvancedDocumentAuditListener.EMPTY_VALUE, "text.txt");
    }


    private void checkEntry(LogEntry entry, String fieldname, String expectedOldValue, String expectedNewValue) {
        Assert.assertEquals(
                fieldname,
                entry.getExtendedValue(AdvancedDocumentAuditListener.FIELD_NAME));
        Assert.assertEquals(
                expectedNewValue,
                entry.getExtendedValue(AdvancedDocumentAuditListener.NEW_VALUE));
        Assert.assertEquals(
                expectedOldValue,
                entry.getExtendedValue(AdvancedDocumentAuditListener.OLD_VALUE));
    }

    private List<LogEntry> getEntries(DocumentModel doc) {
        PageProviderDefinition ppdef = pps.getPageProviderDefinition("DOCUMENT_HISTORY_PROVIDER");
        assertNotNull(ppdef);

        DocumentModel searchDoc = session.createDocumentModel("BasicAuditSearch");
        searchDoc.setPathInfo("/", "auditsearch");
        searchDoc = session.createDocument(searchDoc);
        searchDoc.setPropertyValue("basicauditsearch:eventIds",
                new String[] { AdvancedDocumentAuditListener.EVENT_ID });
        List<SortInfo> sorts = List.of(new SortInfo(LOG_ID, false));

        PageProvider<LogEntry>  pp = (PageProvider<LogEntry>) pps.getPageProvider(
                "DOCUMENT_HISTORY_PROVIDER", sorts, 20L, 0L, new HashMap<>(), doc);
        pp.setSearchDocumentModel(searchDoc);
        return pp.getCurrentPage();
    }


}
