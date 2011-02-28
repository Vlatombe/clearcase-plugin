/**
 * The MIT License
 *
 * Copyright (c) 2007-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt,
 *                          Henrik Lynggaard, Peter Liljenberg, Andrew Bayer
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.clearcase.ucm;

import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.ModelObject;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.Hudson;
import hudson.plugins.clearcase.action.CheckOutAction;
import hudson.plugins.clearcase.base.ClearCaseSCM;
import hudson.plugins.clearcase.base.ClearCaseSCM.ClearCaseScmDescriptor;
import hudson.plugins.clearcase.exec.ClearTool;
import hudson.plugins.clearcase.exec.ClearToolDynamic;
import hudson.plugins.clearcase.history.action.HistoryAction;
import hudson.plugins.clearcase.history.action.SaveChangeLogAction;
import hudson.plugins.clearcase.history.model.ChangeSetLevel;
import hudson.plugins.clearcase.launcher.ClearToolLauncher;
import hudson.plugins.clearcase.model.AbstractClearCaseRevision;
import hudson.plugins.clearcase.scm.AbstractClearCaseSCM;
import hudson.plugins.clearcase.util.BuildVariableResolver;
import hudson.scm.ChangeLogParser;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.scm.SCM;
import hudson.util.VariableResolver;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * SCM for ClearCaseUCM. This SCM will create a UCM view from a stream and apply a list of load rules to it.
 */
public class ClearCaseUcmSCM extends AbstractClearCaseSCM {

    private static final String STREAM_PREFIX = "stream:";

    private final static String AUTO_ALLOCATE_VIEW_NAME = "${STREAM}_${JOB_NAME}_bs_hudson_view";

    private final String stream;
    private final String overrideBranchName;
    private boolean allocateViewName;
    private final boolean useManualLoadRules;
    @DataBoundConstructor
    public ClearCaseUcmSCM(String stream, String loadrules, String viewTag, boolean usedynamicview, String viewdrive, String mkviewoptionalparam,
            boolean filterOutDestroySubBranchEvent, boolean useUpdate, boolean rmviewonrename, String excludedRegions, String multiSitePollBuffer,
            String overrideBranchName, boolean createDynView, String winDynStorageDir, String unixDynStorageDir, boolean freezeCode, boolean recreateView,
            boolean allocateViewName, String viewPath, boolean useManualLoadRules, ChangeSetLevel changeset) {
        super(viewTag, mkviewoptionalparam, filterOutDestroySubBranchEvent, useUpdate, rmviewonrename, excludedRegions, usedynamicview, viewdrive, useManualLoadRules ? loadrules : null,
                multiSitePollBuffer, createDynView, winDynStorageDir, unixDynStorageDir, freezeCode, recreateView, viewPath, changeset);
        this.stream = shortenStreamName(stream);
        this.allocateViewName = allocateViewName;
        this.overrideBranchName = overrideBranchName;
        this.useManualLoadRules = useManualLoadRules ? useManualLoadRules : StringUtils.isNotBlank(loadrules); // Default to keep backward compat 
        Validate.notEmpty(this.stream, "The stream selector cannot be empty");
    }

    @Deprecated
    public ClearCaseUcmSCM(String stream, String loadrules, String viewTag, boolean usedynamicview, String viewdrive, String mkviewoptionalparam,
            boolean filterOutDestroySubBranchEvent, boolean useUpdate, boolean rmviewonrename) {
        this(stream, loadrules, viewTag, usedynamicview, viewdrive, mkviewoptionalparam, filterOutDestroySubBranchEvent, useUpdate, rmviewonrename, "", null,
                "", false, null, null, false, false, false, viewTag, StringUtils.isBlank(loadrules), ChangeSetLevel.defaultLevel());
    }



    /**
     * Return the stream that is used to create the UCM view.
     * 
     * @return string containing the stream selector.
     */
    public String getStream() {
        return stream;
    }
    
    public String getStream(VariableResolver<String> variableResolver) {
        return Util.replaceMacro(stream, variableResolver);
    }
    
    public boolean isAllocateViewName() {
        return allocateViewName;
    }

    public void setAllocateViewName(boolean allocateViewName) {
        this.allocateViewName = allocateViewName;
    }

    /**
     * Return the branch type used for changelog and polling. By default this will be the empty string, and the stream
     * will be split to get the branch.
     * 
     * @return string containing the branch type.
     */
    public String getOverrideBranchName() {
        return overrideBranchName;
    }

    @Override
    public ClearCaseUcmScmDescriptor getDescriptor() {
        return (ClearCaseUcmScmDescriptor) super.getDescriptor();
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return new UcmChangeLogParser();
    }
    
    public boolean isUseManualLoadRules() {
        return useManualLoadRules;
    }

