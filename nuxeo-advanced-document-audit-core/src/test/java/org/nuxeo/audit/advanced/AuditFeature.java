/*
 * (C) Copyright 2006-2012 Nuxeo SA (http://nuxeo.com/) and others.
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
 *
 */

package org.nuxeo.audit.advanced;

import org.nuxeo.ecm.core.persistence.PersistenceProviderFactory;
import org.nuxeo.runtime.test.runner.TransactionalFeature;
import org.nuxeo.runtime.test.runner.TransactionalFeature.Waiter;
import org.nuxeo.ecm.platform.audit.api.AuditLogger;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.management.ManagementFeature;
import org.nuxeo.runtime.test.runner.*;
import org.nuxeo.runtime.transaction.TransactionHelper;

import javax.persistence.EntityManager;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Features({ManagementFeature.class, PlatformFeature.class})
@Deploy({
        "org.nuxeo.runtime.datasource",
        "org.nuxeo.runtime.metrics",
        "org.nuxeo.ecm.core.persistence",
        "org.nuxeo.ecm.platform.audit",
        "nuxeo-advanced-document-audit-core"
})
@LocalDeploy("nuxeo-document-trail-core:audit-contrib.xml")
public class AuditFeature extends SimpleFeature {

    @Override
    public void initialize(FeaturesRunner runner) throws Exception {
        runner.getFeature(TransactionalFeature.class)
                .addWaiter(new Waiter() {

                    @Override
                    public boolean await(long deadline) throws InterruptedException {
                        return Framework.getService(AuditLogger.class)
                                .await(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
                    }

                    @Override
                    public boolean await(Duration arg0) throws InterruptedException {
                        return Framework.getService(AuditLogger.class)
                                .await(arg0.toMillis(), TimeUnit.MILLISECONDS);
                    }

                });
    }

    @Override
    public void stop(FeaturesRunner runner) throws Exception {
        clear();
    }

    protected void clear() {
        boolean started = TransactionHelper.isTransactionActive() == false && TransactionHelper.startTransaction();
        try {
            doClear();
        } finally {
            if (started) {
                TransactionHelper.commitOrRollbackTransaction();
            }
        }
    }

    public void doClear() {
        EntityManager em = Framework.getService(PersistenceProviderFactory.class).newProvider("nxaudit-logs").acquireEntityManager();
        try {
            em.createNativeQuery("delete from nxp_logs_mapextinfos").executeUpdate();
            em.createNativeQuery("delete from nxp_logs_extinfo").executeUpdate();
            em.createNativeQuery("delete from nxp_logs").executeUpdate();
        } finally {
            em.close();
        }
    }
}
