package com.czertainly.core.messaging.proxy.handler;

import com.czertainly.api.clients.mq.model.ProxyResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MessageTypeHandlerRegistry}.
 * Tests handler registration, exact/wildcard pattern matching, and dispatch logic.
 */
class MessageTypeHandlerRegistryTest {

    // ==================== Initialization Tests ====================

    @Test
    void init_registersAllHandlers() {
        List<MessageTypeResponseHandler> handlers = List.of(
                createHandler("type.a"),
                createHandler("type.b"),
                createHandler("type.c")
        );

        MessageTypeHandlerRegistry registry = new MessageTypeHandlerRegistry(handlers);
        registry.init();

        assertThat(registry.getHandlerCount()).isEqualTo(3);
    }

    @Test
    void init_withNullHandlerList_initializesEmptyRegistry() {
        MessageTypeHandlerRegistry registry = new MessageTypeHandlerRegistry(null);
        registry.init();

        assertThat(registry.getHandlerCount()).isZero();
    }

    @Test
    void init_withEmptyHandlerList_initializesEmptyRegistry() {
        MessageTypeHandlerRegistry registry = new MessageTypeHandlerRegistry(List.of());
        registry.init();

        assertThat(registry.getHandlerCount()).isZero();
    }

    @Test
    void init_withBlankMessageType_skipsHandler() {
        List<MessageTypeResponseHandler> handlers = List.of(
                createHandler("valid.type"),
                createHandler(""),
                createHandler("another.type")
        );

        MessageTypeHandlerRegistry registry = new MessageTypeHandlerRegistry(handlers);
        registry.init();

        assertThat(registry.getHandlerCount()).isEqualTo(2);
    }

    @Test
    void init_withNullMessageType_skipsHandler() {
        List<MessageTypeResponseHandler> handlers = new ArrayList<>();
        handlers.add(createHandler("valid.type"));
        handlers.add(createHandler(null));
        handlers.add(createHandler("another.type"));

        MessageTypeHandlerRegistry registry = new MessageTypeHandlerRegistry(handlers);
        registry.init();

        assertThat(registry.getHandlerCount()).isEqualTo(2);
    }

    @Test
    void init_withDuplicateMessageType_keepsFirstHandler() {
        MessageTypeResponseHandler firstHandler = createHandler("duplicate.type");
        MessageTypeResponseHandler secondHandler = createHandler("duplicate.type");

        List<MessageTypeResponseHandler> handlers = List.of(firstHandler, secondHandler);

        MessageTypeHandlerRegistry registry = new MessageTypeHandlerRegistry(handlers);
        registry.init();

        // Only one should be registered
        assertThat(registry.getHandlerCount()).isEqualTo(1);

        // Verify first handler is kept
        ProxyResponse response = createResponse("duplicate.type");
        registry.dispatch(response);
        verify(firstHandler).handleResponse(response);
        verify(secondHandler, never()).handleResponse(any());
    }

    // ==================== hasHandler Tests ====================

    @Test
    void hasHandler_withExactMatch_returnsTrue() {
        MessageTypeHandlerRegistry registry = new MessageTypeHandlerRegistry(
                List.of(createHandler("certificate.issued"))
        );
        registry.init();

        assertThat(registry.hasHandler("certificate.issued")).isTrue();
    }

    @Test
    void hasHandler_withWildcardMatch_returnsTrue() {
        MessageTypeHandlerRegistry registry = new MessageTypeHandlerRegistry(
                List.of(createHandler("POST:/v1/certificates/*"))
        );
        registry.init();

        assertThat(registry.hasHandler("POST:/v1/certificates/issue")).isTrue();
        assertThat(registry.hasHandler("POST:/v1/certificates/revoke")).isTrue();
        assertThat(registry.hasHandler("POST:/v1/certificates/anything")).isTrue();
    }

    @Test
    void hasHandler_withNoMatch_returnsFalse() {
        MessageTypeHandlerRegistry registry = new MessageTypeHandlerRegistry(
                List.of(createHandler("certificate.issued"))
        );
        registry.init();

        assertThat(registry.hasHandler("certificate.revoked")).isFalse();
        assertThat(registry.hasHandler("unknown.type")).isFalse();
    }

