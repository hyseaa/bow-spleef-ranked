package net.bowspleef.ranked.service;

import net.bowspleef.ranked.model.RatingAlgorithm;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Every {@link RatingEngine}, by algorithm. Fails at startup if an algorithm has no engine or two. */
@Component
public class RatingEngines {

    private final Map<RatingAlgorithm, RatingEngine> engines = new EnumMap<>(RatingAlgorithm.class);

    public RatingEngines(List<RatingEngine> available) {
        for (RatingEngine engine : available) {
            if (engines.putIfAbsent(engine.algorithm(), engine) != null) {
                throw new IllegalStateException("Two rating engines for " + engine.algorithm());
            }
        }
        for (RatingAlgorithm algorithm : RatingAlgorithm.values()) {
            if (!engines.containsKey(algorithm)) {
                throw new IllegalStateException("No rating engine for " + algorithm);
            }
        }
    }

    public RatingEngine get(RatingAlgorithm algorithm) {
        return engines.get(algorithm);
    }
}
