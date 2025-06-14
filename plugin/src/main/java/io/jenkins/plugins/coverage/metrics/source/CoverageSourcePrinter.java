package io.jenkins.plugins.coverage.metrics.source;

import edu.hm.hafner.coverage.FileNode;
import io.jenkins.plugins.prism.Sanitizer;
import org.apache.commons.lang3.StringUtils;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static j2html.TagCreator.*;

/**
 * Provides all required information for a {@link FileNode} so that its source code can be rendered together with the
 * line and branch coverage in HTML.
 */
class CoverageSourcePrinter implements Serializable {
    @Serial
    private static final long serialVersionUID = -6044649044983631852L;

    static final Sanitizer SANITIZER = new Sanitizer();
    static final String UNDEFINED = "noCover";
    static final String MODIFIED = "modified";
    static final String NO_COVERAGE = "coverNone";
    static final String FULL_COVERAGE = "coverFull";
    static final String PARTIAL_COVERAGE = "coverPart";
    static final String NBSP = "&nbsp;";

    private final String path;
    private final int[] linesToPaint;
    private final int[] coveredPerLine;

    private final int[] missedPerLine;

    private final Set<Integer> modifiedLines;

    CoverageSourcePrinter(final FileNode file) {
        path = file.getRelativePath();

        linesToPaint = file.getLinesWithCoverage().stream().mapToInt(i -> i).toArray();
        coveredPerLine = file.getCoveredCounters();
        missedPerLine = file.getMissedCounters();
        modifiedLines = file.getModifiedLines();
    }

    public String renderLine(final int line, final String sourceCode) {
        var isPainted = isPainted(line);
        return tr()
                .withClass(isPainted ? getColorClass(line) : UNDEFINED)
                .condAttr(isPainted, "data-html-tooltip", isPainted ? getTooltip(line) : StringUtils.EMPTY)
                .with(
                        td().withClass("line")
                                .with(a().withName(String.valueOf(line)).withText(String.valueOf(line))),
                        td().withClass("hits")
                                .with(isPainted ? text(getSummaryColumn(line)) : text(StringUtils.EMPTY)),
                        td().withClass("code")
                                .with(rawHtml(SANITIZER.render(cleanupCode(sourceCode)))))
                .render();
    }

    protected String cleanupCode(final String content) {
        return content.replace("\n", StringUtils.EMPTY)
                .replace("\r", StringUtils.EMPTY)
                .replace(" ", NBSP)
                .replace("\t", NBSP.repeat(8));
    }

    final int size() {
        return linesToPaint.length;
    }

    public String getModifiedColorClass(final int line) {
        return isModified(line) ? MODIFIED : "";
    }

    public String getColorClass(final int line) {
        String coverageClass;
        if (getCovered(line) == 0) {
            coverageClass = NO_COVERAGE;
        }
        else if (getMissed(line) == 0) {
            coverageClass = FULL_COVERAGE;
        }
        else if (findIndexOfLine(line) >= 0) {
            coverageClass = PARTIAL_COVERAGE;
        }
        else {
            coverageClass = "";
        }

        return Stream.of(getModifiedColorClass(line), coverageClass)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(" "));
    }

    public String getTooltipPrefix(final int line) {
        return isModified(line) ? "Modified" : "";
    }

    public String getTooltip(final int line) {
        var covered = getCovered(line);
        var missed = getMissed(line);
        String suffix;
        if (covered + missed > 1) {
            if (missed == 0) {
                suffix = "All branches covered";
            }
            else if (covered == 0) {
                suffix = "No branches covered";
            }
            else {
                suffix = "Partially covered, branch coverage: %d/%d".formatted(covered, covered + missed);
            }
        }
        else if (covered == 1) {
            suffix = "Covered at least once";
        }
        else {
            suffix = "Not covered";
        }

        var prefix = getTooltipPrefix(line);
        if (StringUtils.isEmpty(prefix)) {
            return suffix;
        } else {
            return String.join(", ", prefix, StringUtils.uncapitalize(suffix));
        }
    }

    public String getSummaryColumn(final int line) {
        var covered = getCovered(line);
        var missed = getMissed(line);
        if (covered + missed > 1) {
            return "%d/%d".formatted(covered, covered + missed);
        }
        return String.valueOf(covered);
    }

    public final String getPath() {
        return path;
    }

    public boolean isPainted(final int line) {
        return findIndexOfLine(line) >= 0 || isModified(line);
    }

    public boolean isModified(int line) {
        return modifiedLines.contains(line);
    }

    int findIndexOfLine(final int line) {
        return Arrays.binarySearch(linesToPaint, line);
    }

    public int getCovered(final int line) {
        return getCounter(line, coveredPerLine);
    }

    public int getMissed(final int line) {
        return getCounter(line, missedPerLine);
    }

    int getCounter(final int line, final int... counters) {
        var index = findIndexOfLine(line);
        if (index >= 0) {
            return counters[index];
        }
        return 0;
    }

    public String getColumnHeader() {
        return StringUtils.EMPTY;
    }
}
