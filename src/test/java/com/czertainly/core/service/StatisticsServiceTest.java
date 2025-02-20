package com.czertainly.core.service;

import com.czertainly.api.model.discovery.StatisticsDto;
import com.czertainly.core.dao.entity.CertificateGroup;
import com.czertainly.core.dao.repository.CertificateGroupRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@Rollback
@WithMockUser(roles="SUPERADMINISTRATOR")
public class StatisticsServiceTest {

    @Autowired
    private StatisticsService statisticsService;

    @Autowired
    private CertificateGroupRepository certificateGroupRepository;

    @Test
    public void testGetStatistics() {
        StatisticsDto result = statisticsService.getStatistics();
        Assertions.assertNotNull(result);
        Assertions.assertEquals(0l, result.getTotalCertificates());
        Assertions.assertEquals(0l, result.getTotalGroups());
        Assertions.assertEquals(0l, result.getTotalEntities());
    }

    @Test
    public void testGetStatistics_oneGroup() {
        certificateGroupRepository.save(new CertificateGroup());

        StatisticsDto result = statisticsService.getStatistics();
        Assertions.assertNotNull(result);
        Assertions.assertEquals(0l, result.getTotalCertificates());
        Assertions.assertEquals(1l, result.getTotalGroups());
        Assertions.assertEquals(0l, result.getTotalEntities());
    }
}
