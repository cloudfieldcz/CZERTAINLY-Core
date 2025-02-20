package com.czertainly.core.service.impl;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import com.czertainly.core.aop.AuditLogged;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Service;

import com.czertainly.core.dao.entity.Admin;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.repository.AdminRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.service.AdminService;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.api.core.modal.AddAdminRequestDto;
import com.czertainly.api.core.modal.AdminDto;
import com.czertainly.api.core.modal.AdminRole;
import com.czertainly.api.core.modal.EditAdminRequestDto;
import com.czertainly.api.core.modal.ObjectType;
import com.czertainly.api.core.modal.OperationType;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;

@Service
@Transactional
@Secured({"ROLE_SUPERADMINISTRATOR"})
public class AdminServiceImpl implements AdminService {

    private static final Logger logger = LoggerFactory.getLogger(AdminServiceImpl.class);

    @Autowired
    private AdminRepository adminRepository;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateService certificateService;

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ADMINISTRATOR, operation = OperationType.REQUEST)
    public List<AdminDto> listAdmins() {
        List<Admin> admins = adminRepository.findAll();

        return admins.stream().map(Admin::mapToDto).collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ADMINISTRATOR, operation = OperationType.CREATE)
    public AdminDto addAdmin(AddAdminRequestDto request)
            throws CertificateException, AlreadyExistException, ValidationException, NotFoundException {

        if (StringUtils.isBlank(request.getUsername())) {
            throw new ValidationException("username must not be empty");
        }
        if (StringUtils.isAnyBlank(request.getName(), request.getSurname())) {
            throw new ValidationException("name and surname must not be empty");
        }
        if (StringUtils.isBlank(request.getEmail())) {
            throw new ValidationException("email must not be empty");
        }

        if (request.getRole() == null) {
            request.setRole(AdminRole.ADMINISTRATOR);
        }

        if (adminRepository.existsByUsername(request.getUsername())) {
            throw new AlreadyExistException(Admin.class, request.getUsername());
        }
        
        String serialNumber;
        
        if (!StringUtils.isAnyBlank(request.getAdminCertificate())) { 
        	X509Certificate certificate = CertificateUtil.getX509Certificate(request.getAdminCertificate());
        	serialNumber = CertificateUtil.getSerialNumberFromX509Certificate(certificate);
        } else {
        	Certificate certificate = certificateService.getCertificateEntity(request.getCertificateUuid());
        	serialNumber = certificate.getSerialNumber();
        }

        if (adminRepository.findBySerialNumber(serialNumber).isPresent()) {
            throw new AlreadyExistException(Admin.class, serialNumber);
        }

        Admin admin = createAdmin(request);

        adminRepository.save(admin);
        logger.info("Admin {} registered successfully.", admin.getCertificate().getSubjectDn());

        return admin.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ADMINISTRATOR, operation = OperationType.REQUEST)
    public AdminDto getAdminBySerialNumber(String serialNumber) throws NotFoundException {
        Admin admin = adminRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new NotFoundException(Admin.class, serialNumber));
        return admin.mapToDto();
    }

    @Override
    public AdminDto getAdminByUuid(String uuid) throws NotFoundException {
        Admin admin = adminRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Admin.class, uuid));
        return admin.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ADMINISTRATOR, operation = OperationType.REQUEST)
    public AdminDto getAdminByUsername(String username) throws NotFoundException {
        Admin admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException(Admin.class, username));

        return admin.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ADMINISTRATOR, operation = OperationType.CHANGE)
    public AdminDto editAdmin(String uuid, EditAdminRequestDto request) throws CertificateException, NotFoundException, AlreadyExistException {
        if (StringUtils.isAnyBlank(request.getName(), request.getSurname())) {
            throw new ValidationException("name and surname must not be empty");
        }
        if (StringUtils.isBlank(request.getEmail())) {
            throw new ValidationException("email must not be empty");
        }

        Admin admin = adminRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Admin.class, uuid));

        if (request.getAdminCertificate() != null) {
            X509Certificate certificate = CertificateUtil.getX509Certificate(request.getAdminCertificate());
            String serialNumber = CertificateUtil.getSerialNumberFromX509Certificate(certificate);
            // Updating to another certificate?
            if (!admin.getSerialNumber().equals(serialNumber) &&
                    adminRepository.findBySerialNumber(serialNumber).isPresent()) {
                throw new AlreadyExistException(Admin.class, serialNumber);
            }
        }

        updateAdmin(admin, request);

        adminRepository.save(admin);
        logger.info("Admin {} updated successfully.", uuid);

        return admin.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ADMINISTRATOR, operation = OperationType.DELETE)
    public void removeAdmin(String uuid) throws NotFoundException {
        Admin admin = adminRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Admin.class, uuid));
        adminRepository.delete(admin);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ADMINISTRATOR, operation = OperationType.ENABLE)
    public void enableAdmin(String uuid) throws NotFoundException, CertificateException {
        Admin admin = adminRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Admin.class, uuid));

        admin.setEnabled(true);
        adminRepository.save(admin);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ADMINISTRATOR, operation = OperationType.DISABLE)
    public void disableAdmin(String uuid) throws NotFoundException {
        Admin admin = adminRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Admin.class, uuid));

        admin.setEnabled(false);
        adminRepository.save(admin);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ADMINISTRATOR, operation = OperationType.DELETE)
    public void bulkRemoveAdmin(List<String> adminUuids) {
        for(String uuid: adminUuids){
            try{
                Admin admin = adminRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(Admin.class, uuid));
                adminRepository.delete(admin);
            }
            catch (NotFoundException e){
                logger.warn("Unable to delete the admin with id {}", uuid);
            }
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ADMINISTRATOR, operation = OperationType.DISABLE)
    public void bulkDisableAdmin(List<String> adminUuids) {
        for(String uuid: adminUuids){
            try{
                Admin admin = adminRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(Admin.class, uuid));

                admin.setEnabled(false);
                adminRepository.save(admin);
            }catch(NotFoundException e){
                logger.warn("Unable to disable admin with id {}", uuid);
            }
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ADMINISTRATOR, operation = OperationType.ENABLE)
    public void bulkEnableAdmin(List<String> adminUuids) {
        for(String uuid: adminUuids){
            try{
                Admin admin = adminRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(Admin.class, uuid));

                admin.setEnabled(true);
                adminRepository.save(admin);
            }catch(NotFoundException e){
                logger.warn("Unable to enable admin with id {}", uuid);
            }
        }
    }

    private Admin createAdmin(AddAdminRequestDto requestDTO) throws CertificateException, AlreadyExistException, NotFoundException {
        Admin model = new Admin();
        
        Certificate certificate;
        if (StringUtils.isNotBlank(requestDTO.getCertificateUuid())) {
        	certificate = certificateService.getCertificateEntity(requestDTO.getCertificateUuid());
        	model.setCertificate(certificate);
        } else {
        	X509Certificate x509Cert = CertificateUtil.parseCertificate(requestDTO.getAdminCertificate());
        	if (certificateRepository.findBySerialNumberIgnoreCase(x509Cert.getSerialNumber().toString(16)).isPresent()) {
                throw new AlreadyExistException(Certificate.class, x509Cert.getSerialNumber().toString(16));
            }
        	certificate = certificateService.createCertificateEntity(x509Cert);
        	certificateRepository.save(certificate);
        	model.setCertificate(certificate);
        }
        model.setUsername(requestDTO.getUsername());
        model.setName(requestDTO.getName());
        model.setDescription(requestDTO.getDescription());
        model.setEnabled(requestDTO.getEnabled() != null && requestDTO.getEnabled());
        model.setRole(requestDTO.getRole());
        model.setEmail(requestDTO.getEmail());
        model.setSurname(requestDTO.getSurname());
        model.setSerialNumber(certificate.getSerialNumber());
        return model;
    }

    private Admin updateAdmin(Admin admin, EditAdminRequestDto dto) throws CertificateException, NotFoundException, AlreadyExistException {
    	
    	Certificate certificate;
        if((dto.getAdminCertificate() != null && !dto.getAdminCertificate().isEmpty()) || (dto.getCertificateUuid() != null && !dto.getCertificateUuid().isEmpty())) {
            if (!dto.getCertificateUuid().isEmpty()) {
                certificate = certificateService.getCertificateEntity(dto.getCertificateUuid());
                admin.setCertificate(certificate);

            } else {
                X509Certificate x509Cert = CertificateUtil.parseCertificate(dto.getAdminCertificate());
                if (certificateRepository.findBySerialNumberIgnoreCase(x509Cert.getSerialNumber().toString(16)).isPresent()) {
                    throw new AlreadyExistException(Certificate.class, x509Cert.getSerialNumber().toString(16));
                }

                certificate = certificateService.createCertificateEntity(x509Cert);
                certificateRepository.save(certificate);
                admin.setCertificate(certificate);
            }
            admin.setSerialNumber(certificate.getSerialNumber());
        }
        
        admin.setName(dto.getName());
        admin.setDescription(dto.getDescription());
        if (dto.getRole() != null) {
            admin.setRole(dto.getRole());
        }
        admin.setEmail(dto.getEmail());
        admin.setSurname(dto.getSurname());
        
        return admin;
    }
}
