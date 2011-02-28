package hudson.plugins.clearcase.history.model;

/**
 * The change set level describes which level of details will be in the changeset
 */
public enum ChangeSetLevel {
	/**
	 * No changeset will be generated
	 */
	NONE("no"),
	/**
	 * Changeset will be generated based only on changes done in current branch
	 */
	BRANCH("branch"),
	/**
	 * Changeset will be generated based on changes done in current branch, and changes due to rebase
	 */
	ALL("all");
	
	private String name;
	private ChangeSetLevel(String name) {
		this.name = name;
	}
	public static ChangeSetLevel fromString(String str) {
		for (ChangeSetLevel csl : values()) {
			if (csl.name.equals(str)) {
				return csl;
			}
		}
		return ChangeSetLevel.defaultLevel();
	}
	
	public String getName() {
	    return name;
	} 
	public static ChangeSetLevel defaultLevel() {
		return ChangeSetLevel.BRANCH;
	}
}