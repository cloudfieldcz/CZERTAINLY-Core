package com.czertainly.core.api.web;

import java.net.URI;
import java.util.List;

import com.czertainly.api.exception.ConnectorException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.czertainly.core.service.RaProfileService;
import com.czertainly.api.core.interfaces.web.RAProfileManagementController;
import com.czertainly.api.core.modal.ClientDto;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.raprofile.AddRaProfileRequestDto;
import com.czertainly.api.model.raprofile.EditRaProfileRequestDto;
import com.czertainly.api.model.raprofile.RaProfileDto;

@RestController
public class RAProfileManagementControllerImpl implements RAProfileManagementController {

    @Autowired
    private RaProfileService raProfileService;

    @Override
    public List<RaProfileDto> listRaProfiles() {
        return raProfileService.listRaProfiles();
    }

    @Override
    public List<RaProfileDto> listRaProfiles(@RequestParam Boolean isEnabled) {
        return raProfileService.listRaProfiles(isEnabled);
    }

    @Override
    public ResponseEntity<?> addRaProfile(@RequestBody AddRaProfileRequestDto request)
            throws AlreadyExistException, ValidationException, NotFoundException, ConnectorException {
        RaProfileDto raProfile = raProfileService.addRaProfile(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{raProfileUuid}")
                .buildAndExpand(raProfile.getUuid()).toUri();
        return ResponseEntity.created(location).build();
    }

    @Override
    public RaProfileDto getRaProfile(@PathVariable String uuid) throws NotFoundException {
        return raProfileService.getRaProfile(uuid);
    }

    @Override
    public RaProfileDto editRaProfile(@PathVariable String uuid, @RequestBody EditRaProfileRequestDto request)
            throws NotFoundException, ConnectorException {
        return raProfileService.editRaProfile(uuid, request);
    }

    @Override
    public void removeRaProfile(@PathVariable String uuid) throws NotFoundException {
        raProfileService.removeRaProfile(uuid);
    }

    @Override
    public void disableRaProfile(@PathVariable String uuid) throws NotFoundException {
        raProfileService.disableRaProfile(uuid);
    }

    @Override
    public void enableRaProfile(@PathVariable String uuid) throws NotFoundException {
        raProfileService.enableRaProfile(uuid);
    }

    @Override
    public List<ClientDto> listClients(@PathVariable String uuid) throws NotFoundException {
        return raProfileService.listClients(uuid);
    }

    @Override
    public void bulkRemoveRaProfile(List<String> uuids) throws NotFoundException, ValidationException {
        raProfileService.bulkRemoveRaProfile(uuids);
    }

    @Override
    public void bulkDisableRaProfile(List<String> uuids) throws NotFoundException {
        raProfileService.bulkDisableRaProfile(uuids);
    }

    @Override
    public void bulkEnableRaProfile(List<String> uuids) throws NotFoundException {
        raProfileService.bulkEnableRaProfile(uuids);
    }
}
