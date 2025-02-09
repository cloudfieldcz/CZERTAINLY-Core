package com.czertainly.core.service.impl;

import com.czertainly.core.aop.AuditLogged;
import com.czertainly.api.core.modal.ObjectType;
import com.czertainly.api.core.modal.OperationType;
import com.czertainly.core.service.ConnectorRegistrationService;
import com.czertainly.core.service.ConnectorService;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.connector.ConnectorDto;
import com.czertainly.api.model.connector.ConnectorStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

@Service
@Transactional
@Secured({"ROLE_ANONYMOUS"})
public class ConnectorRegistrationServiceImpl implements ConnectorRegistrationService {
    private static final Logger logger = LoggerFactory.getLogger(ConnectorRegistrationServiceImpl.class);

    @Autowired
    private ConnectorService connectorService;

    @Override
    @AuditLogged(originator = ObjectType.CONNECTOR, affected = ObjectType.CONNECTOR, operation = OperationType.CREATE)
    public ConnectorDto registerConnector(ConnectorDto request) throws AlreadyExistException, NotFoundException {
        connectorService.validateConnector(request.getFunctionGroups(), "uuid");
        logger.info("Connector {} successfully validated.", request.getName());

        ConnectorDto connector = connectorService.createConnector(request, ConnectorStatus.WAITING_FOR_APPROVAL);
        logger.info("Connector {} registered and is waiting for approval.", request.getName());

        return connector;
    }
}
