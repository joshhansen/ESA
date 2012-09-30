package jhn.esa;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Arrays;

import it.unimi.dsi.fastutil.ints.Int2DoubleMap;

import jhn.counts.d.i.IntDoubleCounter;
import jhn.idx.IntIndex;
import jhn.util.Util;

public class ReduceDimensionality {
//	logDir + "/reduced.libsvm"
	
	public static void reduceDimensionality(ESA esa, IntIndex features, String outputFilename) throws Exception {
		try(PrintStream out = new PrintStream(new FileOutputStream(outputFilename))) {
			reduceDimensionality(esa, features, out);
		}
	}
	
	private static void reduceDimensionality(ESA esa, IntIndex features, PrintStream out) throws Exception {
		int classNum;
		IntDoubleCounter semInterpVector;
		for(int docNum = 0; docNum < esa.numDocs(); docNum++) {
			semInterpVector = esa.semanticInterpretationVector(docNum, features);
			
			classNum = esa.docLabelIdx(docNum);
			
			out.print(classNum);
			
			Int2DoubleMap.Entry[] entries = semInterpVector.int2DoubleEntrySet().toArray(new Int2DoubleMap.Entry[0]);
			Arrays.sort(entries, ESA.fastKeyCmp);
			
			for(Int2DoubleMap.Entry entry : entries) {
				out.print(' ');
				out.print(entry.getIntKey());
				out.print(':');
				out.print(entry.getDoubleValue());
			}
			out.println();
			
			System.out.print('.');
			if(docNum > 0 && docNum % 120 == 0) {
				System.out.println(docNum);
			}
		}
	}
	
	public static void main (String[] args) throws Exception {
		final String topicWordIdxName = "wp_lucene4";
		final String datasetName = "reuters21578";// toy_dataset2, debates2012, sacred_texts, state_of_the_union reuters21578
		final int minCount = 2;
		final int topN = 1;
		
		ESA esa = RunESA.loadESA(topicWordIdxName, datasetName, minCount);
		
		System.out.print("Deserializing selected features...");
		String featselFilename = Paths.featselFilename(topicWordIdxName, datasetName, topN);
		IntIndex features = (IntIndex) Util.deserialize(featselFilename);
		System.out.println("done.");
		
		String dimReducedFilename = Paths.dimensionReducedDocsFilename(topicWordIdxName, datasetName, topN);
		reduceDimensionality(esa, features, dimReducedFilename);
	}//end main
}
