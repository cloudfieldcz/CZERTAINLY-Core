package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.AttributeCallback;
import com.czertainly.api.model.NameAndUuidDto;
import com.czertainly.core.dao.entity.Credential;
import com.czertainly.core.dao.repository.CredentialRepository;
import com.czertainly.core.service.impl.CoreCallbackServiceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@SpringBootTest
@Transactional
@Rollback
@WithMockUser(roles="SUPERADMINISTRATOR")
public class CoreCallbackServiceTest {

    @Autowired
    private CoreCallbackService coreCallbackService;

    @Autowired
    private CredentialRepository credentialRepository;

    @Test
    public void testCoreGetCredentials() throws NotFoundException {
        Credential credential = new Credential();
        credential.setType("certificate");
        credential.setEnabled(true);
        credential = credentialRepository.save(credential);

        AttributeCallback callback = new AttributeCallback();
        callback.setPathVariables(Map.ofEntries(Map.entry(CoreCallbackServiceImpl.CREDENTIAL_KIND_PATH_VARIABLE, credential.getType())));

        List<NameAndUuidDto> credentials = coreCallbackService.coreGetCredentials(callback);
        Assertions.assertNotNull(credentials);
        Assertions.assertFalse(credentials.isEmpty());
        Assertions.assertEquals(credential.getUuid(), credentials.get(0).getUuid());
    }

    @Test
    public void testCoreGetCredentials_validationFail() {
        Assertions.assertThrows(ValidationException.class, () -> coreCallbackService.coreGetCredentials(new AttributeCallback()));
    }

    @Test
    public void testCoreGetCredentials_notFound() {
        AttributeCallback callback = new AttributeCallback();
        callback.setPathVariables(Map.ofEntries(Map.entry(CoreCallbackServiceImpl.CREDENTIAL_KIND_PATH_VARIABLE, "")));

        Assertions.assertThrows(NotFoundException.class, () -> coreCallbackService.coreGetCredentials(callback));
    }
}
