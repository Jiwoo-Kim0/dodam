package com.ssafy.dodam.domain.pattern.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * 학습된 모델 아티팩트(`app.pattern.model-location`)를 읽어 {@link ClusterModel}로 만든다. 한 번 읽으면 프로세스가 사는 동안 재사용한다
 * (아티팩트는 배포 단위로 바뀐다).
 *
 * <p>위치가 비어 있거나 파일이 없거나 형식이 틀리면 비어 있는 결과를 돌려주고 경고만 남긴다. 아티팩트 문제로 서버 기동이 막히면
 * 규칙 기반 지표까지 못 쓰게 되므로, 호출자({@link ClusterModelProvider})가 인프로세스 학습이나 RULE_ONLY로 넘어가게 한다.
 */
@Component
public class ClusterModelArtifactLoader {

    private static final Logger log = LoggerFactory.getLogger(ClusterModelArtifactLoader.class);

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final PatternProperties properties;
    private volatile Optional<ClusterModel> cached;

    public ClusterModelArtifactLoader(ResourceLoader resourceLoader, ObjectMapper objectMapper, PatternProperties properties) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Optional<ClusterModel> load() {
        Optional<ClusterModel> current = cached;
        if (current == null) {
            synchronized (this) {
                if (cached == null) {
                    cached = read();
                }
                current = cached;
            }
        }
        return current;
    }

    /** 다음 조회에서 다시 읽는다(테스트·운영 점검용). */
    public void reload() {
        cached = null;
    }

    private Optional<ClusterModel> read() {
        String location = properties.modelLocation();
        if (location == null || location.isBlank()) {
            log.info("Pattern cluster model artifact disabled (app.pattern.model-location is empty)");
            return Optional.empty();
        }
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            log.info("Pattern cluster model artifact not found at {} — falling back", location);
            return Optional.empty();
        }
        try (InputStream in = resource.getInputStream()) {
            ClusterModel model = parse(in, objectMapper);
            log.info("Pattern cluster model artifact loaded: version={} source={} trainedBy={} population={} clusters={} window={}~{}",
                    model.modelVersion(), location, model.trainedBy(), model.populationSize(), model.clusterCount(),
                    model.window().start(), model.window().end());
            return Optional.of(model);
        } catch (IOException | IllegalArgumentException exception) {
            log.warn("Pattern cluster model artifact at {} is unreadable — falling back", location, exception);
            return Optional.empty();
        }
    }

    static ClusterModel parse(InputStream in, ObjectMapper objectMapper) throws IOException {
        return objectMapper.readValue(in, ClusterModelArtifact.class).toModel();
    }
}