    @Test
    void hasHandler_withNullMessageType_returnsFalse() {
        MessageTypeHandlerRegistry registry = new MessageTypeHandlerRegistry(
                List.of(createHandler("certificate.issued"))
        );
        registry.init();

        assertThat(registry.hasHandler(null)).isFalse();
    }

    @Test
    void hasHandler_wildcardDoesNotMatchPartialPrefix() {
        MessageTypeHandlerRegistry registry = new MessageTypeHandlerRegistry(
                List.of(createHandler("POST:/v1/certificates/*"))
        );
        registry.init();

        // Should not match because path doesn't start with full prefix
        assertThat(registry.hasHandler("POST:/v1/cert")).isFalse();
        assertThat(registry.hasHandler("GET:/v1/certificates/issue")).isFalse();
    }

    // ==================== dispatch - Exact Match Tests ====================

    @Test
    void dispatch_withExactMatch_invokesHandler() {
        MessageTypeResponseHandler handler = createHandler("certificate.issued");
        MessageTypeHandlerRegistry registry = new MessageTypeHandlerRegistry(List.of(handler));
        registry.init();

        ProxyResponse response = createResponse("certificate.issued");
        boolean result = registry.dispatch(response);

        assertThat(result).isTrue();
        verify(handler).handleResponse(response);
    }

    @Test
    void dispatch_withExactMatch_returnsTrue() {
        MessageTypeHandlerRegistry registry = new MessageTypeHandlerRegistry(
                List.of(createHandler("certificate.issued"))
        );
        registry.init();

        assertThat(registry.dispatch(createResponse("certificate.issued"))).isTrue();
    }

    // ==================== dispatch - Wildcard Match Tests ====================

    @Test
    void dispatch_withWildcardMatch_invokesHandler() {
        MessageTypeResponseHandler handler = createHandler("POST:/v1/certificates/*");
        MessageTypeHandlerRegistry registry = new MessageTypeHandlerRegistry(List.of(handler));
        registry.init();

        ProxyResponse response = createResponse("POST:/v1/certificates/issue");
        boolean result = registry.dispatch(response);

        assertThat(result).isTrue();
        verify(handler).handleResponse(response);
    }

    @Test
    void dispatch_longerPrefixWins_overShorterPrefix() {
        MessageTypeResponseHandler shortHandler = createHandler("POST:/*");
        MessageTypeResponseHandler longHandler = createHandler("POST:/v1/certificates/*");

        MessageTypeHandlerRegistry registry = new MessageTypeHandlerRegistry(
                List.of(shortHandler, longHandler)
        );
        registry.init();

        ProxyResponse response = createResponse("POST:/v1/certificates/issue");
        registry.dispatch(response);

        // Longer prefix should win
        verify(longHandler).handleResponse(response);
        verify(shortHandler, never()).handleResponse(any());
    }

    @Test
    void dispatch_exactMatchTakesPrecedence_overWildcard() {
        MessageTypeResponseHandler wildcardHandler = createHandler("POST:/v1/certificates/*");
        MessageTypeResponseHandler exactHandler = createHandler("POST:/v1/certificates/issue");

        MessageTypeHandlerRegistry registry = new MessageTypeHandlerRegistry(
                List.of(wildcardHandler, exactHandler)
        );
        registry.init();

        ProxyResponse response = createResponse("POST:/v1/certificates/issue");
        registry.dispatch(response);

        // Exact match should take precedence
        verify(exactHandler).handleResponse(response);
        verify(wildcardHandler, never()).handleResponse(any());
    }

    // ==================== dispatch - No Match Tests ====================

    @Test
    void dispatch_withNoMatch_returnsFalse() {
        MessageTypeHandlerRegistry registry = new MessageTypeHandlerRegistry(
                List.of(createHandler("certificate.issued"))
        );
        registry.init();

        assertThat(registry.dispatch(createResponse("unknown.type"))).isFalse();
    }

    @Test
    void dispatch_withNullResponse_returnsFalse() {
        MessageTypeHandlerRegistry registry = new MessageTypeHandlerRegistry(
                List.of(createHandler("certificate.issued"))
        );
        registry.init();

        assertThat(registry.dispatch(null)).isFalse();
    }

