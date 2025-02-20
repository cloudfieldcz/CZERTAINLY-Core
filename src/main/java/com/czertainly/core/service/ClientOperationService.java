package com.czertainly.core.service;

import com.czertainly.api.core.modal.*;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;

import java.security.cert.CertificateException;
import java.util.List;

public interface ClientOperationService {

    ClientCertificateSignResponseDto issueCertificate(String raProfileName, ClientCertificateSignRequestDto request) throws NotFoundException, AlreadyExistException, CertificateException, ConnectorException;

    void revokeCertificate(String raProfileName, ClientCertificateRevocationDto request) throws NotFoundException, ConnectorException;

    List<ClientEndEntityDto> listEntities(String raProfileName) throws NotFoundException, ConnectorException;

    void addEndEntity(String raProfileName, ClientAddEndEntityRequestDto request) throws NotFoundException, AlreadyExistException, ConnectorException;

    ClientEndEntityDto getEndEntity(String raProfileName, String username) throws NotFoundException, ConnectorException;

    void editEndEntity(String raProfileName, String username, ClientEditEndEntityRequestDto request) throws NotFoundException, ConnectorException;

    void revokeAndDeleteEndEntity(String raProfileName, String username) throws NotFoundException, ConnectorException;

    void resetPassword(String raProfileName, String username) throws NotFoundException, ConnectorException;
}
