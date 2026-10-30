package za.co.vision.config;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_java;
import org.opencv.core.Core;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Eagerly extracts/loads bundled Linux natives; a native failure aborts startup. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VisionProperties.class)
public class OpenCvRuntime {
    /** Loads the official OpenCV Java JNI binding through JavaCPP's native cache. */
    public OpenCvRuntime() {
        Loader.load(opencv_java.class);
    }

    /**
     * Reports the loaded native version.
     * @return loaded native OpenCV version for the readiness endpoint
     */
    public String version() {
        return Core.getVersionString();
    }
}
