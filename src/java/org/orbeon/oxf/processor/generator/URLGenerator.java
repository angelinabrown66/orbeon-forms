/**
 *  Copyright (C) 2004 - 2005 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.processor.generator;

import org.apache.log4j.Logger;
import org.dom4j.Element;
import org.orbeon.oxf.cache.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.resources.handler.OXFHandler;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.xerces.XIncludeHandler;
import org.w3c.dom.Document;
import org.w3c.tidy.Tidy;
import org.xml.sax.*;

import javax.xml.parsers.SAXParser;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

/**
 * Generates SAX events from a document fetched from an URL.
 * 
 * NOTE: For XML content-type and encoding related questions, check out the following draft
 * document:
 *
 * http://www.faqs.org/rfcs/rfc3023.html
 * http://www.ietf.org/internet-drafts/draft-murata-kohn-lilley-xml-00.txt
 */
public class URLGenerator extends ProcessorImpl {

    private static Logger logger = Logger.getLogger(URLGenerator.class);

    private static final boolean DEFAULT_VALIDATING = false;
    public static final boolean DEFAULT_HANDLE_XINCLUDE = false;

    private static final boolean DEFAULT_FORCE_CONTENT_TYPE = false;
    private static final boolean DEFAULT_FORCE_ENCODING = false;
    private static final boolean DEFAULT_IGNORE_CONNECTION_ENCODING = false;

    private static final boolean DEFAULT_OUTPUT_BINARY = false;

    private static final int CACHE_EXPIRATION_NO_CACHE = 0;
    private static final int CACHE_EXPIRATION_NO_EXPIRATION = -1;
    private static final int CACHE_EXPIRATION_LAST_MODIFIED = -2;

    private static final boolean DEFAULT_CACHE_USE_LOCAL_CACHE = true;
    private static final boolean DEFAULT_CACHE_ALWAYS_REVALIDATE = true;
    private static final int DEFAULT_CACHE_EXPIRATION = CACHE_EXPIRATION_LAST_MODIFIED;

    public static final String URL_NAMESPACE_URI = "http://www.orbeon.org/oxf/xml/url";
    public static final String VALIDATING_PROPERTY = "validating";
    public static final String HANDLE_XINCLUDE_PROPERTY = "handle-xinclude";

    private ConfigURIReferences localConfigURIReferences;

