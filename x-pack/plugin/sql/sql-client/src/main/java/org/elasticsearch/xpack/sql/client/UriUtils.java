/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.sql.client;

import java.net.URI;
import java.net.URISyntaxException;

public final class UriUtils {
    private UriUtils() {

    }

    /**
     * Parses the URL provided by the user and
     */
    public static URI parseURI(String connectionString, URI defaultURI) {
        final URI uri = parseWithNoScheme(connectionString);
        // Repack the connection string with provided default elements - where missing from the original string - and reparse into a URI.
        final String path = "".equals(uri.getPath()) ? defaultURI.getPath() : uri.getPath();
        final String rawQuery = uri.getQuery() == null ? defaultURI.getRawQuery() : uri.getRawQuery();
        final String rawFragment = uri.getFragment() == null ? defaultURI.getRawFragment() : uri.getRawFragment();
        final int port = uri.getPort() < 0 ? defaultURI.getPort() : uri.getPort();
        try {
            // The query part is attached in original "raw" format, to preserve the escaping of characters. This is needed since any
            // escaped query structure characters (`&` and `=`) wouldn't remain escaped when passed back through the URI constructor
            // (since they are legal in the query part), and that would later interfere with correctly parsing the attributes.
            // And same with escaped `#` chars in the fragment part.
            String connStr = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), port, path, null, null).toString();
            if (StringUtils.hasLength(rawQuery)) {
                connStr += "?" + rawQuery;
            }
            if (StringUtils.hasLength(rawFragment)) {
                connStr += "#" + rawFragment;
            }
            return new URI(connStr);
        } catch (URISyntaxException e) {
            // should only happen if the defaultURI is malformed
            throw new IllegalArgumentException("Invalid connection configuration [" + connectionString + "]: " + e.getMessage(), e);
        }
    }

    private static URI parseWithNoScheme(String connectionString) {
        URI uri;
        // check if URI can be parsed correctly without adding scheme
        // if the connection string is in format host:port or just host, the host is going to be null
        // if the connection string contains IPv6 localhost [::1] the parsing will fail
        URISyntaxException firstException = null;
        try {
            uri = new URI(connectionString);
            if (uri.getHost() == null || uri.getScheme() == null) {
                uri = null;
            }
        } catch (URISyntaxException e) {
            firstException = e;
            uri = null;
        }

        if (uri == null) {
            // We couldn't parse URI without adding scheme, let's try again with scheme this time
            try {
                return new URI("http://" + connectionString);
            } catch (URISyntaxException e) {
                IllegalArgumentException ie =
                    new IllegalArgumentException("Invalid connection configuration [" + connectionString + "]: " + e.getMessage(), e);
                if (firstException != null) {
                    ie.addSuppressed(firstException);
                }
                throw ie;
            }
        } else {
            // We managed to parse URI and all necessary pieces are present, let's make sure the scheme is correct
            if ("http".equals(uri.getScheme()) == false && "https".equals(uri.getScheme()) == false) {
                throw new IllegalArgumentException(
                        "Invalid connection configuration [" + connectionString + "]: Only http and https protocols are supported");
            }
            return uri;
        }
    }

    /**
     * Removes the query part of the URI
     */
    public static URI removeQuery(URI uri, String connectionString, URI defaultURI) {
        try {
            return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(), null, defaultURI.getFragment());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid connection configuration [" + connectionString + "]: " + e.getMessage(), e);
        }
    }

    public static URI appendSegmentToPath(URI uri, String segment) {
        if (uri == null) {
            throw new IllegalArgumentException("URI must not be null");
        }
        if (segment == null || segment.isEmpty() || "/".equals(segment)) {
            return uri;
        }
        
        String path = uri.getPath();
        String concatenatedPath = "";
        String cleanSegment = segment.startsWith("/") ? segment.substring(1) : segment;
        
        if (path == null || path.isEmpty()) {
            path = "/";
        }

        if (path.charAt(path.length() - 1) == '/') {
            concatenatedPath = path + cleanSegment;
        } else {
            concatenatedPath = path + "/" + cleanSegment;
        }
        try {
            return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), concatenatedPath,
                    uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid segment [" + segment + "] for URI [" + uri + "]: " + e.getMessage(), e);
        }
    }
}
