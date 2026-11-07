package com.imageaiplatform.foundation.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class RuntimeSettingsLoaderTest {
    @TempDir
    Path directory;

    private final Logger logger = (Logger) LoggerFactory.getLogger(RuntimeSettingsLoader.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void stopCapturingLogs() {
        logger.detachAppender(logs);
        logs.stop();
    }

    @Test
    void readsAllDirectPropertiesInUtf8AndIgnoresOtherFiles() throws Exception {
        write("config/runtime/a.properties", "name=한글\n");
        write("config/runtime/b.properties", "count=21\n");
        write("config/runtime/nested/ignored.properties", "name=duplicate\n");
        write("config/runtime/ignored.yaml", "name: ignored\n");

        try (var loader = resourceLoader(directory)) {
            var values = RuntimeSettingsLoader.readProperties(loader);
            assertThat(values).hasSize(2);
            assertThat(values.getProperty("name")).isEqualTo("한글");
            assertThat(values.getProperty("count")).isEqualTo("21");
        }
    }

    @Test
    void readsJarWithoutDirectoryEntries() throws Exception {
        Path jar = directory.resolve("settings.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("config/runtime/a.properties"));
            output.write("name=한글\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new JarEntry("config/runtime/b.properties"));
            output.write("count=21\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        try (var loader = resourceLoader(jar)) {
            assertThat(RuntimeSettingsLoader.readProperties(loader))
                    .containsEntry("name", "한글").containsEntry("count", "21");
        }
        assertThat(logs.list).filteredOn(event -> event.getFormattedMessage().contains("파일 로딩 완료"))
                .hasSize(2).allSatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.INFO);
                    assertThat(event.getFormattedMessage()).contains("settings.jar!/config/runtime/");
                });
    }

    @Test
    void logsLoadedFileLocationsWithoutValues() throws Exception {
        write("config/runtime/a.properties", "password=secret-value\n");
        write("config/runtime/b.properties", "name=한글\n");
        try (var loader = resourceLoader(directory)) {
            RuntimeSettingsLoader.readProperties(loader);
        }
        assertThat(logs.list).filteredOn(event -> event.getFormattedMessage().contains("파일 로딩 완료"))
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly(
                        "런타임 설정 파일 로딩 완료: " + directory.resolve("config/runtime/a.properties").toUri().toURL(),
                        "런타임 설정 파일 로딩 완료: " + directory.resolve("config/runtime/b.properties").toUri().toURL());
        assertThat(logs.list).allSatisfy(event ->
                assertThat(event.getFormattedMessage()).doesNotContain("secret-value", "한글"));
    }

    @Test
    void warnsWithCreationInstructionsWhenFolderIsMissing() throws Exception {
        assertMissingSettingsWarning();
    }

    @Test
    void warnsWithCreationInstructionsWhenFolderHasNoProperties() throws Exception {
        write("config/runtime/ignored.yaml", "name: ignored\n");
        assertMissingSettingsWarning();
    }

    private void assertMissingSettingsWarning() throws Exception {
        try (var loader = resourceLoader(directory)) {
            assertThat(RuntimeSettingsLoader.readProperties(loader)).isEmpty();
        }
        assertThat(logs.list).filteredOn(event -> event.getLevel() == Level.WARN)
                .singleElement().satisfies(event -> assertThat(event.getFormattedMessage())
                        .contains("config/runtime/", ".properties", "생성하세요"));
        assertThat(logs.list).noneSatisfy(event ->
                assertThat(event.getFormattedMessage()).contains("파일 로딩 완료"));
    }

    @Test
    void rejectsDuplicateKeysWithinFile() throws Exception {
        write("config/runtime/a.properties", "key=secret-one\nkey=secret-two\n");
        try (var loader = resourceLoader(directory)) {
            assertThatThrownBy(() -> RuntimeSettingsLoader.readProperties(loader))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("a.properties [key]")
                    .hasMessageNotContaining("secret");
        }
    }

    @Test
    void rejectsDuplicateKeysAcrossFiles() throws Exception {
        write("config/runtime/a.properties", "key=one\n");
        write("config/runtime/b.properties", "key=two\n");
        try (var loader = resourceLoader(directory)) {
            assertThatThrownBy(() -> RuntimeSettingsLoader.readProperties(loader))
                    .hasMessageContaining("b.properties [key]");
        }
    }

    @Test
    void rejectsSameResourcePathAcrossClasspathLocations() throws Exception {
        write("first/config/runtime/a.properties", "one=1\n");
        write("second/config/runtime/a.properties", "two=2\n");
        try (var loader = resourceLoader(directory.resolve("first"), directory.resolve("second"))) {
            assertThatThrownBy(() -> RuntimeSettingsLoader.readProperties(loader))
                    .hasMessageContaining("중복 런타임 설정 리소스")
                    .hasMessageContaining("config/runtime/a.properties");
        }
    }

    @Test
    void hidesMalformedValueAndSupportsNoConfiguredFields() throws Exception {
        try (var loader = resourceLoader(directory)) {
            assertThat(RuntimeSettingsLoader.readProperties(loader)).isEmpty();
        }
        write("config/runtime/a.properties", "secret=\\uSECRET\n");
        try (var loader = resourceLoader(directory)) {
            assertThatThrownBy(() -> RuntimeSettingsLoader.readProperties(loader))
                    .hasMessage("런타임 설정 파일 읽기 실패: a.properties")
                    .hasNoCause();
        }
    }

    @Test
    void initializesOnceOnConcurrentStaticAccessWithoutSpring() throws Exception {
        try (var loader = isolatedApplicationLoader();
                var executor = Executors.newFixedThreadPool(8)) {
            Class<?> config = Class.forName(RuntimeConfig.class.getName(), false, loader);
            Callable<Object> access = () -> config.getMethod("settings").invoke(null);
            var results = executor.invokeAll(java.util.Collections.nCopies(16, access));
            Object settings = results.getFirst().get();
            for (var result : results) {
                assertThat(result.get()).isSameAs(settings);
            }
            assertThat(settings.getClass().getClassLoader()).isSameAs(loader);
        }
    }

    @Test
    void staticInitializationFailurePreventsSubsequentAccess() throws Exception {
        write("config/runtime/failure.properties", "duplicate=one\nduplicate=two\n");
        try (var loader = isolatedApplicationLoader()) {
            Class<?> config = Class.forName(RuntimeConfig.class.getName(), false, loader);
            assertThatThrownBy(() -> config.getMethod("settings").invoke(null))
                    .isInstanceOf(ExceptionInInitializerError.class);
            assertThatThrownBy(() -> config.getMethod("settings").invoke(null))
                    .isInstanceOf(NoClassDefFoundError.class);
        }
    }

    private void write(String name, String content) throws Exception {
        Path file = directory.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private URLClassLoader resourceLoader(Path... paths) throws Exception {
        List<URL> urls = new ArrayList<>();
        for (Path path : paths) {
            urls.add(path.toUri().toURL());
        }
        return new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
    }

    private URLClassLoader isolatedApplicationLoader() throws Exception {
        List<Path> paths = new ArrayList<>();
        paths.add(directory);
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            paths.add(Path.of(entry));
        }
        return resourceLoader(paths.toArray(Path[]::new));
    }
}
