package com.czertainly.core.messaging.proxy.handler;

import com.czertainly.api.clients.mq.model.ProxyResponse;

/**
 * Handler interface for processing proxy responses by messageType.
 *
 * <p>Implement this interface to handle fire-and-forget style responses where any
 * instance can process the response. The handler is registered with a specific
 * messageType pattern, and when a response arrives with a matching messageType,
 * it will be dispatched to this handler instead of using correlation-based routing.</p>
 *
 * <p>Use cases:</p>
 * <ul>
 *   <li>Certificate issuance results that need to be stored in the database</li>
 *   <li>Discovery results that should be processed by any available instance</li>
 *   <li>Long-running operations where the response can be handled asynchronously</li>
 * </ul>
 *
 * <p>Pattern matching:</p>
 * <ul>
 *   <li>Exact match: "certificate.issued" matches only "certificate.issued"</li>
 *   <li>Wildcard: "POST:/v1/certificates/*" matches "POST:/v1/certificates/issue"</li>
 * </ul>
 */
public interface MessageTypeResponseHandler {

    /**
     * Get the message type pattern this handler processes.
     * Can be an exact match or a pattern with wildcard at the end.
     *
     * @return Message type identifier (e.g., "certificate.issued", "POST:/v1/certificates/*")
     */
    String getMessageType();

    /**
     * Process the proxy response.
     *
     * @param response The proxy response to handle
     */
    void handleResponse(ProxyResponse response);
}
