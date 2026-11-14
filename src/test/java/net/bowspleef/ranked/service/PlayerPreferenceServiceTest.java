package net.bowspleef.ranked.service;

import net.bowspleef.ranked.dto.PreferencesResponse;
import net.bowspleef.ranked.dto.UpdatePreferencesRequest;
import net.bowspleef.ranked.exception.InvalidPreferenceException;
import net.bowspleef.ranked.mapper.PlayerMapper;
import net.bowspleef.ranked.mapper.PlayerPreferenceMapper;
import net.bowspleef.ranked.model.PlayerPreference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Spec 016, ADR-0023: the preferences of a player, with the defaults for what was never set. */
@ExtendWith(MockitoExtension.class)
class PlayerPreferenceServiceTest {

    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private PlayerMapper playerMapper;
    @Mock
    private PlayerPreferenceMapper preferenceMapper;
    @InjectMocks
    private PlayerPreferenceService service;

    @Test
    void aPlayerWithNoRowHasTheDefaults_REQ_016_09() {
        when(preferenceMapper.findByPlayer(PLAYER)).thenReturn(Optional.empty());

        assertThat(service.get(PLAYER)).isEqualTo(new PreferencesResponse(0));
        verifyNoInteractions(playerMapper);
    }

    @Test
    void aStoredValueIsReturned_REQ_016_09() {
        PlayerPreference row = new PlayerPreference();
        row.setPlayerUuid(PLAYER);
        row.setTntRemovedPercent(40);
        when(preferenceMapper.findByPlayer(PLAYER)).thenReturn(Optional.of(row));

        assertThat(service.get(PLAYER)).isEqualTo(new PreferencesResponse(40));
    }

    @Test
    void anUpdateCreatesThePlayerIfAbsentThenKeepsTheValue_REQ_016_09() {
        PlayerPreference row = new PlayerPreference();
        row.setTntRemovedPercent(60);
        when(preferenceMapper.findByPlayer(PLAYER)).thenReturn(Optional.of(row));

        PreferencesResponse answer = service.update(PLAYER, new UpdatePreferencesRequest(60));

        InOrder order = inOrder(playerMapper, preferenceMapper);
        order.verify(playerMapper).insertIfAbsent(PLAYER);
        order.verify(preferenceMapper).upsert(PLAYER, 60);
        assertThat(answer).isEqualTo(new PreferencesResponse(60));
    }

    @Test
    void theBoundsZeroAndOneHundredAreAccepted_REQ_016_02() {
        service.update(PLAYER, new UpdatePreferencesRequest(0));
        service.update(PLAYER, new UpdatePreferencesRequest(100));

        verify(preferenceMapper).upsert(PLAYER, 0);
        verify(preferenceMapper).upsert(PLAYER, 100);
    }

    @Test
    void aValueOutsideTheRangeIsRefusedAndNothingIsWritten_REQ_016_02() {
        assertThatThrownBy(() -> service.update(PLAYER, new UpdatePreferencesRequest(101)))
                .isInstanceOf(InvalidPreferenceException.class);
        assertThatThrownBy(() -> service.update(PLAYER, new UpdatePreferencesRequest(-1)))
                .isInstanceOf(InvalidPreferenceException.class);

        verifyNoInteractions(playerMapper);
        verify(preferenceMapper, never()).upsert(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void anUpdateWithNoFieldIsRefused_REQ_016_09() {
        assertThatThrownBy(() -> service.update(PLAYER, new UpdatePreferencesRequest(null)))
                .isInstanceOf(InvalidPreferenceException.class);
        assertThatThrownBy(() -> service.update(PLAYER, null))
                .isInstanceOf(InvalidPreferenceException.class);

        verifyNoInteractions(playerMapper);
    }
}
