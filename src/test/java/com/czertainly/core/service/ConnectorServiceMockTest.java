package com.czertainly.core.service;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.czertainly.core.dao.entity.Endpoint;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.dao.repository.FunctionGroupRepository;
import com.czertainly.core.service.impl.ConnectorServiceImpl;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;

import com.czertainly.core.dao.entity.FunctionGroup;
import com.czertainly.api.ConnectorApiClient;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.connector.ConnectorDto;
import com.czertainly.api.model.connector.FunctionGroupCode;
import com.czertainly.api.model.connector.InfoResponse;

@SpringBootTest
public class ConnectorServiceMockTest {

    @Mock
    private ConnectorApiClient connectorApiClient;

    @Mock
    private ConnectorRepository connectorRepository;

    @Mock
    private FunctionGroupRepository functionGroupRepository;

    @InjectMocks
    private ConnectorService connectorService = new ConnectorServiceImpl();

    private Endpoint endpoint1, endpoint2, endpoint3;


    @BeforeEach
    public void setUp() {
        endpoint1 = new Endpoint();
        endpoint1.setName("endpoint1");
        endpoint1.setContext("/e1");
        endpoint1.setMethod("GET");
        endpoint1.setRequired(true);

        endpoint2 = new Endpoint();
        endpoint2.setName("endpoint2");
        endpoint2.setContext("/e2");
        endpoint2.setMethod("GET");
        endpoint2.setRequired(true);

        endpoint3 = new Endpoint();
        endpoint3.setName("endpoint3");
        endpoint3.setContext("/e3");
        endpoint3.setMethod("GET");
        endpoint3.setRequired(false);

        FunctionGroup functionGroup = new FunctionGroup();
        functionGroup.setCode(FunctionGroupCode.CREDENTIAL_PROVIDER);
        functionGroup.getEndpoints().add(endpoint1);
        functionGroup.getEndpoints().add(endpoint2);
        functionGroup.getEndpoints().add(endpoint3);

        Mockito.when(functionGroupRepository.findByCode(Mockito.any())).thenReturn(Optional.empty());
        Mockito.when(functionGroupRepository.findByCode(Mockito.eq(FunctionGroupCode.CREDENTIAL_PROVIDER))).thenReturn(Optional.of(functionGroup));
    }

    @Test
    public void testConnect_UnknownFunctionGroup() throws Exception {

        List<InfoResponse> connectorFunctions = new ArrayList<>();
        List<String> types = List.of("default");
        connectorFunctions.add(new InfoResponse(types, FunctionGroupCode.CA_CONNECTOR, Collections.singletonList(endpoint1.mapToDto())));
        Mockito.when(connectorApiClient.listSupportedFunctions(Mockito.any())).thenReturn(connectorFunctions);

        ConnectorDto request = new ConnectorDto();
        request.setUrl("http://localhost");

        Assertions.assertThrows(ValidationException.class, () ->
                connectorService.connect(request)
        );
    }

    @Test
    public void testConnect_RequiredEndpointNotSupported() throws Exception {

        List<InfoResponse> connectorFunctions = new ArrayList<>();
        List<String> types = List.of("default");
        connectorFunctions.add(new InfoResponse(types, FunctionGroupCode.CREDENTIAL_PROVIDER, Collections.singletonList(endpoint1.mapToDto())));
        Mockito.when(connectorApiClient.listSupportedFunctions(Mockito.any())).thenReturn(connectorFunctions);

        ConnectorDto request = new ConnectorDto();
        request.setUrl("http://localhost");
        Assertions.assertThrows(ValidationException.class, () ->
                connectorService.connect(request)
        );
    }

    @Test
    public void testConnect_Successful() throws Exception {

        List<InfoResponse> connectorFunctions = new ArrayList<>();
        List<String> types = List.of("default");
        connectorFunctions.add(new InfoResponse(types, FunctionGroupCode.CREDENTIAL_PROVIDER, Arrays.asList(endpoint1.mapToDto(), endpoint2.mapToDto())));
        Mockito.when(connectorApiClient.listSupportedFunctions(Mockito.any())).thenReturn(connectorFunctions);

        ConnectorDto request = new ConnectorDto();
        request.setUrl("http://localhost");

        connectorService.connect(request);
    }
}
