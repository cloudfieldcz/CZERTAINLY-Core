package com.czertainly.core.service.v2.impl;

import com.czertainly.api.core.modal.ObjectType;
import com.czertainly.api.core.modal.OperationType;
import com.czertainly.api.core.modal.UuidDto;
import com.czertainly.api.core.v2.model.ClientCertificateDataResponseDto;
import com.czertainly.api.core.v2.model.ClientCertificateRenewRequestDto;
import com.czertainly.api.core.v2.model.ClientCertificateRevocationDto;
import com.czertainly.api.core.v2.model.ClientCertificateSignRequestDto;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.AttributeDefinition;
import com.czertainly.api.model.discovery.CertificateStatus;
import com.czertainly.api.v2.CertificateApiClient;
import com.czertainly.api.v2.model.ca.CertRevocationDto;
import com.czertainly.api.v2.model.ca.CertificateDataResponseDto;
import com.czertainly.api.v2.model.ca.CertificateRenewRequestDto;
import com.czertainly.api.v2.model.ca.CertificateSignRequestDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.Connector2FunctionGroup;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.service.CertValidationService;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.v2.ClientOperationService;
import com.czertainly.core.util.ValidatorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.security.cert.CertificateException;
import java.util.List;

@Service("clientOperationServiceImplV2")
@Transactional
@Secured({"ROLE_CLIENT"})
public class ClientOperationServiceImpl implements ClientOperationService {
    private static final Logger logger = LoggerFactory.getLogger(ClientOperationServiceImpl.class);

    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private CertificateApiClient certificateApiClient;
    @Autowired
    private CertificateService certificateService;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertValidationService certValidationService;
    @Autowired
    private ConnectorRepository connectorRepository;


    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.ATTRIBUTES, operation = OperationType.REQUEST)
    public List<AttributeDefinition> listIssueCertificateAttributes(String raProfileName) throws NotFoundException, ConnectorException {
        ValidatorUtil.validateAuthToRaProfile(raProfileName);

        RaProfile raProfile = raProfileRepository.findByNameAndEnabledIsTrue(raProfileName)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileName));
        validateLegacyConnector(raProfile.getCaInstanceReference().getConnector());

        return certificateApiClient.listIssueCertificateAttributes(
                raProfile.getCaInstanceReference().getConnector().mapToDto(),
                raProfile.getCaInstanceReference().getCaInstanceId());
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.ATTRIBUTES, operation = OperationType.VALIDATE)
    public boolean validateIssueCertificateAttributes(String raProfileName, List<AttributeDefinition> attributes) throws NotFoundException, ConnectorException, ValidationException {
        ValidatorUtil.validateAuthToRaProfile(raProfileName);

        RaProfile raProfile = raProfileRepository.findByNameAndEnabledIsTrue(raProfileName)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileName));
        validateLegacyConnector(raProfile.getCaInstanceReference().getConnector());

        return certificateApiClient.validateIssueCertificateAttributes(
                raProfile.getCaInstanceReference().getConnector().mapToDto(),
                raProfile.getCaInstanceReference().getCaInstanceId(),
                attributes);
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.END_ENTITY_CERTIFICATE, operation = OperationType.ISSUE)
    public ClientCertificateDataResponseDto issueCertificate(String raProfileName, ClientCertificateSignRequestDto request) throws NotFoundException, ConnectorException, AlreadyExistException, CertificateException {
        ValidatorUtil.validateAuthToRaProfile(raProfileName);

        RaProfile raProfile = raProfileRepository.findByNameAndEnabledIsTrue(raProfileName)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileName));
        validateLegacyConnector(raProfile.getCaInstanceReference().getConnector());

        CertificateSignRequestDto caRequest = new CertificateSignRequestDto();
        caRequest.setPkcs10(request.getPkcs10());
        caRequest.setAttributes(request.getAttributes());
        caRequest.setRaProfile(raProfile.mapToDto());

        CertificateDataResponseDto caResponse = certificateApiClient.issueCertificate(
                raProfile.getCaInstanceReference().getConnector().mapToDto(),
                raProfile.getCaInstanceReference().getCaInstanceId(),
                caRequest);

        Certificate certificate = certificateService.checkCreateCertificate(caResponse.getCertificateData());

        logger.info("Certificate Created. Adding the certificate to Inventory");
        UuidDto dto = new UuidDto();
        dto.setUuid(raProfile.getUuid());
        logger.debug("Id of the certificate is {}", certificate.getId());
        logger.debug("Id of the RA Profile is {}", raProfile.getId());
        certificateService.updateRaProfile(certificate.getUuid(), dto);
        certificateService.updateIssuer();
        try {
            certValidationService.validate(certificate);
        } catch (Exception e){
            logger.warn("Unable to validate the uploaded certificate, {}", e.getMessage());
        }

        ClientCertificateDataResponseDto response = new ClientCertificateDataResponseDto();
        response.setCertificateData(caResponse.getCertificateData());
        response.setUuid(certificate.getUuid());
        return response;
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.END_ENTITY_CERTIFICATE, operation = OperationType.RENEW)
    public ClientCertificateDataResponseDto renewCertificate(String raProfileName, String certificateId, ClientCertificateRenewRequestDto request) throws NotFoundException, ConnectorException, AlreadyExistException, CertificateException {
        ValidatorUtil.validateAuthToRaProfile(raProfileName);

        RaProfile raProfile = raProfileRepository.findByNameAndEnabledIsTrue(raProfileName)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileName));
        validateLegacyConnector(raProfile.getCaInstanceReference().getConnector());

        CertificateRenewRequestDto caRequest = new CertificateRenewRequestDto();
        caRequest.setPkcs10(request.getPkcs10());
        caRequest.setRaProfile(raProfile.mapToDto());

        CertificateDataResponseDto caResponse = certificateApiClient.renewCertificate(
                raProfile.getCaInstanceReference().getConnector().mapToDto(),
                raProfile.getCaInstanceReference().getCaInstanceId(),
                certificateId,
                caRequest);

        Certificate certificate = certificateService.checkCreateCertificate(caResponse.getCertificateData());
        logger.info("Certificate Renewed. Adding the certificate to Inventory");
        UuidDto dto = new UuidDto();
        dto.setUuid(raProfile.getUuid());
        logger.debug("Id of the certificate is {}", certificate.getId());
        logger.debug("Id of the RA Profile is {}", raProfile.getId());
        certificateService.updateRaProfile(certificate.getUuid(), dto);
        certificateService.updateIssuer();
        try {
            certValidationService.validate(certificate);
        } catch (Exception e){
            logger.warn("Unable to validate the uploaded certificate, {}", e.getMessage());
        }

        ClientCertificateDataResponseDto response = new ClientCertificateDataResponseDto();
        response.setCertificateData(caResponse.getCertificateData());
        response.setUuid(certificate.getUuid());
        return response;
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.ATTRIBUTES, operation = OperationType.REQUEST)
    public List<AttributeDefinition> listRevokeCertificateAttributes(String raProfileName) throws NotFoundException, ConnectorException {
        ValidatorUtil.validateAuthToRaProfile(raProfileName);
        RaProfile raProfile = raProfileRepository.findByNameAndEnabledIsTrue(raProfileName)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileName));

        return certificateApiClient.listRevokeCertificateAttributes(
                raProfile.getCaInstanceReference().getConnector().mapToDto(),
                raProfile.getCaInstanceReference().getCaInstanceId());
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.ATTRIBUTES, operation = OperationType.VALIDATE)
    public boolean validateRevokeCertificateAttributes(String raProfileName, List<AttributeDefinition> attributes) throws NotFoundException, ConnectorException, ValidationException {
        ValidatorUtil.validateAuthToRaProfile(raProfileName);
        RaProfile raProfile = raProfileRepository.findByNameAndEnabledIsTrue(raProfileName)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileName));

        return certificateApiClient.validateRevokeCertificateAttributes(
                raProfile.getCaInstanceReference().getConnector().mapToDto(),
                raProfile.getCaInstanceReference().getCaInstanceId(),
                attributes);
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.END_ENTITY_CERTIFICATE, operation = OperationType.REVOKE)
    public void revokeCertificate(String raProfileName, String certificateId, ClientCertificateRevocationDto request) throws NotFoundException, ConnectorException {
        ValidatorUtil.validateAuthToRaProfile(raProfileName);

        RaProfile raProfile = raProfileRepository.findByNameAndEnabledIsTrue(raProfileName)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileName));
        validateLegacyConnector(raProfile.getCaInstanceReference().getConnector());

        logger.debug("Ra Profile {} set for revoking the certificate", raProfile.getName());

        CertRevocationDto caRequest = new CertRevocationDto();
        caRequest.setReason(request.getReason());
        caRequest.setAttributes(request.getAttributes());
        caRequest.setRaProfile(raProfile.mapToDto());

        certificateApiClient.revokeCertificate(
                raProfile.getCaInstanceReference().getConnector().mapToDto(),
                raProfile.getCaInstanceReference().getCaInstanceId(),
                certificateId,
                caRequest);
        try {
            Certificate certificate = certificateService.getCertificateEntityBySerial(certificateId);
            certificate.setStatus(CertificateStatus.REVOKED);
            certificateRepository.save(certificate);
        }catch(Exception e) {
            logger.warn(e.getMessage());
        }
    }

    private void validateLegacyConnector(Connector connector) throws NotFoundException{
        for(Connector2FunctionGroup fg: connector.getFunctionGroups()){
            if(!connectorRepository.findConnectedByFunctionGroupAndKind(fg.getFunctionGroup(), "LegacyEjbca").isEmpty()){
                throw new NotFoundException("Legacy Authority. V2 Implementation not found on the connector");
            }
        }
    }
}
