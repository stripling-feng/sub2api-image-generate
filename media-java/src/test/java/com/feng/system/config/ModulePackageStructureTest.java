package com.feng.system.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModulePackageStructureTest {

    @Test
    void imageAndVideoModulesUseSystemStyleSubpackages() throws IOException {
        Path moduleRoot = moduleRoot();

        for (String moduleName : List.of("image", "video")) {
            Path rootPackage = moduleRoot.resolve(moduleName);
            List<String> rootJavaFiles;

            try (var files = Files.list(rootPackage)) {
                rootJavaFiles = files
                        .filter(path -> path.getFileName().toString().endsWith(".java"))
                        .map(path -> path.getFileName().toString())
                        .sorted()
                        .toList();
            }

            assertThat(rootJavaFiles)
                    .as("%s module should place Java classes in subpackages like system module", moduleName)
                    .isEmpty();
        }
    }

    @Test
    void videoModelAdminEndpointsLiveInSystemModule() throws IOException {
        Path moduleRoot = moduleRoot();
        String imageModelAdmin = Files.readString(moduleRoot.resolve("image/controller/ModelAdminController.java"));
        Path systemVideoModelController = moduleRoot.resolve("system/controller/VideoModelController.java");

        assertThat(imageModelAdmin)
                .as("video model admin endpoints should not live in image module")
                .doesNotContain("/api/model/videos");
        assertThat(systemVideoModelController)
                .as("video model admin endpoints should have a system module controller")
                .exists();
        assertThat(Files.readString(systemVideoModelController))
                .contains("/api/model/videos");
    }

    private static Path moduleRoot() {
        Path mavenModuleRoot = Path.of("src/main/java/com/feng/system/module");
        if (Files.exists(mavenModuleRoot)) {
            return mavenModuleRoot;
        }

        return Path.of("media-java/src/main/java/com/feng/system/module");
    }
}
