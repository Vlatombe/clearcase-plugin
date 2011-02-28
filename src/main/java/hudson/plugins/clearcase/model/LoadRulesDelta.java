package hudson.plugins.clearcase.model;

import java.util.Set;

public class LoadRulesDelta {
    private final Set<String> removed;
    private final Set<String> added;
    public LoadRulesDelta(Set<String> removed, Set<String> added) {
        super();
        this.removed = removed;
        this.added = added;
    }
    public String[] getAdded() {
        return added.toArray(new String[added.size()]);
    }
    public String[] getRemoved() {
        return removed.toArray(new String[removed.size()]);
    }
    public boolean isEmpty() {
        return added.isEmpty() && removed.isEmpty();
    }
}