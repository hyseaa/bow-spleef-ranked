package net.bowspleef.ranked.service;

import net.bowspleef.ranked.dto.SourceRequest;
import net.bowspleef.ranked.dto.SourceResponse;
import net.bowspleef.ranked.exception.SourceAlreadyExistsException;
import net.bowspleef.ranked.mapper.SourceMapper;
import net.bowspleef.ranked.model.Source;
import net.bowspleef.ranked.model.SourceKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Spec 033: registering and listing the sources of matches. */
class SourceServiceTest {

    private SourceMapper mapper;
    private SourceService service;

    @BeforeEach
    void setUp() {
        mapper = mock(SourceMapper.class);
        service = new SourceService(mapper);
    }

    private static Source source(String name, String displayName, String kind) {
        Source s = new Source();
        s.setName(name);
        s.setDisplayName(displayName);
        s.setKind(kind);
        return s;
    }

    @Test
    void aNewSourceIsStoredWithTheValueOfItsKind_REQ_033_04() {
        when(mapper.findByName("asia-server")).thenReturn(Optional.empty());

        SourceResponse response = service.create(
                new SourceRequest("asia-server", "Hy Sea's Asia server", SourceKind.CUSTOM_SERVER));

        ArgumentCaptor<Source> stored = ArgumentCaptor.forClass(Source.class);
        verify(mapper).insert(stored.capture());
        assertThat(stored.getValue().getKind()).isEqualTo("custom_server");
        assertThat(response).isEqualTo(
                new SourceResponse("asia-server", "Hy Sea's Asia server", SourceKind.CUSTOM_SERVER));
    }

    @Test
    void aNameAlreadyRegisteredIsRefused_REQ_033_04() {
        when(mapper.findByName("hysea-server"))
                .thenReturn(Optional.of(source("hysea-server", "Hy Sea's server", "custom_server")));

        assertThatThrownBy(() -> service.create(new SourceRequest("hysea-server", "X", SourceKind.BOT)))
                .isInstanceOf(SourceAlreadyExistsException.class);
        verify(mapper, never()).insert(any());
    }

    @Test
    void aNameTakenByAConcurrentCallIsRefused_REQ_033_04() {
        when(mapper.findByName("asia-server")).thenReturn(Optional.empty());
        doThrow(new DuplicateKeyException("duplicate")).when(mapper).insert(any());

        assertThatThrownBy(() -> service.create(new SourceRequest("asia-server", "X", SourceKind.CUSTOM_SERVER)))
                .isInstanceOf(SourceAlreadyExistsException.class);
    }

    @Test
    void everySourceIsListedWithItsKind_REQ_033_03() {
        when(mapper.findAll()).thenReturn(List.of(
                source("discord_bot", "Discord bot", "bot"),
                source("hysea-server", "Hy Sea's server", "custom_server")));

        assertThat(service.getAll()).containsExactly(
                new SourceResponse("discord_bot", "Discord bot", SourceKind.BOT),
                new SourceResponse("hysea-server", "Hy Sea's server", SourceKind.CUSTOM_SERVER));
    }
}
