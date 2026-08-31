package io.keydra.operator.status;

import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.model.Condition;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The one thing about a condition that is easy to get wrong and impossible to notice afterwards.
 *
 * <p>{@code lastTransitionTime} answers "how long has it been like this", which is the question
 * somebody has at three in the morning. An implementation that stamps the clock on every write
 * answers "a second ago" for ever, and nothing about the resource looks wrong.
 */
class ConditionsTest {

    @Test
    void keepsTheTransitionTimeWhileTheAnswerHasNotChanged() {
        List<Condition> conditions = new ArrayList<>();
        Conditions.set(conditions, "Available", true, "Ready", "up", 1L);
        String first = conditions.get(0).getLastTransitionTime();

        Conditions.set(conditions, "Available", true, "Ready", "still up", 2L);

        assertThat(conditions).hasSize(1);
        assertThat(conditions.get(0).getLastTransitionTime()).isEqualTo(first);
        assertThat(conditions.get(0).getMessage()).isEqualTo("still up");
        assertThat(conditions.get(0).getObservedGeneration()).isEqualTo(2L);
    }

    @Test
    void movesItWhenTheAnswerChanges() throws InterruptedException {
        List<Condition> conditions = new ArrayList<>();
        Conditions.set(conditions, "Available", true, "Ready", "up", 1L);
        String first = conditions.get(0).getLastTransitionTime();

        // The stamp has a second's resolution, which is what Kubernetes stores.
        Thread.sleep(1100);
        Conditions.set(conditions, "Available", false, "Gone", "down", 1L);

        assertThat(conditions.get(0).getStatus()).isEqualTo("False");
        assertThat(conditions.get(0).getLastTransitionTime()).isNotEqualTo(first);
    }

    @Test
    void keepsOneConditionPerTypeAndOrdersThem() {
        List<Condition> conditions = new ArrayList<>();
        Conditions.set(conditions, "Progressing", true, "Rolling", "…", 1L);
        Conditions.set(conditions, "Available", false, "None", "…", 1L);
        Conditions.set(conditions, "Degraded", false, "Fine", "…", 1L);
        Conditions.set(conditions, "Available", true, "Ready", "…", 1L);

        assertThat(conditions)
                .extracting(Condition::getType)
                .containsExactly("Available", "Degraded", "Progressing");
    }
}
