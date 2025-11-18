package com.czertainly.core;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(NoSchedulingConfig.class)
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApplicationTests {

	@Test
	void contextLoads() {
	}
}
