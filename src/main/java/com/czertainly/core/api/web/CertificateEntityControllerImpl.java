package com.czertainly.core.api.web;

import java.net.URI;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.czertainly.core.service.CertificateEntityService;
import com.czertainly.api.core.interfaces.web.CertificateEntityController;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.discovery.CertificateEntityDto;

@RestController
public class CertificateEntityControllerImpl implements CertificateEntityController{

    @Autowired
    private CertificateEntityService certificateEntityService;

    @Override
    public List<CertificateEntityDto> listCertificateEntitys() {
        return certificateEntityService.listCertificateEntity();
    }

    @Override
    public CertificateEntityDto getCertificateEntity(@PathVariable String uuid) throws NotFoundException {
        return certificateEntityService.getCertificateEntity(uuid);
    }

    @Override
    public ResponseEntity<?> createCertificateEntity(@RequestBody CertificateEntityDto request) throws AlreadyExistException, NotFoundException {
        CertificateEntityDto entityDto = certificateEntityService.createCertificateEntity(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(entityDto.getUuid())
                .toUri();

        return ResponseEntity.created(location).build();
    }

    @Override
    public CertificateEntityDto updateCertificateEntity(@PathVariable String uuid, @RequestBody CertificateEntityDto request) throws NotFoundException {
        return certificateEntityService.updateCertificateEntity(uuid, request);
    }

    @Override
    public void removeCertificateEntity(@PathVariable String uuid) throws NotFoundException {
        certificateEntityService.removeCertificateEntity(uuid);
    }

    @Override
    public void bulkRemoveCertificateEntity(List<String> entityUuids) throws NotFoundException {
        certificateEntityService.bulkRemoveCertificateEntity(entityUuids);
    }
}
