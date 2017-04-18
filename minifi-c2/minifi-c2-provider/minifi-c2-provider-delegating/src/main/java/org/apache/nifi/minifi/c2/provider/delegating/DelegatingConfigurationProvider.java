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

package org.apache.nifi.minifi.c2.provider.delegating;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.nifi.minifi.c2.api.Configuration;
import org.apache.nifi.minifi.c2.api.ConfigurationProvider;
import org.apache.nifi.minifi.c2.api.ConfigurationProviderException;
import org.apache.nifi.minifi.c2.api.InvalidParameterException;
import org.apache.nifi.minifi.c2.api.cache.ConfigurationCache;
import org.apache.nifi.minifi.c2.api.cache.ConfigurationCacheFileInfo;
import org.apache.nifi.minifi.c2.api.cache.WriteableConfiguration;
import org.apache.nifi.minifi.c2.provider.util.HttpConnector;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;

public class DelegatingConfigurationProvider implements ConfigurationProvider {
    private final ConfigurationCache configurationCache;
    private final HttpConnector httpConnector;
    private final ObjectMapper objectMapper;

    public DelegatingConfigurationProvider(ConfigurationCache configurationCache, String delegateUrl) throws InvalidParameterException, GeneralSecurityException, IOException {
        this(configurationCache, new HttpConnector(delegateUrl));
    }

    public DelegatingConfigurationProvider(ConfigurationCache configurationCache, HttpConnector httpConnector) {
        this.configurationCache = configurationCache;
        this.httpConnector = httpConnector;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<String> getContentTypes() throws ConfigurationProviderException {
        try {
            HttpURLConnection httpURLConnection = httpConnector.get("/c2/config/contentTypes");
            try {
                return objectMapper.readValue(httpURLConnection.getInputStream(), List.class);
            } finally {
                httpURLConnection.disconnect();
            }
        } catch (IOException e) {
            throw new ConfigurationProviderException("Unable to get content types from delegate.", e);
        }
    }

    @Override
    public Configuration getConfiguration(String contentType, Integer version, Map<String, List<String>> parameters) throws ConfigurationProviderException {
        HttpURLConnection remoteC2ServerConnection = null;
        try {
            if (version == null) {
                remoteC2ServerConnection = getDelegateConnection(contentType, parameters);
                version = Integer.parseInt(remoteC2ServerConnection.getHeaderField("X-Content-Version"));
            }
            ConfigurationCacheFileInfo cacheFileInfo = configurationCache.getCacheFileInfo(contentType, parameters);
            WriteableConfiguration configuration = cacheFileInfo.getConfiguration(version);
            if (!configuration.exists()) {
                if (remoteC2ServerConnection == null) {
                    remoteC2ServerConnection = getDelegateConnection(contentType, parameters);
                }
                try (InputStream inputStream = remoteC2ServerConnection.getInputStream();
                     OutputStream outputStream = configuration.getOutputStream()) {
                    IOUtils.copy(inputStream, outputStream);
                } catch (IOException e) {
                    throw new ConfigurationProviderException("Unable to copy remote configuration to cache.", e);
                }
            }
            return configuration;
        } finally {
            if (remoteC2ServerConnection != null) {
                remoteC2ServerConnection.disconnect();
            }
        }
    }

    protected HttpURLConnection getDelegateConnection(String contentType, Map<String, List<String>> parameters) throws ConfigurationProviderException {
        StringBuilder queryStringBuilder = new StringBuilder();
        for (Map.Entry<String, List<String>> keyValues : parameters.entrySet()) {
            String key = keyValues.getKey();
            for (String value : keyValues.getValue()) {
                try {
                    queryStringBuilder.append(URLEncoder.encode(key, "UTF-8"));
                    queryStringBuilder.append("=");
                    queryStringBuilder.append(URLEncoder.encode(value, "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    throw new ConfigurationProviderException("Unsupported encoding.", e);
                }
                queryStringBuilder.append("&");
            }
        }
        String url = "/c2/config";
        if (queryStringBuilder.length() > 0) {
            queryStringBuilder.setLength(queryStringBuilder.length() - 1);
            url = url + "?" + queryStringBuilder.toString();
        }
        HttpURLConnection httpURLConnection = httpConnector.get(url);
        httpURLConnection.setRequestProperty("Accepts", contentType);
        return httpURLConnection;
    }
}
