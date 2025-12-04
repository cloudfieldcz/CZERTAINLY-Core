package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.notification.NotificationDto;
import com.czertainly.api.model.client.notification.NotificationRequestDto;
import com.czertainly.api.model.client.notification.NotificationResponseDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.discovery.DiscoveryStatus;
import com.czertainly.core.dao.entity.DiscoveryHistory;
import com.czertainly.core.dao.entity.FunctionGroup;
import com.czertainly.core.dao.repository.DiscoveryRepository;
import com.czertainly.core.dao.repository.FunctionGroupRepository;
import com.czertainly.core.security.authn.CzertainlyUserDetails;
import com.czertainly.core.util.BaseMessagingIntTest;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.UncategorizedJmsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

@ActiveProfiles({"messaging-int-test"})
class DiscoveryServiceIntTest extends BaseMessagingIntTest {

    private static final String DISCOVERY_NAME = "testDiscovery1";

    @Autowired
    private DiscoveryService discoveryService;
    @Autowired
    private DiscoveryRepository discoveryRepository;
    @Autowired
    private FunctionGroupRepository functionGroupRepository;
    @Autowired
    private NotificationService notificationService;

    private DiscoveryHistory discovery;

    @BeforeEach
    void setUp() {
        // Mock authentication for all threads
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);

        Authentication authentication = getAuthentication();
        CzertainlyUserDetails principal = (CzertainlyUserDetails) authentication.getPrincipal();
        CzertainlyUserDetails userDetails = Mockito.mock(CzertainlyUserDetails.class);
        Mockito.when(userDetails.getUserUuid()).thenReturn(principal.getUserUuid());
        Mockito.when(userDetails.getUsername()).thenReturn(principal.getUsername());
        Mockito.when(userDetails.getRawData()).thenReturn(principal.getRawData());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        FunctionGroup functionGroup = new FunctionGroup();
        functionGroup.setCode(FunctionGroupCode.DISCOVERY_PROVIDER);
        functionGroup.setName(FunctionGroupCode.DISCOVERY_PROVIDER.getCode());
        functionGroupRepository.save(functionGroup);

        discovery = new DiscoveryHistory();
        discovery.setName(DISCOVERY_NAME);
        discovery.setStatus(DiscoveryStatus.IN_PROGRESS);
        discovery.setConnectorStatus(DiscoveryStatus.IN_PROGRESS);
        discovery = discoveryRepository.save(discovery);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        // back to an original strategy
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_THREADLOCAL);
    }

    @Test
    @Disabled("Runs on localhost, but fails on github")
    void testBulkRemove() throws NotFoundException {
        try {
            discoveryService.bulkRemoveDiscovery(List.of(discovery.getSecuredUuid()));

            NotificationRequestDto requestDto = new NotificationRequestDto();
            requestDto.setUnread(true);

            // wait until a message is processed (and a notification is created)
            int maxRetries = 60;
            for (int i = 0; i < maxRetries; i++) {
                NotificationResponseDto notificationResponseDto = notificationService.listNotifications(requestDto);
                if (!notificationResponseDto.getItems().isEmpty()) {
                    break;
                }
                Thread.sleep(500);
            }

            // final check
            NotificationResponseDto notificationResponseDto = notificationService.listNotifications(requestDto);

            NotificationDto notificationDto = notificationResponseDto.getItems().getFirst();
            Assertions.assertEquals("Discovery histories have been deleted.", notificationDto.getMessage());
            Assertions.assertEquals(Resource.DISCOVERY, notificationDto.getTargetObjectType());
        } catch (UncategorizedJmsException e) {
            Assertions.fail("UncategorizedJmsException thrown: " + e.getMessage());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