    @Override
    public String[] getBranchNames(VariableResolver<String> variableResolver) {
        String override = Util.replaceMacro(overrideBranchName, variableResolver);
        if (StringUtils.isNotEmpty(override)) {
            return new String[] { override };
        } else {
            return new String[] { UcmCommon.getNoVob(getStream(variableResolver)) };
        }
    }
    
    @Override
    public SCMRevisionState calcRevisionsFromBuild(AbstractBuild<?, ?> build, Launcher launcher, TaskListener taskListener) throws IOException,
            InterruptedException {
        return createRevisionState(build, launcher, taskListener, getBuildTime(build));
    }
    
    @Override
    public SCMRevisionState calcRevisionsFromPoll(AbstractBuild<?, ?> build, Launcher launcher, TaskListener taskListener) throws IOException,
            InterruptedException {
        return createRevisionState(build, launcher, taskListener, new Date());
    }
    
    private AbstractClearCaseRevision createRevisionState(AbstractBuild<?, ?> build, Launcher launcher, TaskListener taskListener, Date date) throws IOException, InterruptedException {
        ClearTool clearTool = createClearTool(build, launcher);
        VariableResolver<String> variableResolver = new BuildVariableResolver(build);
        String resolvedStream = getStream(variableResolver);
        UCMRevision revisionState = new UCMRevision(UcmCommon.getFoundationBaselines(clearTool, resolvedStream), date, resolvedStream);
        revisionState.setLoadRules(getViewPaths(variableResolver, build, launcher));
        return revisionState; 
    }
    
    @Override
    protected boolean isFirstBuild(SCMRevisionState baseline) {
        return baseline == null || !(baseline instanceof UCMRevision);
    }
    
    @Override
    public String generateNormalizedViewName(VariableResolver<String> variableResolver, String modViewName) {
        // Modify the view name in order to support concurrent builds
        if (allocateViewName) {
            modViewName = AUTO_ALLOCATE_VIEW_NAME.replace("${STREAM}", UcmCommon.getNoVob(getStream(variableResolver)));
        }
        return super.generateNormalizedViewName(variableResolver, modViewName);
    }

    @Override
    protected CheckOutAction createCheckOutAction(VariableResolver<String> variableResolver, ClearToolLauncher launcher, AbstractBuild<?, ?> build) throws IOException, InterruptedException {
        CheckOutAction action;
        if (isUseDynamicView()) {
            action = new UcmDynamicCheckoutAction(createClearTool(variableResolver, launcher), getStream(variableResolver), isCreateDynView(),
                    getNormalizedWinDynStorageDir(variableResolver), getNormalizedUnixDynStorageDir(variableResolver), build, isFreezeCode(), isRecreateView());
        } else {
            action = new UcmSnapshotCheckoutAction(createClearTool(variableResolver, launcher), getStream(variableResolver), getViewPaths(variableResolver, build, launcher.getLauncher()), isUseUpdate(), getViewPath(variableResolver));
        }
        return action;
    }

    @Override
	protected HistoryAction createHistoryAction(VariableResolver<String> variableResolver, ClearToolLauncher launcher, AbstractBuild<?, ?> build) throws IOException, InterruptedException {
        ClearTool ct = createClearTool(variableResolver, launcher);
        UcmHistoryAction action;
        UCMRevision oldBaseline = null;
        UCMRevision newBaseline = null;
        PrintStream logger = launcher.getListener().getLogger();
        if (build != null) {
            try {
                AbstractBuild<?, ?> previousBuild = build.getPreviousBuild();
                if (previousBuild != null) {
                    oldBaseline = build.getPreviousBuild().getAction(UCMRevision.class);
                }
                newBaseline = (UCMRevision) calcRevisionsFromBuild(build, launcher.getLauncher(), launcher.getListener());
            } catch (IOException e) {
                Logger.getLogger(ClearCaseUcmSCM.class.getName()).log(Level.SEVERE, "IOException when calculating revisions'", e);
                e.printStackTrace(logger);
                return null;
            } catch (InterruptedException e) {
                Logger.getLogger(ClearCaseUcmSCM.class.getName()).log(Level.SEVERE, "InterruptedException when calculating revisions'", e);
                e.printStackTrace(logger);
                return null;
            }
        }
        if (isFreezeCode()) {
            action = new FreezeCodeUcmHistoryAction(ct, isUseDynamicView(), configureFilters(variableResolver, build, launcher.getLauncher()), getStream(variableResolver), getViewDrive(), build, oldBaseline, newBaseline);
        } else {
            action = new UcmHistoryAction(ct, isUseDynamicView(), configureFilters(variableResolver, build, launcher.getLauncher()), oldBaseline, newBaseline, getChangeset());
        }
        try {
            String pwv = ct.pwv(generateNormalizedViewName(variableResolver));

            if (pwv != null) {
                if (pwv.contains("/")) {
                    pwv += "/";
                } else {
                    pwv += "\\";
                }
                action.setExtendedViewPath(pwv);
            }
        } catch (Exception e) {
            Logger.getLogger(ClearCaseUcmSCM.class.getName()).log(Level.WARNING, "Exception when running 'cleartool pwv'", e);
        }

        return action;
    }

