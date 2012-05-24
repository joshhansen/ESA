package jhn.esa;

import java.io.File;

import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelAlphabet;

import jhn.eda.Paths;
import jhn.eda.topiccounts.TopicCounts;
import jhn.eda.typetopiccounts.TypeTopicCounts;
import jhn.util.Util;

public final class RunESA {
	private RunESA() {}
	
	private static int nextLogNum(String logDir) {
		int max = -1;
		for(File f : new File(logDir).listFiles()) {
			final String fname = f.getName();
			
			if(fname.endsWith(".txt")) {
				String[] parts = fname.split("\\.");
				
				int value = Integer.parseInt(parts[0]);
				if(value > max) {
					max = value;
				}
			}
		}
		return max + 1;
	}
	
	private static String logFilename() {
		final String logDir = Paths.runsDir();
		String filename = logDir + "/" + String.valueOf(nextLogNum(logDir)) + ".txt";
		System.out.println("Writing to log file: " + filename);
		return filename;
	}
	
	public static void main (String[] args) throws Exception {
		final String topicWordIdxName = "wp_lucene4";
		final String datasetName = "debates2012";// toy_dataset2, debates2012, state_of_the_union
		final int minCount = 2;
		
        System.out.print("Loading target corpus...");
        InstanceList targetData = InstanceList.load(new File(jhn.eda.Paths.datasetFilename(datasetName)));
        System.out.println("done.");

		System.out.print("Loading type-topic counts...");
		String ttCountsFilename = jhn.eda.Paths.typeTopicCountsFilename(topicWordIdxName, datasetName, minCount);
		TypeTopicCounts ttcs = (TypeTopicCounts) Util.deserialize(ttCountsFilename);
		System.out.println("done.");
		
		System.out.print("Loading topic counts...");
		final String topicCountsFilename = Paths.topicCountsFilename(topicWordIdxName, datasetName, minCount);
		TopicCounts tcs = (TopicCounts) Util.deserialize(topicCountsFilename);
		System.out.println("done.");

		System.out.print("Loading label alphabet...");
		String labelAlphabetFilename = jhn.eda.Paths.labelAlphabetFilename(topicWordIdxName, datasetName, minCount);
		LabelAlphabet topicAlphabet = (LabelAlphabet) Util.deserialize(labelAlphabetFilename);
		System.out.println("done.");
		
		ESA esa = new ESA(topicAlphabet, tcs, ttcs, logFilename());
		
		System.out.print("Loading training data...");
		esa.setTrainingData(targetData);
		System.out.println("done.");

		esa.printSemInterpVectors();
	}//end main
}

