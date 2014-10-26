package jhn.esa;

import it.unimi.dsi.fastutil.ints.Int2DoubleMap;

import jhn.ExtractorParams;
import jhn.counts.d.i.IntDoubleCounter;
import jhn.idx.IntIndex;
import jhn.idx.IntRAMIndex;
import jhn.util.Util;

/**
 * Select features as the union across documents of the top N topics as determined by ESA's semantic interpretation
 * vector.
 * 
 * @author Josh Hansen
 *
 */
public class SelectFeatures {
	public static void selectFeatures(ESA esa, String outputFilename, int topN) throws Exception {
		System.out.print("Selecting features...");
		IntIndex topics = new IntRAMIndex();
		
		IntDoubleCounter semInterpVector;
		for(int docNum = 0; docNum < esa.numDocs(); docNum++) {
			semInterpVector = esa.semanticInterpretationVector(docNum, false);
			
			for(Int2DoubleMap.Entry entry : semInterpVector.fastTopN(topN)) {
				topics.indexOfI(entry.getIntKey());
			}
			
			System.out.print('.');
			if(docNum % 120 == 0) {
				System.out.println(docNum);
			}
		}
		System.out.println("done.");
		
		System.out.println("Selected " + topics.size() + " features.");
		
		System.out.print("Serializing selected features...");
		Util.serialize(topics, outputFilename);
		System.out.println("done.");
	}
	
	public static void main(String[] args) throws Exception {
		ExtractorParams ep = new ExtractorParams()
			.topicWordIdxName("wp_lucene4")
			.datasetName("reuters21578_noblah2")// toy_dataset2, debates2012, sacred_texts, state_of_the_union reuters21578
			.minCount(2);
		
		final int topN = 1;
		
		ESA esa = RunESA.loadESA(ep);
		String featselFilename = Paths.featselFilename(ep.topicWordIdxName, ep.datasetName, topN);
		selectFeatures(esa, featselFilename, topN);
	}
}
