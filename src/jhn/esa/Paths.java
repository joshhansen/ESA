package jhn.esa;

public final class Paths {
	private static final String FEATURE_SET_EXT = ".feature_set";
	private Paths() {}
	
	public static String outputDir() {
		return jhn.Paths.outputDir("ESA");
	}
	
	public static String featselDir() {
		return outputDir() + "/featsel";
	}
	
	public static String featselFilename(String topicWordIdxName, String datasetName, int topN) {
		return featselDir() + "/" + topicWordIdxName + ":" + datasetName + "_top" + topN + FEATURE_SET_EXT;
	}
	
	public static String libSvmDir() {
		return outputDir() + "/libsvm";
	}
	
	private static String key(String topicWordIdxName, String datasetName, int topN) {
		return topicWordIdxName + ":" + datasetName + "_top" + topN;
	}
	
	public static String dimensionReducedDocsFilename(String topicWordIdxName, String datasetName, int topN) {
		return libSvmDir() + "/" + key(topicWordIdxName, datasetName, topN) + jhn.Paths.LIBSVM_EXT;
	}
	
	public static String runsDir() {
		return outputDir() + "/runs";
	}
}
