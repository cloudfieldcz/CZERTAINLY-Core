package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.proxy.ProxyRequestDto;
import com.czertainly.api.model.client.proxy.ProxyUpdateRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.proxy.ProxyDto;
import com.czertainly.api.model.core.proxy.ProxyStatus;
import com.czertainly.core.dao.entity.Proxy;
import com.czertainly.core.dao.repository.ProxyRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

class ProxyServiceTest extends BaseSpringBootTest {

    private static final String PROXY_NAME = "testProxy1";

    @Autowired
    private ProxyService proxyService;

    @Autowired
    private ProxyRepository proxyRepository;

    private Proxy proxy;

    @BeforeEach
    public void setUp() {
        proxy = new Proxy();
        proxy.setName(PROXY_NAME);
        proxy.setDescription("Test Proxy 1");
        proxy.setCode("TEST_PROXY_1");
        proxy.setStatus(ProxyStatus.CONNECTED);
        proxy = proxyRepository.save(proxy);
    }

    @Test
    void testListProxies() throws NotFoundException {
        List<ProxyDto> proxys = proxyService.listProxies(SecurityFilter.create(), Optional.empty());
        Assertions.assertNotNull(proxys);
        Assertions.assertFalse(proxys.isEmpty());
        Assertions.assertEquals(1, proxys.size());
        Assertions.assertEquals(proxy.getUuid().toString(), proxys.getFirst().getUuid());
    }

    @Test
    void testListProxiesByStatus() throws NotFoundException {
        List<ProxyDto> proxys = proxyService.listProxies(
            SecurityFilter.create(),
            Optional.of(ProxyStatus.CONNECTED)
        );
        Assertions.assertNotNull(proxys);
        Assertions.assertFalse(proxys.isEmpty());
        Assertions.assertEquals(1, proxys.size());
        Assertions.assertEquals(proxy.getUuid().toString(), proxys.getFirst().getUuid());
    }

    @Test
    void testListProxiesByStatus_notFound() throws NotFoundException {
        List<ProxyDto> proxys = proxyService.listProxies(
            SecurityFilter.create(),
            Optional.of(ProxyStatus.FAILED)
        );
        Assertions.assertNotNull(proxys);
        Assertions.assertTrue(proxys.isEmpty());
    }

    @Test
    void testGetProxy() throws NotFoundException {
        ProxyDto dto = proxyService.getProxy(proxy.getSecuredUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(proxy.getUuid().toString(), dto.getUuid());
        Assertions.assertEquals(proxy.getName(), dto.getName());
    }

    @Test
    void testGetProxy_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> proxyService.getProxy(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void testAddProxy() throws AlreadyExistException {
        ProxyRequestDto request = new ProxyRequestDto();
        request.setName("testProxy2");
        request.setDescription("Test Proxy 2");

        ProxyDto dto = proxyService.createProxy(request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getName(), dto.getName());
    }

    @Test
    void testAddProxy_validationFail() {
        ProxyRequestDto request = new ProxyRequestDto();
        Assertions.assertThrows(ValidationException.class, () -> proxyService.createProxy(request));
    }

    @Test
    void testAddProxy_alreadyExist() {
        ProxyRequestDto request = new ProxyRequestDto();
        request.setName(PROXY_NAME);
        Assertions.assertThrows(AlreadyExistException.class, () -> proxyService.createProxy(request));
    }

    @Test
    void testEditProxy() throws NotFoundException {
        ProxyUpdateRequestDto request = new ProxyUpdateRequestDto();
        request.setDescription("Updated Test Proxy 1");

        ProxyDto dto = proxyService.editProxy(proxy.getSecuredUuid(), request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(proxy.getUuid().toString(), dto.getUuid());
        Assertions.assertEquals(proxy.getName(), dto.getName());
        Assertions.assertEquals("Updated Test Proxy 1", dto.getDescription());
    }

    @Test
    void testEditProxy_notFound() {
        ProxyUpdateRequestDto request = new ProxyUpdateRequestDto();
        request.setDescription("Updated Description");
        Assertions.assertThrows(NotFoundException.class, () -> proxyService.editProxy(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"), request));
    }

    @Test
    void testGetObjectsForResource() {
        List<NameAndUuidDto> dtos = proxyService.listResourceObjects(SecurityFilter.create());
        Assertions.assertEquals(1, dtos.size());
    }
}
