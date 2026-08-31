package io.keydra.operator.status;

import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Writing a condition without destroying what it was.
 *
 * <p>{@code lastTransitionTime} is the field that makes conditions worth having and the one an
 * implementation gets wrong by not thinking about it. It is the moment the answer <em>changed</em>,
 * not the moment it was last written — an operator that stamps the clock on every reconciliation
 * turns "this has been down for forty minutes" into "this was down a second ago", which is the one
 * thing somebody reading a condition at three in the morning needs.
 */
public final class Conditions {

    private Conditions() {}

    private static final DateTimeFormatter RFC3339 =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    public static final String TRUE = "True";
    public static final String FALSE = "False";

    /**
     * Replaces the condition of this type, keeping its transition time when the answer has not
     * changed.
     */
    public static void set(
            List<Condition> conditions,
            String type,
            boolean met,
            String reason,
            String message,
            Long observedGeneration) {

        String status = met ? TRUE : FALSE;
        Optional<Condition> existing =
                conditions.stream()
                        .filter(condition -> type.equals(condition.getType()))
                        .findFirst();

        String transitioned =
                existing.filter(condition -> status.equals(condition.getStatus()))
                        .map(Condition::getLastTransitionTime)
                        .orElseGet(Conditions::now);

        existing.ifPresent(conditions::remove);
        conditions.add(
                new ConditionBuilder()
                        .withType(type)
                        .withStatus(status)
                        .withReason(reason)
                        .withMessage(message)
                        .withLastTransitionTime(transitioned)
                        .withObservedGeneration(observedGeneration)
                        .build());
        conditions.sort((left, right) -> left.getType().compareTo(right.getType()));
    }

    private static String now() {
        return ZonedDateTime.now(ZoneOffset.UTC).format(RFC3339);
    }
}
