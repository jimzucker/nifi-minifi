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

package org.apache.nifi.minifi.c2.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordnik.swagger.annotations.Api;
import org.apache.nifi.minifi.c2.api.Configuration;
import org.apache.nifi.minifi.c2.api.ConfigurationProvider;
import org.apache.nifi.minifi.c2.api.ConfigurationProviderException;
import org.apache.nifi.minifi.c2.api.InvalidParameterException;
import org.apache.nifi.minifi.c2.api.security.authorization.AuthorizationException;
import org.apache.nifi.minifi.c2.api.security.authorization.Authorizer;
import org.apache.nifi.minifi.c2.api.util.Pair;
import org.apache.nifi.minifi.c2.util.HttpRequestUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Path("/config")
@Api(
        value = "/config",
        description = "Provides configuration for MiNiFi instances"
)
public class ConfigService {
    private static final Logger logger = LoggerFactory.getLogger(ConfigService.class);
    private final List<ConfigurationProvider> configurationProviders;
    private final Authorizer authorizer;
    private final ObjectMapper objectMapper;

    private volatile List<Pair<MediaType, ConfigurationProvider>> mediaTypeList;
    private volatile List<String> contentTypes;

    public ConfigService(List<ConfigurationProvider> configurationProviders, Authorizer authorizer) {
        this.authorizer = authorizer;
        this.objectMapper = new ObjectMapper();
        if (configurationProviders == null || configurationProviders.size() == 0) {
            throw new IllegalArgumentException("Expected at least one configuration provider");
        }
        this.configurationProviders = new ArrayList<>(configurationProviders);
    }

    protected void initContentTypeInfo() throws ConfigurationProviderException {
        List<Pair<MediaType, ConfigurationProvider>> mediaTypeList = new ArrayList<>();
        List<String> contentTypes = new ArrayList<>();
        Set<MediaType> seenMediaTypes = new LinkedHashSet<>();

        for (ConfigurationProvider configurationProvider : configurationProviders) {
            for (String contentTypeString : configurationProvider.getContentTypes()) {
                MediaType mediaType = MediaType.valueOf(contentTypeString);
                if (seenMediaTypes.add(mediaType)) {
                    contentTypes.add(contentTypeString);
                    mediaTypeList.add(new Pair<>(mediaType, configurationProvider));
                }
            }
        }
        this.mediaTypeList = mediaTypeList;
        this.contentTypes = contentTypes;
    }

