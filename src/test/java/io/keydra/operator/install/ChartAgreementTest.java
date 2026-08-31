package io.keydra.operator.install;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.keydra.operator.v1alpha1.Keydra;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * The chart and the operator, checked against each other.
 *
 * <p>There are now two ways to install Keydra and they configure the same application. A variable
 * one of them sets and the other does not is a deployment that behaves differently depending on how
 * it was installed — and it is the kind of difference nobody finds until somebody migrates. The
 * chart's {@code deployment.yaml} is the older of the two and is treated as the reference.
 *
 * <p>The chart lives in another repository, so this test finds it or steps aside. CI checks both
 * out side by side, which is what makes it run where it matters; a developer with only one of them
 * gets a skip rather than a failure about a file they were never expected to have.
 */
class ChartAgreementTest {

    private static final Pattern ENV_NAME =
            Pattern.compile("- name: (KEYDRA_[A-Z0-9_]+|JAVA_OPTS)");

    /**
     * Variables the chart sets that the operator deliberately does not.
     *
     * <p>Empty, and that is the point: an entry here is a decision that the two installation paths
     * configure the application differently, and adding one means writing down which decision and
     * why. An empty set is the state where no such decision has been taken.
     */
    private static final Set<String> DELIBERATELY_DIFFERENT = Set.of();

    @Test
    void setsEveryVariableTheChartDoes() throws IOException {
        Path template = chart();
        Assumptions.assumeTrue(
                template != null,
                "The chart is not checked out beside this repository; nothing to compare against.");

        Set<String> inChart = new LinkedHashSet<>();
        Matcher matcher = ENV_NAME.matcher(Files.readString(template));
        while (matcher.find()) {
            inChart.add(matcher.group(1));
        }
        assertThat(inChart)
                .as(
                        "the template was read but nothing was found in it, which means the pattern"
                                + " no longer matches the file rather than that the two agree")
                .hasSizeGreaterThan(10);

        Set<String> inOperator = operatorVariables();

        assertThat(inOperator)
                .as("variables the chart sets and the operator does not")
                .containsAll(
                        inChart.stream()
                                .filter(name -> !DELIBERATELY_DIFFERENT.contains(name))
                                .toList());
    }

    @Test
    void setsNothingTheChartDoesNot() throws IOException {
        Path template = chart();
        Assumptions.assumeTrue(template != null, "The chart is not checked out beside this one.");

        Set<String> inChart = new LinkedHashSet<>();
        Matcher matcher = ENV_NAME.matcher(Files.readString(template));
        while (matcher.find()) {
            inChart.add(matcher.group(1));
        }

        assertThat(inChart)
                .as(
                        "variables the operator sets and the chart does not — which is a chart that"
                                + " needs the same field, not an operator that is ahead")
                .containsAll(operatorVariables());
    }

    private static Set<String> operatorVariables() {
        @SuppressWarnings("unchecked")
        Context<Keydra> context = mock(Context.class);
        when(context.getSecondaryResource(any(), anyString())).thenReturn(Optional.empty());
        Set<String> names = new LinkedHashSet<>();
        Environment.forApplication(Specs.maximal(), context).stream()
                .map(EnvVar::getName)
                .forEach(names::add);
        return names;
    }

    /** Where the chart is, if it is anywhere. */
    private static Path chart() {
        String named = System.getProperty("keydra.chart.deployment");
        if (named != null) {
            Path path = Path.of(named);
            return Files.exists(path) ? path : null;
        }
        Path sibling =
                Path.of("..", "keydra-helm", "charts", "keydra", "templates", "deployment.yaml");
        return Files.exists(sibling) ? sibling : null;
    }
}
