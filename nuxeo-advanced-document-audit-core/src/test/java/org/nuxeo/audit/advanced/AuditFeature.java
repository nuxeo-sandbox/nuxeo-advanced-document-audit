/*
 * (C) Copyright 2006-2025 Nuxeo SA (http://nuxeo.com/) and others.
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
 */

package org.nuxeo.audit.advanced;

import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.RunnerFeature;

/**
 * Test feature for the advanced document audit listener.
 * <p>
 * Delegates the audit backend setup to Nuxeo's built-in {@link org.nuxeo.audit.test.AuditFeature}
 * (in-memory backend by default in LTS 2025) and deploys this plugin's bundle.
 *
 * @since 2025.1
 */
@Features(org.nuxeo.audit.test.AuditFeature.class)
@Deploy("nuxeo-advanced-document-audit-core")
public class AuditFeature implements RunnerFeature {
}