    @Test
    void dispatch_withNullMessageType_returnsFalse() {
        MessageTypeHandlerRegistry registry = new MessageTypeHandlerRegistry(
                List.of(createHandler("certificate.issued"))
        );
        registry.init();

        assertThat(registry.dispatch(createResponse(null))).isFalse();
    }

    // ==================== Handler Error Tests ====================

    @Test
    void dispatch_onHandlerException_returnsFalseAndLogsError() {
        MessageTypeResponseHandler failingHandler = mock(MessageTypeResponseHandler.class);
        when(failingHandler.getMessageType()).thenReturn("failing.type");
        doThrow(new RuntimeException("Handler failed")).when(failingHandler).handleResponse(any());

        MessageTypeHandlerRegistry registry = new MessageTypeHandlerRegistry(List.of(failingHandler));
        registry.init();

        ProxyResponse response = createResponse("failing.type");
        boolean result = registry.dispatch(response);

        assertThat(result).isFalse();
        verify(failingHandler).handleResponse(response);
    }

    // ==================== Pattern Matching Edge Cases ====================

    @Test
    void dispatch_wildcardOnlyMatchesAtEnd() {
        MessageTypeHandlerRegistry registry = new MessageTypeHandlerRegistry(
                List.of(createHandler("POST:/v1/*/certificates"))
        );
        registry.init();

        // Pattern doesn't end with /*, so it's treated as exact match
        assertThat(registry.hasHandler("POST:/v1/something/certificates")).isFalse();
        assertThat(registry.hasHandler("POST:/v1/*/certificates")).isTrue();
    }

    @Test
    void dispatch_emptyPrefixWildcard() {
        MessageTypeHandlerRegistry registry = new MessageTypeHandlerRegistry(
                List.of(createHandler("/*"))
        );
        registry.init();

        // Empty prefix wildcard should match anything starting with empty string
        assertThat(registry.hasHandler("/anything")).isTrue();
        assertThat(registry.hasHandler("/v1/certificates")).isTrue();
    }

    @Test
    void dispatch_multipleWildcards_longestPrefixWins() {
        MessageTypeResponseHandler level1 = createHandler("GET:/*");
        MessageTypeResponseHandler level2 = createHandler("GET:/v1/*");
        MessageTypeResponseHandler level3 = createHandler("GET:/v1/certificates/*");

        MessageTypeHandlerRegistry registry = new MessageTypeHandlerRegistry(
                List.of(level1, level2, level3)
        );
        registry.init();

        // Test different paths - longest matching prefix should win
        ProxyResponse response1 = createResponse("GET:/v1/certificates/123");
        registry.dispatch(response1);
        verify(level3).handleResponse(response1);

        ProxyResponse response2 = createResponse("GET:/v1/keys/456");
        registry.dispatch(response2);
        verify(level2).handleResponse(response2);

        ProxyResponse response3 = createResponse("GET:/other/path");
        registry.dispatch(response3);
        verify(level1).handleResponse(response3);
    }

    // ==================== getHandlerCount Tests ====================

    @Test
    void getHandlerCount_returnsCorrectCount() {
        MessageTypeHandlerRegistry registry = new MessageTypeHandlerRegistry(
                List.of(
                        createHandler("type.a"),
                        createHandler("type.b"),
                        createHandler("type.c")
                )
        );
        registry.init();

        assertThat(registry.getHandlerCount()).isEqualTo(3);
    }

    @Test
    void getHandlerCount_afterInit_withNullTypes_excludesInvalid() {
        List<MessageTypeResponseHandler> handlers = new ArrayList<>();
        handlers.add(createHandler("valid.type"));
        handlers.add(createHandler(null));
        handlers.add(createHandler(""));

        MessageTypeHandlerRegistry registry = new MessageTypeHandlerRegistry(handlers);
        registry.init();

        assertThat(registry.getHandlerCount()).isEqualTo(1);
    }

    // ==================== Helper Methods ====================

    private MessageTypeResponseHandler createHandler(String messageType) {
        MessageTypeResponseHandler handler = mock(MessageTypeResponseHandler.class);
        when(handler.getMessageType()).thenReturn(messageType);
        return handler;
    }

    private ProxyResponse createResponse(String messageType) {
        return ProxyResponse.builder()
                .correlationId("test-corr")
                .messageType(messageType)
                .statusCode(200)
                .timestamp(Instant.now())
                .build();
    }
}
