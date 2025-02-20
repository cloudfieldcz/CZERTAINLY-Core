package com.czertainly.core.api.v2.client;

import com.czertainly.api.core.v2.interfaces.ClientOperationController;
import com.czertainly.api.core.v2.model.ClientCertificateDataResponseDto;
import com.czertainly.api.core.v2.model.ClientCertificateRenewRequestDto;
import com.czertainly.api.core.v2.model.ClientCertificateRevocationDto;
import com.czertainly.api.core.v2.model.ClientCertificateSignRequestDto;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.AttributeDefinition;
import com.czertainly.core.service.v2.ClientOperationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.cert.CertificateException;
import java.util.List;

@RestController("clientOperationControllerV2")
public class ClientOperationControllerImpl implements ClientOperationController {

    @Autowired
    private ClientOperationService clientOperationService;

    @Override
    public List<AttributeDefinition> listIssueCertificateAttributes(
            @PathVariable String raProfileName) throws NotFoundException, ConnectorException {
        return clientOperationService.listIssueCertificateAttributes(raProfileName);
    }

    @Override
    public boolean validateIssueCertificateAttributes(
            @PathVariable String raProfileName,
            @RequestBody List<AttributeDefinition> attributes) throws NotFoundException, ConnectorException, ValidationException {
        return clientOperationService.validateIssueCertificateAttributes(raProfileName, attributes);
    }

    @Override
    public ClientCertificateDataResponseDto issueCertificate(
            @PathVariable String raProfileName,
            @RequestBody ClientCertificateSignRequestDto request) throws NotFoundException, ConnectorException, AlreadyExistException, CertificateException {
        return clientOperationService.issueCertificate(raProfileName, request);
    }

    @Override
    public ClientCertificateDataResponseDto renewCertificate(
            @PathVariable String raProfileName,
            @PathVariable String certificateId,
            @RequestBody ClientCertificateRenewRequestDto request) throws NotFoundException, ConnectorException, AlreadyExistException, CertificateException {
        return clientOperationService.renewCertificate(raProfileName, certificateId, request);
    }

    @Override
    public List<AttributeDefinition> listRevokeCertificateAttributes(
            @PathVariable String raProfileName) throws NotFoundException, ConnectorException {
        return clientOperationService.listRevokeCertificateAttributes(raProfileName);
    }

    @Override
    public boolean validateRevokeCertificateAttributes(
            @PathVariable String raProfileName,
            @RequestBody List<AttributeDefinition> attributes) throws NotFoundException, ConnectorException, ValidationException {
        return clientOperationService.validateRevokeCertificateAttributes(raProfileName, attributes);
    }

	@Override
    public void revokeCertificate(
            @PathVariable String raProfileName,
            @PathVariable String certificateId,
            @RequestBody ClientCertificateRevocationDto request) throws NotFoundException, ConnectorException {
        clientOperationService.revokeCertificate(raProfileName, certificateId, request);
    }
}