package com.imageaiplatform.foundation.config;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

public final class RuntimeSettingsLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeSettingsLoader.class);
    private static final String LOCATION = "classpath*:config/runtime/*.properties";

    private RuntimeSettingsLoader() {
    }

    public static RuntimeSettings load() {
        Properties properties = readProperties(RuntimeSettingsLoader.class.getClassLoader());
        // 새 항목은 여기서 타입 변환·필수값 검증 후 RuntimeSettings 필드에 매핑합니다.
        return new RuntimeSettings();
    }

    static Properties readProperties(ClassLoader classLoader) {
        Properties merged = new Properties();
        Set<String> filenames = new HashSet<>();
        try {
            LOGGER.info("런타임 설정 탐색: 클래스패스 폴더=config/runtime/, 대상={}", LOCATION);
            List<Resource> resources = findResources(classLoader);
            if (resources.isEmpty()) {
                LOGGER.warn("런타임 설정 폴더 또는 설정 파일을 찾을 수 없습니다: {}. "
                        + "런타임 설정을 사용하려면 클래스패스에 config/runtime/ 폴더와 "
                        + "UTF-8 .properties 설정 파일을 생성하세요.", LOCATION);
            }
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (!filenames.add(filename)) {
                    throw new IllegalStateException("중복 런타임 설정 리소스: config/runtime/" + filename);
                }
                Properties file = new Properties() {
                    @Override
                    public synchronized Object put(Object key, Object value) {
                        if (containsKey(key) || merged.containsKey(key)) {
                            throw new IllegalStateException("중복 런타임 설정 키: " + filename + " [" + key + "]");
                        }
                        return super.put(key, value);
                    }
                };
                try {
                    var connection = resource.getURL().openConnection();
                    connection.setUseCaches(false);
                    try (Reader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                        file.load(reader);
                    }
                } catch (IOException | IllegalArgumentException exception) {
                    // 원본 예외에는 설정값이 포함될 수 있으므로 외부로 전달하지 않습니다.
                    throw new IllegalStateException("런타임 설정 파일 읽기 실패: " + filename);
                }
                merged.putAll(file);
                LOGGER.info("런타임 설정 파일 로딩 완료: {}", resource.getURL().toExternalForm());
            }
            return merged;
        } catch (IOException exception) {
            throw new IllegalStateException("런타임 설정 리소스 탐색 실패: " + LOCATION);
        }
    }

    private static List<Resource> findResources(ClassLoader classLoader) throws IOException {
        List<Resource> resources = new ArrayList<>();
        Set<String> urls = new HashSet<>();
        for (Resource resource : new PathMatchingResourcePatternResolver(classLoader).getResources(LOCATION)) {
            if (urls.add(resource.getURL().toExternalForm())) {
                resources.add(resource);
            }
        }
        // 디렉터리 엔트리가 없는 JAR은 ClassLoader.getResources(directory)로 찾을 수 없습니다.
        for (ClassLoader current = classLoader; current != null; current = current.getParent()) {
            if (!(current instanceof URLClassLoader urlClassLoader)) {
                continue;
            }
            for (var url : urlClassLoader.getURLs()) {
                if (!"file".equals(url.getProtocol()) || !url.getPath().endsWith(".jar")) {
                    continue;
                }
                try (JarFile jar = new JarFile(Path.of(url.toURI()).toFile())) {
                    var entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        String name = entries.nextElement().getName();
                        String prefix = "config/runtime/";
                        if (name.startsWith(prefix) && name.endsWith(".properties")
                                && name.indexOf('/', prefix.length()) < 0) {
                            Resource resource = new UrlResource("jar:" + url.toExternalForm() + "!/" + name);
                            if (urls.add(resource.getURL().toExternalForm())) {
                                resources.add(resource);
                            }
                        }
                    }
                } catch (java.net.URISyntaxException exception) {
                    throw new IOException("런타임 설정 JAR 경로 오류");
                }
            }
        }
        resources.sort(Comparator.comparing(Resource::getFilename));
        return resources;
    }
}
