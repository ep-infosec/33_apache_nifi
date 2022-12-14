/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.web.security;

import org.apache.nifi.authorization.Authorizer;
import org.apache.nifi.authorization.util.IdentityMapping;
import org.apache.nifi.authorization.util.IdentityMappingUtil;
import org.apache.nifi.authorization.util.UserGroupUtil;
import org.apache.nifi.util.NiFiProperties;
import org.springframework.security.authentication.AuthenticationProvider;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Base AuthenticationProvider that provides common functionality to mapping identities.
 */
public abstract class NiFiAuthenticationProvider implements AuthenticationProvider {

    private final Authorizer authorizer;

    private final List<IdentityMapping> mappings;

    public NiFiAuthenticationProvider(final NiFiProperties properties, final Authorizer authorizer) {
        this.mappings = Collections.unmodifiableList(IdentityMappingUtil.getIdentityMappings(properties));
        this.authorizer = authorizer;
    }

    public List<IdentityMapping> getMappings() {
        return mappings;
    }

    protected String mapIdentity(final String identity) {
        return IdentityMappingUtil.mapIdentity(identity, mappings);
    }

    protected Set<String> getUserGroups(final String identity) {
        return UserGroupUtil.getUserGroups(authorizer, identity);
    }
}
