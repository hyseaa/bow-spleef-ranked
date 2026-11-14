package net.bowspleef.ranked.mapper;

import net.bowspleef.ranked.model.PlayerPreference;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;
import java.util.UUID;

@Mapper
public interface PlayerPreferenceMapper {

    @Select("SELECT player_uuid, tnt_removed_percent, updated_at FROM player_preference WHERE player_uuid = #{uuid}")
    Optional<PlayerPreference> findByPlayer(UUID uuid);

    /** Inserts the row of a player or replaces its values; the player must exist (foreign key). */
    @Insert("INSERT INTO player_preference (player_uuid, tnt_removed_percent) VALUES (#{uuid}, #{tntRemovedPercent}) "
            + "ON CONFLICT (player_uuid) DO UPDATE SET tnt_removed_percent = EXCLUDED.tnt_removed_percent, updated_at = NOW()")
    void upsert(@Param("uuid") UUID uuid, @Param("tntRemovedPercent") int tntRemovedPercent);
}