    @GET
    @Path("/contentTypes")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getContentTypes(@Context HttpServletRequest request, @Context UriInfo uriInfo) {
        try {
            authorizer.authorize(SecurityContextHolder.getContext().getAuthentication(), uriInfo);
        } catch (AuthorizationException e) {
            logger.warn(HttpRequestUtil.getClientString(request) + " not authorized to access " + uriInfo, e);
            return Response.status(403).build();
        }
        if (contentTypes == null) {
            try {
                initContentTypeInfo();
            } catch (ConfigurationProviderException e) {
                logger.warn("Unable to initialize content type information.", e);
                return Response.status(500).build();
            }
        }
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(byteArrayOutputStream, contentTypes);
        } catch (IOException e) {
            logger.warn("Unable to write configuration providers to output stream.", e);
            return Response.status(500).build();
        }
        return Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(byteArrayOutputStream.toByteArray()).build();
    }

    @GET
    public Response getConfig(@Context HttpServletRequest request, @Context HttpHeaders httpHeaders, @Context UriInfo uriInfo) {
        try {
            authorizer.authorize(SecurityContextHolder.getContext().getAuthentication(), uriInfo);
        } catch (AuthorizationException e) {
            logger.warn(HttpRequestUtil.getClientString(request) + " not authorized to access " + uriInfo, e);
            return Response.status(403).build();
        }
        Map<String, List<String>> parameters = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
            parameters.put(entry.getKey(), entry.getValue());
        }
        List<MediaType> acceptValues = httpHeaders.getAcceptableMediaTypes();
        boolean defaultAccept = false;
        if (acceptValues.size() == 0) {
            acceptValues = Arrays.asList(MediaType.WILDCARD_TYPE);
            defaultAccept = true;
        }
        if (logger.isDebugEnabled()) {
            StringBuilder builder = new StringBuilder("Handling request from ")
                    .append(HttpRequestUtil.getClientString(request))
                    .append(" with parameters ")
                    .append(parameters)
                    .append(" and Accept");
            if (defaultAccept) {
                builder = builder.append(" default value");
            }
            builder = builder.append(": ")
                    .append(acceptValues.stream().map(Object::toString).collect(Collectors.joining(", ")));
            logger.debug(builder.toString());
        }
        Pair<MediaType, ConfigurationProvider> providerPair = null;
        try {
            providerPair = getProvider(acceptValues);
        } catch (ConfigurationProviderException e) {
            logger.warn("Unable to get provider.", e);
            return Response.status(500).build();
        }

        try {
            Integer version = null;
            List<String> versionList = parameters.get("version");
            if (versionList != null && versionList.size() > 0) {
                try {
                    version = Integer.parseInt(versionList.get(0));
                } catch (NumberFormatException e) {
                    throw new InvalidParameterException("Unable to parse " + version + " as integer.", e);
                }
            }
            Response.ResponseBuilder ok = Response.ok();
            Configuration configuration = providerPair.getSecond().getConfiguration(providerPair.getFirst().toString(), version, parameters);
            ok = ok.header("X-Content-Version", configuration.getVersion());
            ok = ok.type(providerPair.getFirst());
            byte[] buffer = new byte[1024];
            int read;
            try (InputStream inputStream = configuration.getInputStream();
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                MessageDigest md5 = MessageDigest.getInstance("MD5");
                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                while((read = inputStream.read(buffer)) >= 0) {
                    outputStream.write(buffer, 0, read);
                    md5.update(buffer, 0, read);
                    sha256.update(buffer, 0, read);
                }
                ok = ok.header("Content-MD5", bytesToHex(md5.digest()));
                ok = ok.header("X-Content-SHA-256", bytesToHex(sha256.digest()));
                ok = ok.entity(outputStream.toByteArray());
            } catch (IOException|NoSuchAlgorithmException e) {
                logger.error("Error reading or checksumming configuration file", e);
                throw new WebApplicationException(500);
            }
            return ok.build();
        } catch (AuthorizationException e) {
            logger.warn(HttpRequestUtil.getClientString(request) + " not authorized to access " + uriInfo, e);
            return Response.status(403).build();
        } catch (InvalidParameterException e) {
            logger.info(HttpRequestUtil.getClientString(request) + " made invalid request with " + HttpRequestUtil.getQueryString(request), e);
            return Response.status(400).entity("Invalid request.").build();
        } catch (Throwable t) {
            logger.error(HttpRequestUtil.getClientString(request) + " made request with " + HttpRequestUtil.getQueryString(request) + " that caused error in " + providerPair.getSecond(), t);
            return Response.status(500).entity("Internal error").build();
        }
    }

    // see: http://stackoverflow.com/questions/15429257/how-to-convert-byte-array-to-hexstring-in-java#answer-15429408
    protected static String bytesToHex(byte[] in) {
        final StringBuilder builder = new StringBuilder();
        for(byte b : in) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private Pair<MediaType, ConfigurationProvider> getProvider(List<MediaType> acceptValues) throws ConfigurationProviderException {
        if (mediaTypeList == null) {
            initContentTypeInfo();
        }
        for (MediaType accept : acceptValues) {
            for (Pair<MediaType, ConfigurationProvider> pair : mediaTypeList) {
                MediaType mediaType = pair.getFirst();
                if (accept.isCompatible(mediaType)) {
                    return new Pair<>(mediaType, pair.getSecond());
                }
            }
        }

        throw new WebApplicationException(Response.status(406).entity("Unable to find configuration provider for " +
                "\"Accept: " + acceptValues.stream().map(Object::toString).collect(Collectors.joining(", ")) + "\" supported media types are " +
                mediaTypeList.stream().map(Pair::getFirst).map(Object::toString).collect(Collectors.joining(", "))).build());
    }
}
