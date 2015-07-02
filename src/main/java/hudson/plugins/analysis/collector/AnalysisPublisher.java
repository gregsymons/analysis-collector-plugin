package hudson.plugins.analysis.collector; // NOPMD

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.FilePath;
import hudson.Launcher;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.analysis.collector.handler.*;
import hudson.plugins.analysis.core.BuildResult;
import hudson.plugins.analysis.core.HealthAwarePublisher;
import hudson.plugins.analysis.core.ParserResult;
import hudson.plugins.analysis.core.ResultAction;
import hudson.plugins.analysis.util.PluginLogger;
import hudson.plugins.analysis.util.model.FileAnnotation;

/**
 * Collects the results of the various analysis plug-ins.
 *
 * @author Ulli Hafner
 */
// CHECKSTYLE:COUPLING-OFF
public class AnalysisPublisher extends HealthAwarePublisher {
    private static final long serialVersionUID = 5512072640635006098L;

    private final boolean isCheckStyleDeactivated;
    private final boolean isDryDeactivated;
    private final boolean isFindBugsDeactivated;
    private final boolean isPmdDeactivated;
    private final boolean isOpenTasksDeactivated;
    private final boolean isWarningsDeactivated;

    /**
     * Creates a new instance of {@link AnalysisPublisher}.
     *
     * @param healthy
     *            Report health as 100% when the number of annotations is less
     *            than this value
     * @param unHealthy
     *            Report health as 0% when the number of annotations is greater
     *            than this value
     * @param thresholdLimit
     *            determines which warning priorities should be considered when
     *            evaluating the build stability and health
     * @param defaultEncoding
     *            the default encoding to be used when reading and parsing files
     * @param useDeltaValues
     *            determines whether the absolute annotations delta or the
     *            actual annotations set difference should be used to evaluate
     *            the build stability
     * @param unstableTotalAll
     *            annotation threshold
     * @param unstableTotalHigh
     *            annotation threshold
     * @param unstableTotalNormal
     *            annotation threshold
     * @param unstableTotalLow
     *            annotation threshold
     * @param unstableNewAll
     *            annotation threshold
     * @param unstableNewHigh
     *            annotation threshold
     * @param unstableNewNormal
     *            annotation threshold
     * @param unstableNewLow
     *            annotation threshold
     * @param failedTotalAll
     *            annotation threshold
     * @param failedTotalHigh
     *            annotation threshold
     * @param failedTotalNormal
     *            annotation threshold
     * @param failedTotalLow
     *            annotation threshold
     * @param failedNewAll
     *            annotation threshold
     * @param failedNewHigh
     *            annotation threshold
     * @param failedNewNormal
     *            annotation threshold
     * @param failedNewLow
     *            annotation threshold
     * @param isCheckStyleActivated
     *            determines whether to collect the warnings from Checkstyle
     * @param isDryActivated
     *            determines whether to collect the warnings from DRY
     * @param isFindBugsActivated
     *            determines whether to collect the warnings from FindBugs
     * @param isPmdActivated
     *            determines whether to collect the warnings from PMD
     * @param isOpenTasksActivated
     *            determines whether to collect open tasks
     * @param isWarningsActivated
     *            determines whether to collect compiler warnings
     * @param canRunOnFailed
     *            determines whether the plug-in can run for failed builds, too
     * @param usePreviousBuildAsReference
     *            determines whether the previous build should be used as the
     *            reference build
     * @param useStableBuildAsReference
     *            determines whether only stable builds should be used as reference builds or not
     * @param canComputeNew
     *            determines whether new warnings should be computed (with
     *            respect to baseline)
     */
    // CHECKSTYLE:OFF
    @SuppressWarnings("PMD.ExcessiveParameterList")
    @DataBoundConstructor
    public AnalysisPublisher(final String healthy, final String unHealthy, final String thresholdLimit,
            final String defaultEncoding, final boolean useDeltaValues,
            final String unstableTotalAll, final String unstableTotalHigh, final String unstableTotalNormal, final String unstableTotalLow,
            final String unstableNewAll, final String unstableNewHigh, final String unstableNewNormal, final String unstableNewLow,
            final String failedTotalAll, final String failedTotalHigh, final String failedTotalNormal, final String failedTotalLow,
            final String failedNewAll, final String failedNewHigh, final String failedNewNormal, final String failedNewLow,
            final boolean isCheckStyleActivated, final boolean isDryActivated, final boolean isFindBugsActivated,
            final boolean isPmdActivated, final boolean isOpenTasksActivated, final boolean isWarningsActivated,
            final boolean canRunOnFailed,
            final boolean usePreviousBuildAsReference, final boolean useStableBuildAsReference, final boolean canComputeNew) {
        super(healthy, unHealthy, thresholdLimit, defaultEncoding, useDeltaValues,
                unstableTotalAll, unstableTotalHigh, unstableTotalNormal, unstableTotalLow,
                unstableNewAll, unstableNewHigh, unstableNewNormal, unstableNewLow,
                failedTotalAll, failedTotalHigh, failedTotalNormal, failedTotalLow,
                failedNewAll, failedNewHigh, failedNewNormal, failedNewLow,
                canRunOnFailed, usePreviousBuildAsReference, useStableBuildAsReference, false, canComputeNew, false, "ANALYSIS-COLLECTOR");
        isDryDeactivated = !isDryActivated;
        isFindBugsDeactivated = !isFindBugsActivated;
        isPmdDeactivated = !isPmdActivated;
        isOpenTasksDeactivated = !isOpenTasksActivated;
        isWarningsDeactivated = !isWarningsActivated;
        isCheckStyleDeactivated = !isCheckStyleActivated;
    }
    // CHECKSTYLE:ON