    @Override
    public String[] getViewPaths(VariableResolver<String> variableResolver, AbstractBuild build, Launcher launcher) throws IOException, InterruptedException {
        if (!useManualLoadRules) {
            // If the revision state is already available for this build, just use the value
            UCMRevision revisionState = build.getAction(UCMRevision.class);
            if (revisionState != null) {
                String[] lr = revisionState.getLoadRules();
                if (lr != null) {
                    return lr;
                }
            }
            ClearTool clearTool = createClearTool(build, launcher);
            List<Baseline> latest = UcmCommon.getLatestBaselines(clearTool, getStream(variableResolver));
            return UcmCommon.generateLoadRulesFromBaselines(clearTool, getStream(variableResolver), latest);
        } else {
            return super.getViewPaths(variableResolver, build, launcher);
        }
    }

    @Override
    protected SaveChangeLogAction createSaveChangeLogAction(ClearToolLauncher launcher) {
        return new UcmSaveChangeLogAction();
    }

    @Override
    public ClearTool createClearTool(VariableResolver<String> variableResolver, ClearToolLauncher launcher) {
        if (isUseDynamicView()) {
            return new ClearToolDynamic(variableResolver, launcher, getViewDrive(), getMkviewOptionalParam());
        } else {
            return super.createClearTool(variableResolver, launcher);
        }
    }
    
    public ClearTool createClearTool(AbstractBuild<?, ?> build, Launcher launcher) {
        BuildVariableResolver variableResolver = new BuildVariableResolver(build);
        ClearToolLauncher clearToolLauncher = createClearToolLauncher(launcher.getListener(), build.getWorkspace(), launcher);
        return createClearTool(variableResolver, clearToolLauncher);
    }

    private String shortenStreamName(String longStream) {
        if (StringUtils.startsWith(longStream, STREAM_PREFIX)) {
            return StringUtils.substringAfter(longStream, STREAM_PREFIX);
        } else {
            return longStream;
        }
    }

    /**
     * ClearCase UCM SCM descriptor
     * 
     * @author Erik Ramfelt
     */
    @Extension
    public static class ClearCaseUcmScmDescriptor extends SCMDescriptor<ClearCaseUcmSCM> implements ModelObject {

        private ClearCaseScmDescriptor baseDescriptor;
        
        public ClearCaseUcmScmDescriptor() {
            super(ClearCaseUcmSCM.class, null);
            load();
        }

        public String getDefaultViewName() {
            return getBaseDescriptor().getDefaultViewName();
        }

		private ClearCaseScmDescriptor getBaseDescriptor() {
			if (baseDescriptor == null) {
				baseDescriptor = (ClearCaseSCM.ClearCaseScmDescriptor) Hudson.getInstance().getDescriptorOrDie(ClearCaseSCM.class);
			}
			return baseDescriptor;
		}
        
        public String getDefaultViewPath() {
            return getBaseDescriptor().getDefaultViewPath();
        }

        @Override
        public String getDisplayName() {
            return "UCM ClearCase";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) {
            return true;
        }

        public String getDefaultWinDynStorageDir() {
            return getBaseDescriptor().getDefaultWinDynStorageDir();
        }

        public String getDefaultUnixDynStorageDir() {
            return getBaseDescriptor().getDefaultUnixDynStorageDir();
        }

        @Override
        public SCM newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            ClearCaseUcmSCM scm = new ClearCaseUcmSCM(req.getParameter("ucm.stream"),
                                                      req.getParameter("ucm.loadrules"),
                                                      req.getParameter("ucm.viewname"),
                                                      req.getParameter("ucm.usedynamicview") != null,
                                                      req.getParameter("ucm.viewdrive"),
                                                      req.getParameter("ucm.mkviewoptionalparam"),
                                                      req.getParameter("ucm.filterOutDestroySubBranchEvent") != null,
                                                      req.getParameter("ucm.useupdate") != null,
                                                      req.getParameter("ucm.rmviewonrename") != null,
                                                      req.getParameter("ucm.excludedRegions"),
                                                      Util.fixEmpty(req.getParameter("ucm.multiSitePollBuffer")),
                                                      req.getParameter("ucm.overrideBranchName"),
                                                      req.getParameter("ucm.createDynView") != null,
                                                      req.getParameter("ucm.winDynStorageDir"),
                                                      req.getParameter("ucm.unixDynStorageDir"),
                                                      req.getParameter("ucm.freezeCode") != null,
                                                      req.getParameter("ucm.recreateView") != null,
                                                      req.getParameter("ucm.allocateViewName") != null,
                                                      req.getParameter("ucm.viewpath"),
                                                      req.getParameter("ucm.useManualLoadRules") != null,
                                                      ChangeSetLevel.fromString(req.getParameter("ucm.changeset"))
                                                      );
            return scm;
        }
    }
}