    public URLGenerator() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, URL_NAMESPACE_URI));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public URLGenerator(String url) {
        try {
            init(URLFactory.createURL(url), DEFAULT_HANDLE_XINCLUDE);
        } catch (MalformedURLException e) {
            throw new OXFException(e);
        }
    }

    public URLGenerator(String url, boolean handleXInclude) {
        try {
            init(URLFactory.createURL(url), handleXInclude);
        } catch (MalformedURLException e) {
            throw new OXFException(e);
        }
    }

    public URLGenerator(URL url) {
        init(url, DEFAULT_HANDLE_XINCLUDE);
    }

    public URLGenerator(URL url, boolean handleXInclude) {
        init(url, handleXInclude);
    }

    private void init(URL url, boolean handleXInclude) {
        this.localConfigURIReferences = new ConfigURIReferences(new Config(url, handleXInclude));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public URLGenerator(URL url, String contentType, boolean forceContentType) {
        this.localConfigURIReferences = new ConfigURIReferences(new Config(url, contentType, forceContentType));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public URLGenerator(URL url, String contentType, boolean forceContentType, String encoding, boolean forceEncoding,
                      boolean ignoreConnectionEncoding, boolean validating, boolean handleXInclude, boolean outputBinary, Map headers,
                      boolean cacheUseLocalCache) {
        this.localConfigURIReferences = new ConfigURIReferences(new Config(url, contentType, forceContentType, encoding,
                forceEncoding, ignoreConnectionEncoding, validating, handleXInclude, outputBinary, headers, cacheUseLocalCache,
                DEFAULT_CACHE_ALWAYS_REVALIDATE, DEFAULT_CACHE_EXPIRATION, new TidyConfig(null)));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    private static class Config {
        private URL url;
        private String contentType = ProcessorUtils.DEFAULT_CONTENT_TYPE;
        private boolean forceContentType = DEFAULT_FORCE_CONTENT_TYPE;
        private String encoding;
        private boolean forceEncoding = DEFAULT_FORCE_ENCODING;
        private boolean ignoreConnectionEncoding = DEFAULT_IGNORE_CONNECTION_ENCODING;
        private boolean validating = DEFAULT_VALIDATING;
        private Map headers;
        private boolean handleXInclude = DEFAULT_HANDLE_XINCLUDE;

        private boolean outputBinary = DEFAULT_OUTPUT_BINARY;

        private boolean cacheUseLocalCache = DEFAULT_CACHE_USE_LOCAL_CACHE;
        private boolean cacheAlwaysRevalidate = DEFAULT_CACHE_ALWAYS_REVALIDATE;
        private int cacheExpiration = DEFAULT_CACHE_EXPIRATION;

        private TidyConfig tidyConfig;

        public Config(URL url) {
            this.url = url;
            this.tidyConfig = new TidyConfig(null);
        }

        public Config(URL url, boolean handleXInclude) {
            this.url = url;
            this.handleXInclude = handleXInclude;
            this.tidyConfig = new TidyConfig(null);
        }

        public Config(URL url, String contentType, boolean forceContentType) {
            this(url);
            this.forceContentType = true;
            this.contentType = contentType;
            this.forceContentType = forceContentType;
            this.tidyConfig = new TidyConfig(null);
        }

        public Config(URL url, String contentType, boolean forceContentType, String encoding, boolean forceEncoding,
                      boolean ignoreConnectionEncoding, boolean validating, boolean handleXInclude, boolean outputBinary,Map headers,
                      boolean cacheUseLocalCache, boolean cacheAlwaysRevalidate, int cacheExpiration, TidyConfig tidyConfig) {
            this.url = url;
            this.contentType = contentType;
            this.forceContentType = forceContentType;
            this.encoding = encoding;
            this.forceEncoding = forceEncoding;
            this.ignoreConnectionEncoding = ignoreConnectionEncoding;
            this.validating = validating;
            this.headers = headers;
            this.handleXInclude = handleXInclude;

            this.outputBinary = outputBinary;

            this.cacheUseLocalCache = cacheUseLocalCache;
            this.cacheAlwaysRevalidate = cacheAlwaysRevalidate;
            this.cacheExpiration = cacheExpiration;

            this.tidyConfig = tidyConfig;
        }

        public URL getURL() {
            return url;
        }

        public String getContentType() {
            return contentType;
        }

        public boolean isForceContentType() {
            return forceContentType;
        }

        public String getEncoding() {
            return encoding;
        }

        public boolean isForceEncoding() {
            return forceEncoding;
        }

        public boolean isIgnoreConnectionEncoding() {
            return ignoreConnectionEncoding;
        }

        public TidyConfig getTidyConfig() {
            return tidyConfig;
        }

        public boolean isValidating() {
            return validating;
        }

        public boolean isHandleXInclude() {
            return handleXInclude;
        }

        public boolean isOutputBinary() {
            return outputBinary;
        }

        public Map getHeaders() {
            return headers;
        }

        public boolean isCacheUseLocalCache() {
            return cacheUseLocalCache;
        }

//        public boolean isCacheAlwaysRevalidate() {
//            return cacheAlwaysRevalidate;
//        }

//        public int getCacheExpiration() {
//            return cacheExpiration;
//        }

        public String toString() {
            return "[" + getURL().toExternalForm() + "|" + getContentType() + "|" + getEncoding() + "|" + isValidating() + "|" + isHandleXInclude() + "|" + isForceContentType()
                    + "|" + isForceEncoding() + "|" + isIgnoreConnectionEncoding() + "|" + tidyConfig + "]";
        }
    }

    private static class ConfigURIReferences {
        public ConfigURIReferences(Config config) {
            this.config = config;
        }

        public Config config;
        public URIReferences uriReferences;
    }

    public ProcessorOutput createOutput(final String name) {
        ProcessorOutput output = new ProcessorImpl.ProcessorOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext pipelineContext, ContentHandler contentHandler) {

                makeSureStateIsSet(pipelineContext);

                // Read config input into a URL, cache if possible
                final ConfigURIReferences configURIReferences = URLGenerator.this.localConfigURIReferences != null ? localConfigURIReferences :
                        (ConfigURIReferences) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_CONFIG), new CacheableInputReader() {
                            public Object read(PipelineContext context, ProcessorInput input) {
                                Element configElement = readInputAsDOM4J(context, input).getRootElement();

                                // Processor location data
                                final LocationData locationData = URLGenerator.this.getLocationData();

                                // Shortcut if the url is direct child of config
                                {
                                    final String url = configElement.getTextTrim();
                                    if (url != null && !url.equals("")) {
                                        try {
                                            // Legacy, don't even care about handling relative URLs
                                            return new ConfigURIReferences(new Config(URLFactory.createURL(url)));
                                        } catch (MalformedURLException e) {
                                            throw new ValidationException(e, locationData);
                                        }
                                    }
                                }

                                // We have the /config/url syntax
                                final String url = XPathUtils.selectStringValueNormalize(configElement, "/config/url");
                                if (url == null) {
                                    throw new ValidationException("URL generator found null URL for config:\n" + Dom4jUtils.domToString(configElement), locationData);
                                }

                                // Get content-type
                                String contentType = XPathUtils.selectStringValueNormalize(configElement, "/config/content-type");
                                boolean forceContentType = ProcessorUtils.selectBooleanValue(configElement, "/config/force-content-type", DEFAULT_FORCE_CONTENT_TYPE);
                                if (forceContentType && (contentType == null || contentType.equals("")))
                                    throw new ValidationException("The force-content-type element requires a content-type element.", locationData);

                                // Get encoding
                                String encoding = XPathUtils.selectStringValueNormalize(configElement, "/config/encoding");
                                boolean forceEncoding = ProcessorUtils.selectBooleanValue(configElement, "/config/force-encoding", DEFAULT_FORCE_ENCODING);
                                boolean ignoreConnectionEncoding = ProcessorUtils.selectBooleanValue(configElement, "/config/ignore-connection-encoding", DEFAULT_IGNORE_CONNECTION_ENCODING);
                                if (forceEncoding && (encoding == null || encoding.equals("")))
                                    throw new ValidationException("The force-encoding element requires an encoding element.", locationData);

                                // Get headers
                                Map headers = new HashMap();
                                for (Iterator i = configElement.selectNodes("/config/header").iterator(); i.hasNext();) {
                                    Element headerElement = (Element) i.next();
                                    String name = headerElement.element("name").getStringValue();
                                    String value = headerElement.element("value").getStringValue();
                                    headers.put(name, value);
                                }

                                // Validation setting: local, then properties, then hard-coded default
                                boolean defaultValidating = getPropertySet().getBoolean(VALIDATING_PROPERTY, DEFAULT_VALIDATING).booleanValue();
                                boolean validating = ProcessorUtils.selectBooleanValue(configElement, "/config/validating", defaultValidating);

                                // XInclude handling
                                boolean defaultHandleXInclude = getPropertySet().getBoolean(HANDLE_XINCLUDE_PROPERTY, DEFAULT_HANDLE_XINCLUDE).booleanValue();
                                boolean handleXInclude = ProcessorUtils.selectBooleanValue(configElement, "/config/handle-xinclude", defaultHandleXInclude);

                                // Binary output
                                boolean outputBinary = ProcessorUtils.selectBooleanValue(configElement, "/config/output-binary", DEFAULT_OUTPUT_BINARY);

                                // Cache control
                                boolean cacheUseLocalCache = ProcessorUtils.selectBooleanValue(configElement, "/config/cache-control/use-local-cache", DEFAULT_CACHE_USE_LOCAL_CACHE);
                                boolean cacheAlwaysRevalidate = ProcessorUtils.selectBooleanValue(configElement, "/config/cache-control/always-revalidate", DEFAULT_CACHE_ALWAYS_REVALIDATE);
                                int cacheExpiration = ProcessorUtils.selectIntValue(configElement, "/config/cache-control/expiration", DEFAULT_CACHE_EXPIRATION);

                                // Get Tidy config (will only apply if content-type is text/html)
                                TidyConfig tidyConfig = new TidyConfig(XPathUtils.selectSingleNode(configElement, "/config/tidy-options"));

                                // Create configuration object
                                try {
                                    // Use location data if present so that relative URLs can be supported
                                    // NOTE: We check whether there is a protocol, because we have
                                    // some Java location data which are NOT to be interpreted as
                                    // base URIs
                                    URL fullURL = (locationData != null && locationData.getSystemID() != null && NetUtils.urlHasProtocol(locationData.getSystemID()))
                                            ? URLFactory.createURL(locationData.getSystemID(), url)
                                            : URLFactory.createURL(url);

                                    // Create configuration
                                    Config config = new Config(fullURL, contentType, forceContentType, encoding, forceEncoding,
                                            ignoreConnectionEncoding, validating, handleXInclude, outputBinary, headers,
                                            cacheUseLocalCache, cacheAlwaysRevalidate, cacheExpiration, tidyConfig);
                                    if (logger.isDebugEnabled())
                                        logger.debug("Read configuration: " + config.toString());
                                    return new ConfigURIReferences(config);
                                } catch (MalformedURLException e) {
                                    throw new ValidationException(e, locationData);
                                }
                            }
                        });
                try {
                    // Never accept a null URL
                    if (configURIReferences.config.getURL() == null)
                        throw new OXFException("Missing configuration.");
                    // Create unique key and validity for the document
                    final boolean isUseLocalCache = configURIReferences.config.isCacheUseLocalCache();
                    final CacheKey localCacheKey;
                    final Object localCacheValidity;
                    if (isUseLocalCache) {
                        localCacheKey = new InternalCacheKey(URLGenerator.this, "urlDocument", configURIReferences.config.toString());
                        localCacheValidity = getValidityImpl(pipelineContext);
                    } else {
                        localCacheKey = null;
                        localCacheValidity = null;
                    }

                    // Decide whether to use read from the special oxf: handler or the generic URL handler
                    ResourceHandler handler = null;
                    try {
                        // We use the same validity as for the output
                        final Object cachedResource = (localCacheKey == null) ? null : ObjectCache.instance().findValid(pipelineContext, localCacheKey, localCacheValidity);
                        if (cachedResource != null) {
                            // Just replay the cached resource
                            ((SAXStore) cachedResource).replay(contentHandler);
                        } else {
                            // We need to read the resource

                            handler = OXFHandler.PROTOCOL.equals(configURIReferences.config.getURL().getProtocol())
                                    ? (ResourceHandler) new OXFResourceHandler(configURIReferences.config)
                                    : (ResourceHandler) new URLResourceHandler(configURIReferences.config);

                            // Find content-type to use. If the config says to force the
                            // content-type, we use the content-type provided by the user.
                            // Otherwise, we give the priority to the content-type provided by
                            // the connection, then the content-type provided by the user, then
                            // we use the default content-type (XML). The user will have to
                            // provide a content-type for example to read HTML documents with
                            // the file: protocol.
                            String contentType;
                            if (configURIReferences.config.isForceContentType()) {
                                contentType = configURIReferences.config.getContentType();
                            } else {
                                contentType = handler.getResourceContentType();
                                if (contentType == null)
                                    contentType = configURIReferences.config.getContentType();
                                if (contentType == null)
                                    contentType = ProcessorUtils.DEFAULT_CONTENT_TYPE;
                            }

                            // Get and cache validity as the handler is open, as validity is likely to be used later
                            // again for caching reasons
                            getHandlerValidity(pipelineContext, configURIReferences.config.getURL(), handler);

                            // Create store for caching if necessary
                            final ContentHandler output = isUseLocalCache ? new SAXStore(contentHandler) : contentHandler;

                            // Read resource
                            if (configURIReferences.config.isOutputBinary()) {
                                // Always output a binary document in this case
                                handler.readBinary(output, contentType);
                                configURIReferences.uriReferences = null;
                            } else if (ProcessorUtils.HTML_CONTENT_TYPE.equals(contentType)) {
                                handler.readHTML(output);
                                configURIReferences.uriReferences = null;
                            } else if (XMLUtils.isXMLMediatype(contentType)) {
                                final LocalXIncludeListener localXIncludeListener = new LocalXIncludeListener();
                                XIncludeHandler.setXIncludeListener(localXIncludeListener);
                                try {
                                    handler.readXML(pipelineContext, output);
                                } finally {
                                    XIncludeHandler.setXIncludeListener(null);
                                }
                                localXIncludeListener.updateCache(pipelineContext, configURIReferences);
                            } else if (XMLUtils.isTextContentType(contentType)) {
                                handler.readText(output, contentType);
                                configURIReferences.uriReferences = null;
                            } else {
                                handler.readBinary(output, contentType);
                                configURIReferences.uriReferences = null;
                            }

                            // Cache the resource if requested
                            if (isUseLocalCache) {
                                // Make sure SAXStore loses its reference on its output so that we don't clutter the cache
                                ((SAXStore) output).setContentHandler(null);
                                // Add to cache
                                ObjectCache.instance().add(pipelineContext, localCacheKey, localCacheValidity, output);
                            }
                        }
                    } finally {
                        if (handler != null)
                            handler.destroy();
                    }
                } catch (SAXParseException spe) {
                    throw new ValidationException(spe.getMessage(), new LocationData(spe));
                } catch (ValidationException e) {
                    final LocationData locationData = e.getLocationData();
                    // The system id may not be set
                    if (locationData == null || locationData.getSystemID() == null)
                        throw ValidationException.wrapException(e, new LocationData(configURIReferences.config.getURL().toExternalForm(), -1, -1));
                    else
                        throw e;
                } catch (OXFException e) {
                    throw e;
                } catch (Exception e) {
                    throw new ValidationException(e, new LocationData(configURIReferences.config.getURL().toExternalForm(), -1, -1));
                }
            }

            public OutputCacheKey getKeyImpl(PipelineContext pipelineContext) {
                makeSureStateIsSet(pipelineContext);
                try {
                    ConfigURIReferences configURIReferences = getConfigURIReferences(pipelineContext);
                    if (configURIReferences == null)
                        return null;

                    final int keyCount = 1 + ((localConfigURIReferences == null) ? 1 : 0)
                            + ((configURIReferences.uriReferences != null) ? configURIReferences.uriReferences.references.size() : 0);
                    final CacheKey[] outputKeys = new CacheKey[keyCount];

                    // Handle config if read as input
                    int keyIndex = 0;
                    if (localConfigURIReferences == null) {
                        KeyValidity configKeyValidity = getInputKeyValidity(pipelineContext, INPUT_CONFIG);
                        if (configKeyValidity == null) return null;
                        outputKeys[keyIndex++] = configKeyValidity.key;
                    }
                    // Handle main document and config
                    outputKeys[keyIndex++] = new SimpleOutputCacheKey(getProcessorClass(), name, configURIReferences.config.toString());
                    // Handle dependencies if any
                    if (configURIReferences.uriReferences != null) {
                        for (Iterator i = configURIReferences.uriReferences.references.iterator(); i.hasNext();) {
                            URIReference uriReference = (URIReference) i.next();
                            outputKeys[keyIndex++] = new InternalCacheKey(URLGenerator.this, "urlReference", URLFactory.createURL(uriReference.context, uriReference.spec).toExternalForm());
                        }
                    }
                    return new CompoundOutputCacheKey(getProcessorClass(), name, outputKeys);
                } catch (MalformedURLException e) {
                    throw new OXFException(e);
                }
            }

            public Object getValidityImpl(PipelineContext pipelineContext) {
                makeSureStateIsSet(pipelineContext);
                try {
                    ConfigURIReferences configURIReferences = getConfigURIReferences(pipelineContext);
                    if (configURIReferences == null)
                        return null;

                    List validities = new ArrayList();

                    // Handle config if read as input
                    if (localConfigURIReferences == null) {
                        KeyValidity configKeyValidity = getInputKeyValidity(pipelineContext, INPUT_CONFIG);
                        if (configKeyValidity == null)
                            return null;
                        validities.add(configKeyValidity.validity);
                    }
                    // Handle main document and config
                    validities.add(getHandlerValidity(pipelineContext, configURIReferences.config.getURL(), null));
                    // Handle dependencies if any
                    if (configURIReferences.uriReferences != null) {
                        for (Iterator i = configURIReferences.uriReferences.references.iterator(); i.hasNext();) {
                            URIReference uriReference = (URIReference) i.next();
                            validities.add(getHandlerValidity(pipelineContext, URLFactory.createURL(uriReference.context, uriReference.spec), null));
                        }
                    }
                    return validities;
                } catch (IOException e) {
                    throw new OXFException(e);
                }
            }

            private Object getHandlerValidity(PipelineContext pipelineContext, URL url, ResourceHandler handler) {
                final URLGeneratorState state = (URLGenerator.URLGeneratorState) URLGenerator.this.getState(pipelineContext);
                final String urlString = url.toExternalForm();
                if (state.isLastModifiedSet(urlString)) {
                    // Found value in state cache
                    return state.getLastModified(urlString);
                } else {
                    // Get value and cache it in state
                    try {
                        final boolean mustDestroyHandler;
                        if (handler == null) {
                            handler = OXFHandler.PROTOCOL.equals(url.getProtocol())
                                ? (ResourceHandler) new OXFResourceHandler(new Config(url))
                                : (ResourceHandler) new URLResourceHandler(new Config(url));
                            mustDestroyHandler = true;
                        } else {
                            mustDestroyHandler = false;
                        }
                        try {
                            // FIXME: this can potentially be very slow with some URLs
                            final Object validity = handler.getValidity();
                            state.setLastModified(urlString, (Long) validity);
                            return validity;
                        } finally {
                            if (mustDestroyHandler)
                                handler.destroy();
                        }
                    } catch (Exception e) {
                        // If the file no longer exists, for example, we don't want to throw, just to invalidate
                        // An exception will be thrown if necessary when the document is actually read
                        return null;
                    }
                }
            }

            private ConfigURIReferences getConfigURIReferences(PipelineContext context) {
                // Check if config is external
                if (localConfigURIReferences != null)
                    return localConfigURIReferences;

                // Make sure the config input is cacheable
                KeyValidity keyValidity = getInputKeyValidity(context, INPUT_CONFIG);
                if (keyValidity == null)
                    return null;

                // Try to find resource manager key in cache
                ConfigURIReferences config = (ConfigURIReferences) ObjectCache.instance().findValid(context, keyValidity.key, keyValidity.validity);
                if (logger.isDebugEnabled()) {
                    if (config != null)
                        logger.debug("Config found: " + config.toString());
                    else
                        logger.debug("Config not found");
                }
                return config;
            }
        };
        addOutput(name, output);
        return output;
    }

    private interface ResourceHandler {
        public Object getValidity() throws IOException;

        public String getResourceContentType() throws IOException;

        public String getConnectionEncoding() throws IOException;

        public void destroy() throws IOException;

        public void readHTML(ContentHandler output) throws IOException;

        public void readText(ContentHandler output, String contentType) throws IOException;

        public void readXML(PipelineContext pipelineContext, ContentHandler output) throws IOException;

        public void readBinary(ContentHandler output, String contentType) throws IOException;
    }

    private static class OXFResourceHandler implements ResourceHandler {
        private Config config;
        private String resourceManagerKey;
        private InputStream inputStream;

        public OXFResourceHandler(Config config) {
            this.config = config;
        }

        public String getResourceContentType() throws IOException {
            // We generally don't know the "connection" content-type
            return null;
        }

        public String getConnectionEncoding() throws IOException {
            // We generally don't know the "connection" encoding
            // NOTE: We could know, if the underlying protocol was for example HTTP. But we may
            // want to abstract that anyway, so that the behavior is consistent whatever the sandbox
            // is.
            return null;
        }

        public Object getValidity() throws IOException {
            getKey();
            if (logger.isDebugEnabled())
                logger.debug("OXF Protocol: Using ResourceManager for key " + getKey());

            long result = ResourceManagerWrapper.instance().lastModified(getKey(), false);
            // Zero and negative values often have a special meaning, make sure to normalize here
            return (result <= 0) ? null : new Long(result);
        }

        public void destroy() throws IOException {
            if (inputStream != null) {
                inputStream.close();
            }
        }

        private String getExternalEncoding() throws IOException {
            if (config.isForceEncoding())
                return config.getEncoding();

            String connectionEncoding = getConnectionEncoding();
            if (!config.isIgnoreConnectionEncoding() && connectionEncoding != null)
                return connectionEncoding;

            String userEncoding = config.getEncoding();
            if (userEncoding != null)
                return userEncoding;

            return null;
        }

        public void readHTML(ContentHandler output) throws IOException {
            inputStream = ResourceManagerWrapper.instance().getContentAsStream(getKey());
            URLResourceHandler.readHTML(inputStream, config.getTidyConfig(), getExternalEncoding(), output);
        }

        public void readText(ContentHandler output, String contentType) throws IOException {
            inputStream = ResourceManagerWrapper.instance().getContentAsStream(getKey());
            ProcessorUtils.readText(inputStream, getExternalEncoding(), output, contentType);
        }

        public void readXML(PipelineContext pipelineContext, ContentHandler output) throws IOException {
            if (getExternalEncoding() != null) {
                // The encoding is set externally, either forced by the user, or set by the connection
                inputStream = ResourceManagerWrapper.instance().getContentAsStream(getKey());
                XMLUtils.readerToSAX(new InputStreamReader(inputStream, getExternalEncoding()), config.getURL().toExternalForm(), output, config.isValidating(), config.isHandleXInclude());
            } else {
                // Regular case, the resource manager does the job and autodetects the encoding
                ResourceManagerWrapper.instance().getContentAsSAX(getKey(), output, config.isValidating(), config.isHandleXInclude());
            }
        }

        public void readBinary(ContentHandler output, String contentType) throws IOException {
            inputStream = ResourceManagerWrapper.instance().getContentAsStream(getKey());
            ProcessorUtils.readBinary(inputStream, output, contentType);
        }

        private String getKey() {
            if (resourceManagerKey == null)
                resourceManagerKey = config.getURL().getFile();
            return resourceManagerKey;
        }
    }

    private static class LocalXIncludeListener implements XIncludeHandler.XIncludeListener {

        private URIReferences uriReferences;

        public void inclusion(String base, String href) {
            if (uriReferences == null)
                uriReferences = new URIReferences();
            uriReferences.references.add(new URIReference(base, href));
        }

        public void updateCache(PipelineContext pipelineContext, ConfigURIReferences configURIReferences) {
            configURIReferences.uriReferences = uriReferences;
        }
    }

    private static class URLResourceHandler implements ResourceHandler {
        private Config config;
        private URLConnection urlConn;

        public URLResourceHandler(Config config) {
            this.config = config;
        }

        public String getResourceContentType() throws IOException {
            // Return null for file protocol, as it returns incorrect content types
            if ("file".equals(config.getURL().getProtocol()))
                return null;
            // Otherwise, try URLConnection
            openConnection();
            return NetUtils.getContentTypeMediaType(urlConn.getContentType());
        }

        public String getConnectionEncoding() throws IOException {
            // Return null for file protocol, as it returns incorrect content types
            if ("file".equals(config.getURL().getProtocol()))
                return null;
            // Otherwise, try URLConnection
            openConnection();
            return NetUtils.getContentTypeCharset(urlConn.getContentType());
        }

        public Object getValidity() throws IOException {
            openConnection();
            long lastModified = NetUtils.getLastModified(urlConn);
            // Zero and negative values often have a special meaning, make sure to normalize here
            return lastModified <= 0 ? null : new Long(lastModified);
        }

        public void destroy() throws IOException {
            // Make sure the connection is closed because when
            // getting the last modified date, the stream is
            // actually opened. When using the file: protocol, the
            // file can be locked on disk.
            if (urlConn != null && urlConn.getInputStream() != null) {
                urlConn.getInputStream().close();
            }
        }

        private void openConnection() throws IOException {
            if (urlConn == null) {
                urlConn = config.getURL().openConnection();
                Map headers = config.getHeaders();
                if (headers != null) {
                    for (Iterator i = headers.keySet().iterator(); i.hasNext();) {
                        String name = (String) i.next();
                        String value = (String) config.getHeaders().get(name);
                        urlConn.setRequestProperty(name, value);
                    }
                }
            }
        }

        private String getExternalEncoding() throws IOException {
            if (config.isForceEncoding())
                return config.getEncoding();

            String connectionEncoding = getConnectionEncoding();
            if (!config.isIgnoreConnectionEncoding() && connectionEncoding != null)
                return connectionEncoding;

            String userEncoding = config.getEncoding();
            if (userEncoding != null)
                return userEncoding;

            return null;
        }

        public void readHTML(ContentHandler output) throws IOException {
            openConnection();
            readHTML(urlConn.getInputStream(), config.getTidyConfig(), getExternalEncoding(), output);
        }

        public void readText(ContentHandler output, String contentType) throws IOException {
            openConnection();
            ProcessorUtils.readText(urlConn.getInputStream(), getExternalEncoding(), output, contentType);
        }

        public void readBinary(ContentHandler output, String contentType) throws IOException {
            openConnection();
            ProcessorUtils.readBinary(urlConn.getInputStream(), output, contentType);
        }

        public void readXML(PipelineContext pipelineContext, ContentHandler output) throws IOException {
            openConnection();
            // Read the resource from the resource manager and parse it as XML
            try {
                SAXParser parser = XMLUtils.newSAXParser(config.isValidating(), config.isHandleXInclude());
                XMLReader reader = parser.getXMLReader();
                reader.setContentHandler(output);
                reader.setEntityResolver(XMLUtils.ENTITY_RESOLVER);
                reader.setErrorHandler(XMLUtils.ERROR_HANDLER);
                InputSource inputSource;
                if (getExternalEncoding() != null) {
                    // The encoding is set externally, either force by the user, or set by the connection
                    inputSource = new InputSource(new InputStreamReader(urlConn.getInputStream(), getExternalEncoding()));
                } else {
                    // This is the regular case where the XML parser autodetects the encoding
                    inputSource = new InputSource(urlConn.getInputStream());
                }
                inputSource.setSystemId(config.getURL().toExternalForm());
                reader.parse(inputSource);
            } catch (SAXException e) {
                throw new OXFException(e);
            }
        }

        public static void readHTML(InputStream is, TidyConfig tidyConfig, String encoding, ContentHandler output) {
            Tidy tidy = new Tidy();
//          tidy.setOnlyErrors(false);
            tidy.setShowWarnings(tidyConfig.isShowWarnings());
            tidy.setQuiet(tidyConfig.isQuiet());

            // Set encoding
            // If the encoding is null, we get a default
            tidy.setInputEncoding(TidyConfig.getTidyEncoding(encoding));

            // Parse and output to SAXResult
            Document document = tidy.parseDOM(is, null);
            try {
                Transformer transformer = TransformerUtils.getIdentityTransformer();
                transformer.transform(new DOMSource(document), new SAXResult(output));
            } catch (TransformerException e) {
                throw new OXFException(e);
            }
        }

    }

    private static class URIReference {
        public URIReference(String context, String spec) {
            this.context = context;
            this.spec = spec;
        }

        public String context;
        public String spec;
    }

    private static class URIReferences {
        public List references = new ArrayList();
    }

    private class URLGeneratorState {

        private Map map;

        public void setLastModified(String urlString, Long lastModified) {
            if (map == null)
                map = new HashMap();
            map.put(urlString, lastModified == null ? (Object) "" : lastModified);
        }

        public boolean isLastModifiedSet(String urlString) {
            if (map == null)
                return false;
            return map.get(urlString) != null;
        }

        public Long getLastModified(String urlString) {
            final Object result = map.get(urlString);
            return (result instanceof String) ? null : (Long) result;
        }
    }

    private void makeSureStateIsSet(PipelineContext pipelineContext) {
        if (!hasState(pipelineContext))
            setState(pipelineContext, new URLGeneratorState());
    }

    public void reset(PipelineContext pipelineContext) {
        makeSureStateIsSet(pipelineContext);
    }
}