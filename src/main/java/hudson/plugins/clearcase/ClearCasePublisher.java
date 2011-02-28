package hudson.plugins.clearcase;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.plugins.clearcase.action.ClearCaseReportAction;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;

import java.io.IOException;
import java.io.Serializable;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

/**
 * Display ClearCase information report for build
 * 
 * @author Rinat Ailon
 */
public class ClearCasePublisher extends Notifier implements Serializable {
    @Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        build.addAction(new ClearCaseReportAction(build));
        return true;
    }

    /**
     * All global configurations in global.jelly are done from the DescriptorImpl class below
     * 
     * @author rgoren
     */
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        /*
         * This initializes the global configuration when loaded
         */
        public DescriptorImpl() {
            super(ClearCasePublisher.class);
            // This makes sure any existing global configuration is read from the persistence file <Hudson work
            // dir>/hudson.plugins.logparser.LogParserPublisher.xml
            load();
        }
        
        @Override
        public boolean isApplicable(Class jobType) {
            return true;
        }

        @Override
		public String getDisplayName() {
            return "Create ClearCase report";
        }

        @Override
		public String getHelpFile() {
            return "/plugin/clearcase/publisher.html";
        }

        /*
         * This method is invoked when the global configuration "save" is pressed
         */
        @Override
        public boolean configure(StaplerRequest req, JSONObject json)
                throws FormException {
            save();
            return true;
        }

    }

    @Override
	public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

}
