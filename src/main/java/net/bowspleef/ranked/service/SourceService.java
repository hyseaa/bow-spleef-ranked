package net.bowspleef.ranked.service;

import lombok.extern.slf4j.Slf4j;
import net.bowspleef.ranked.dto.SourceRequest;
import net.bowspleef.ranked.dto.SourceResponse;
import net.bowspleef.ranked.exception.SourceAlreadyExistsException;
import net.bowspleef.ranked.mapper.SourceMapper;
import net.bowspleef.ranked.model.Source;
import net.bowspleef.ranked.model.SourceKind;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** The registered sources of matches (spec 033, ADR-0033). */
@Slf4j
@Service
public class SourceService {

    private final SourceMapper sourceMapper;

    public SourceService(SourceMapper sourceMapper) {
        this.sourceMapper = sourceMapper;
    }

    @Transactional
    public SourceResponse create(SourceRequest request) {
        log.info("Creating source: name={}, displayName={}, kind={}",
                request.name(), request.displayName(), request.kind().value());
        if (sourceMapper.findByName(request.name()).isPresent()) {
            log.warn("Source already exists: name={}", request.name());
            throw new SourceAlreadyExistsException(request.name());
        }
        Source source = new Source();
        source.setName(request.name());
        source.setDisplayName(request.displayName());
        source.setKind(request.kind().value());
        try {
            sourceMapper.insert(source);
        } catch (DuplicateKeyException e) {
            // two calls with one name at the same time: the second finds the key taken
            log.warn("Source already exists (concurrent creation): name={}", request.name());
            throw new SourceAlreadyExistsException(request.name());
        }
        log.info("Source created: name={}", source.getName());
        return toResponse(source);
    }

    @Transactional(readOnly = true)
    public List<SourceResponse> getAll() {
        log.debug("Fetching all sources");
        List<SourceResponse> result = sourceMapper.findAll().stream().map(SourceService::toResponse).toList();
        log.debug("Found {} source(s)", result.size());
        return result;
    }

    private static SourceResponse toResponse(Source source) {
        return new SourceResponse(source.getName(), source.getDisplayName(), SourceKind.fromValue(source.getKind()));
    }
}
