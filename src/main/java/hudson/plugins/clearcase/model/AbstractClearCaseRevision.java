package hudson.plugins.clearcase.model;

import hudson.scm.SCMRevisionState;

import java.util.Date;

public abstract class AbstractClearCaseRevision extends SCMRevisionState {

    protected final Date buildTime;
    private String[] loadRules;

    public AbstractClearCaseRevision(Date buildTime) {
        super();
        this.buildTime = buildTime;
    }

    public Date getBuildTime() {
        return buildTime;
    }

    public String[] getLoadRules() {
        return loadRules;
    }

    public void setLoadRules(String[] loadRules) {
        this.loadRules = loadRules;
    }

}