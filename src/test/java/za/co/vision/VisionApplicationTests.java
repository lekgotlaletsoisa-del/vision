package za.co.vision;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "vision.results.directory=${java.io.tmpdir}/vision-tests-${random.uuid}")
class VisionApplicationTests {

	@Test
	void contextLoads() {
	}

}
