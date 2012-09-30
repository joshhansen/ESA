package jhn.esa;

public final class Paths {
	private Paths() {}
	
	public static String outputDir() {
		return jhn.Paths.outputDir("ESA");
	}
	
	public static String featselDir() {
		return outputDir() + "/featsel";
	}
	
	public static String featselFilename(String topicWordIdxName, String datasetName, int topN) {
		return featselDir() + "/" + extractedDataID(topicWordIdxName, datasetName, topN) + ".feature_set";
	}
	
	public static String dimReductionDir() {
		return outputDir() + "/dimreduction";
	}
	
	public static String dimensionReducedDocsFilename(String topicWordIdxName, String datasetName, int topN) {
		return dimReductionDir() + extractedDataID(topicWordIdxName, datasetName, topN) + ".doc_features.libsvm";
	}
	
	public static String runsDir() {
		return outputDir() + "/runs";
	}
	
	public static String extractedDataID(String topicWordIdxName, String datasetName, int minCount) {
		return topicWordIdxName + ":" + datasetName + "_min" + minCount;
	}
}
