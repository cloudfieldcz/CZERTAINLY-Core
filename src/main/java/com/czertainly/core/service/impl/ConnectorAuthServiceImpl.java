package com.czertainly.core.service.impl;

import com.czertainly.core.service.ConnectorAuthService;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.AttributeDefinition;
import com.czertainly.api.model.BaseAttributeDefinitionTypes;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import javax.transaction.Transactional;

import static com.czertainly.api.BaseApiClient.*;

@Service
@Transactional
public class ConnectorAuthServiceImpl implements ConnectorAuthService {
    private static final Logger logger = LoggerFactory.getLogger(ConnectorAuthServiceImpl.class);

    private static final ArrayList<String> SUPPORTED_KEY_STORE_TYPES = new ArrayList<>();

    static {
        SUPPORTED_KEY_STORE_TYPES.add("PKCS12");
        SUPPORTED_KEY_STORE_TYPES.add("JKS");
    }

    @Override
    public List<String> getAuthenticationTypes() {
        return Arrays.asList("NONE","Basic","Certificate","ApiKey","JWT");
    }

    @Override
    public List<AttributeDefinition> getBasicAuthAttributes() {
        List<AttributeDefinition> attrs = new ArrayList<>();

        AttributeDefinition username = new AttributeDefinition();
        username.setId("fe2d6d35-fb3d-4ea0-9f0b-7e39be93beeb");
        username.setName(ATTRIBUTE_USERNAME);
        username.setType(BaseAttributeDefinitionTypes.STRING);
        username.setRequired(true);
        username.setReadOnly(false);
        username.setVisible(true);
        attrs.add(username);

        AttributeDefinition password = new AttributeDefinition();
        password.setId("04506d45-c865-4ddc-b6fc-117ee5d5c8e7");
        password.setName(ATTRIBUTE_PASSWORD);
        password.setType(BaseAttributeDefinitionTypes.SECRET);
        password.setRequired(true);
        password.setReadOnly(false);
        password.setVisible(true);
        attrs.add(password);

        return attrs;
    }

    @Override
    public Boolean validateBasicAuthAttributes(List<AttributeDefinition> attributes) {
        AttributeDefinitionUtils.validateAttributes(getBasicAuthAttributes(), attributes);
        return true;
    }

    @Override
    public List<AttributeDefinition> getCertificateAttributes() {
        List<AttributeDefinition> attrs = new ArrayList<>();

        AttributeDefinition keyStoreType = new AttributeDefinition();
        keyStoreType.setId("e334e055-900e-43f1-aedc-54e837028de0");
        keyStoreType.setName(ATTRIBUTE_KEYSTORE_TYPE);
        keyStoreType.setType(BaseAttributeDefinitionTypes.LIST);
        keyStoreType.setRequired(true);
        keyStoreType.setReadOnly(false);
        keyStoreType.setVisible(true);
        keyStoreType.setValue(SUPPORTED_KEY_STORE_TYPES);
        attrs.add(keyStoreType);

        AttributeDefinition keyStore = new AttributeDefinition();
        keyStore.setId("6df7ace9-c501-4d58-953c-f8d53d4fb378");
        keyStore.setName(ATTRIBUTE_KEYSTORE);
        keyStore.setType(BaseAttributeDefinitionTypes.FILE);
        keyStore.setRequired(true);
        keyStore.setReadOnly(false);
        keyStore.setVisible(true);
        attrs.add(keyStore);

        AttributeDefinition keyStorePassword = new AttributeDefinition();
        keyStorePassword.setId("d975fe42-9d09-4740-a362-fc26f98e55ea");
        keyStorePassword.setName(ATTRIBUTE_KEYSTORE_PASSWORD);
        keyStorePassword.setType(BaseAttributeDefinitionTypes.SECRET);
        keyStorePassword.setRequired(true);
        keyStorePassword.setReadOnly(false);
        keyStorePassword.setVisible(true);
        attrs.add(keyStorePassword);

        AttributeDefinition trustStoreType = new AttributeDefinition();
        trustStoreType.setId("c4454807-805a-44e2-81d1-94b56e993786");
        trustStoreType.setName(ATTRIBUTE_TRUSTSTORE_TYPE);
        trustStoreType.setType(BaseAttributeDefinitionTypes.LIST);
        trustStoreType.setRequired(false);
        trustStoreType.setReadOnly(false);
        trustStoreType.setVisible(true);
        trustStoreType.setValue(SUPPORTED_KEY_STORE_TYPES);
        attrs.add(trustStoreType);

        AttributeDefinition trustStore = new AttributeDefinition();
        trustStore.setId("6a245220-eaf4-44cb-9079-2228ad9264f5");
        trustStore.setName(ATTRIBUTE_TRUSTSTORE);
        trustStore.setType(BaseAttributeDefinitionTypes.FILE);
        trustStore.setRequired(false);
        trustStore.setReadOnly(false);
        trustStore.setVisible(true);
        attrs.add(trustStore);

        AttributeDefinition trustStorePassword = new AttributeDefinition();
        trustStorePassword.setId("85a874da-1413-4770-9830-4188a37c95ee");
        trustStorePassword.setName(ATTRIBUTE_TRUSTSTORE_PASSWORD);
        trustStorePassword.setType(BaseAttributeDefinitionTypes.SECRET);
        trustStorePassword.setRequired(false);
        trustStorePassword.setReadOnly(false);
        trustStorePassword.setVisible(true);
        attrs.add(trustStorePassword);

        return attrs;
    }

