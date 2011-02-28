package hudson.plugins.clearcase;

import java.io.InputStream;

public final class TestFile {
		
	public static InputStream getResourceAsStream(String filename) {
		return TestFile.class.getResourceAsStream(filename);
	}
}
