package za.co.vision;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for the Vision Lab web application. */
@SpringBootApplication
public class VisionApplication {

	/** Creates the application entry point. */
	public VisionApplication() {
	}

	/**
	 * Starts Spring MVC and eagerly initializes the OpenCV runtime.
	 * @param args Spring Boot command-line configuration overrides
	 */
	public static void main(String[] args) {
		SpringApplication.run(VisionApplication.class, args);
	}

}