    @Override
    public Boolean validateCertificateAttributes(List<AttributeDefinition> attributes) {
        AttributeDefinitionUtils.validateAttributes(getCertificateAttributes(), attributes);

        try {
            String keyStoreBase64 = AttributeDefinitionUtils.getAttributeValue(ATTRIBUTE_KEYSTORE, attributes);
            byte[] keyStoreBytes = Base64.getDecoder().decode(keyStoreBase64);

            String keyStoreType = AttributeDefinitionUtils.getAttributeValue(ATTRIBUTE_KEYSTORE_TYPE, attributes);
            String keyStorePassword = AttributeDefinitionUtils.getAttributeValue(ATTRIBUTE_KEYSTORE_PASSWORD, attributes);

            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(new ByteArrayInputStream(keyStoreBytes), keyStorePassword.toCharArray());
            logger.info("Key store attribute successfully validated. Given key store contains: {}", keyStore.aliases());

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new ValidationException(ValidationError.create(e.getMessage()));
        }

        try {
            String trustStoreBase64 = AttributeDefinitionUtils.getAttributeValue(ATTRIBUTE_TRUSTSTORE, attributes);
            String trustStoreType = AttributeDefinitionUtils.getAttributeValue(ATTRIBUTE_TRUSTSTORE_TYPE, attributes);
            String trustStorePassword = AttributeDefinitionUtils.getAttributeValue(ATTRIBUTE_TRUSTSTORE_PASSWORD, attributes);

            if (!StringUtils.isAnyBlank(trustStoreBase64, trustStoreType, trustStorePassword)) {
                byte[] trustStoreBytes = Base64.getDecoder().decode(trustStoreBase64);
                KeyStore trustStore = KeyStore.getInstance(trustStoreType);
                trustStore.load(new ByteArrayInputStream(trustStoreBytes), trustStorePassword.toCharArray());
                logger.info("Trust store attribute successfully validated. Given trust store contains: {}", trustStore.aliases());
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new ValidationException(ValidationError.create(e.getMessage()));
        }

        return true;
    }

    @Override
    public List<AttributeDefinition> getApiKeyAuthAttributes() {
        List<AttributeDefinition> attrs = new ArrayList<>();

        AttributeDefinition apiKeyHeader = new AttributeDefinition();
        apiKeyHeader.setId("705ccbfb-1d81-402a-ae67-8d38f159b240");
        apiKeyHeader.setName(ATTRIBUTE_API_KEY_HEADER);
        apiKeyHeader.setType(BaseAttributeDefinitionTypes.STRING);
        apiKeyHeader.setRequired(true);
        apiKeyHeader.setReadOnly(false);
        apiKeyHeader.setVisible(true);
        apiKeyHeader.setValue("X-API-KEY");
        attrs.add(apiKeyHeader);

        AttributeDefinition apiKey = new AttributeDefinition();
        apiKey.setId("989dafd6-d18c-41f1-b68d-285c56d6331e");
        apiKey.setName(ATTRIBUTE_API_KEY);
        apiKey.setType(BaseAttributeDefinitionTypes.SECRET);
        apiKey.setRequired(true);
        apiKey.setReadOnly(false);
        apiKey.setVisible(true);
        attrs.add(apiKey);

        return attrs;
    }

    @Override
    public Boolean validateApiKeyAuthAttributes(List<AttributeDefinition> attributes) {
        AttributeDefinitionUtils.validateAttributes(getApiKeyAuthAttributes(), attributes);
        return true;
    }

    @Override
    public List<AttributeDefinition> getJWTAuthAttributes() {
        throw new ValidationException(ValidationError.create("Auth type JWT not implemented yet"));
    }

    @Override
    public Boolean validateJWTAuthAttributes(List<AttributeDefinition> attributes) {
        throw new ValidationException(ValidationError.create("Auth type JWT not implemented yet"));
    }
}