    /**
     * Returns whether CheckStyle results should be collected.
     *
     * @return <code>true</code> if CheckStyle results should be collected, <code>false</code> otherwise
     */
    public boolean isCheckStyleActivated() {
        return !isCheckStyleDeactivated;
    }

    /**
     * Returns whether DRY results should be collected.
     *
     * @return <code>true</code> if DRY results should be collected, <code>false</code> otherwise
     */
    public boolean isDryActivated() {
        return !isDryDeactivated;
    }

    /**
     * Returns whether FindBugs results should be collected.
     *
     * @return <code>true</code> if FindBugs results should be collected, <code>false</code> otherwise
     */
    public boolean isFindBugsActivated() {
        return !isFindBugsDeactivated;
    }

    /**
     * Returns whether PMD results should be collected.
     *
     * @return <code>true</code> if PMD results should be collected, <code>false</code> otherwise
     */
    public boolean isPmdActivated() {
        return !isPmdDeactivated;
    }

    /**
     * Returns whether open tasks should be collected.
     *
     * @return <code>true</code> if open tasks should be collected, <code>false</code> otherwise
     */
    public boolean isOpenTasksActivated() {
        return !isOpenTasksDeactivated;
    }

    /**
     * Returns whether compiler warnings results should be collected.
     *
     * @return <code>true</code> if compiler warnings results should be collected, <code>false</code> otherwise
     */
    public boolean isWarningsActivated() {
        return !isWarningsDeactivated;
    }

    /**
     * Initializes the plug-ins that should participate in the results of this
     * analysis collector.
     *
     * @return the plug-in actions to read the results from
     */
    @SuppressWarnings({"PMD.NPathComplexity", "PMD.CyclomaticComplexity"})
    private List<Class<? extends ResultAction<? extends BuildResult>>> getParticipatingPlugins() {
        ArrayList<Class<? extends ResultAction<? extends BuildResult>>> pluginResults;
        pluginResults = new ArrayList<Class<? extends ResultAction<? extends BuildResult>>>();

        if (AnalysisDescriptor.isCheckStyleInstalled() && isCheckStyleActivated()) {
            pluginResults.addAll(new CheckStyleHandler().getResultActions());
        }
        if (AnalysisDescriptor.isDryInstalled() && isDryActivated()) {
            pluginResults.addAll(new DryHandler().getResultActions());
        }
        if (AnalysisDescriptor.isFindBugsInstalled() && isFindBugsActivated()) {
            pluginResults.addAll(new FindBugsHandler().getResultActions());
        }
        if (AnalysisDescriptor.isPmdInstalled() && isPmdActivated()) {
            pluginResults.addAll(new PmdHandler().getResultActions());
        }
        if (AnalysisDescriptor.isOpenTasksInstalled() && isOpenTasksActivated()) {
            pluginResults.addAll(new TasksHandler().getResultActions());
        }
        if (AnalysisDescriptor.isWarningsInstalled() && isWarningsActivated()) {
            pluginResults.addAll(new WarningsHandler().getResultActions());
        }

        return pluginResults;
    }

    @Override
    public Action getProjectAction(final AbstractProject<?, ?> project) {
        return new AnalysisProjectAction(project);
    }

    @Override
    public BuildResult perform(final Run<?, ?> build, final FilePath workspace, final TaskListener listener, final PluginLogger logger) throws InterruptedException, IOException {
        ParserResult overallResult = new ParserResult(workspace);
        for (Class<? extends ResultAction<? extends BuildResult>> result : getParticipatingPlugins()) {
            ResultAction<? extends BuildResult> action = build.getAction(result);
            if (action != null) {
                BuildResult actualResult = action.getResult();
                Collection<FileAnnotation> annotations = actualResult.getAnnotations();
                overallResult.addAnnotations(annotations);
            }
        }

        AnalysisResult result = new AnalysisResult(build, getDefaultEncoding(), overallResult,
                usePreviousBuildAsReference(), useOnlyStableBuildsAsReference());
        build.addAction(new AnalysisResultAction(build, this, result));

        return result;
    }

    @Override
    public AnalysisDescriptor getDescriptor() {
        return (AnalysisDescriptor)super.getDescriptor();
    }

    @Override
    public MatrixAggregator createAggregator(final MatrixBuild build, final Launcher launcher,
            final BuildListener listener) {
        return new AnalysisAnnotationsAggregator(build, launcher, listener, this, getDefaultEncoding(),
                usePreviousBuildAsReference(), useOnlyStableBuildsAsReference());
    }
}
