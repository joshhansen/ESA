package jhn.esa;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Iterator;

import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelAlphabet;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import jhn.eda.topiccounts.TopicCounts;
import jhn.eda.typetopiccounts.TypeTopicCount;
import jhn.eda.typetopiccounts.TypeTopicCounts;
import jhn.util.Config;
import jhn.util.Log;

public class ESA {
	
	private final Log log;
	
	private Alphabet wordTypes;
	
	private String[] docNames;
	
	private int[][] documentTerms;
	
	private double[][] trainingDataTfidfs;
	
	private final LabelAlphabet conceptLabels;
	
	private final TopicCounts topicCounts;
	
	private final TypeTopicCounts typeTopicCounts;
	
	private final Config conf = new Config();
	
	public ESA(LabelAlphabet conceptLabels, TopicCounts topicCounts, TypeTopicCounts typeTopicCounts, String logFilename) throws FileNotFoundException {
		this.conceptLabels = conceptLabels;
		this.topicCounts = topicCounts;
		this.typeTopicCounts = typeTopicCounts;
		this.log = new Log(System.out, logFilename);
		
		conf.putInt(Options.NUM_CONCEPTS, conceptLabels.size());
		conf.putInt(Options.PRINT_NUM_TOP_CONCEPTS, 10);
	}
	
	public void setTrainingData(InstanceList trainingData) {
		wordTypes = trainingData.getAlphabet();
		
		final int numDocs = trainingData.size();
		final int numTypes = wordTypes.size();
		
		// Count term frequencies and document frequencies. Also store which terms are in each document.
		log.print("Counting frequencies...");
		int[] df = new int[numTypes];
		int[][] tf = new int[numDocs][];
		documentTerms = new int[numDocs][];
		docNames = new String[numDocs];
		
		for(int docNum = 0; docNum < tf.length; docNum++) {
			Instance doc = trainingData.get(docNum);
			docNames[docNum] = doc.getName().toString();
			
			FeatureSequence feats = (FeatureSequence) doc.getData();
			
			tf[docNum] = new int[numTypes];
			IntSet typesInDoc = new IntOpenHashSet();
			for(int typeIdx : feats.getFeatures()) {
				tf[docNum][typeIdx]++;
				typesInDoc.add(typeIdx);
			}
			
			int[] docTerms = typesInDoc.toIntArray();
			Arrays.sort(docTerms);
			
			documentTerms[docNum] = docTerms;
			
			// Increment document frequencies
			for(int type : typesInDoc) {
				df[type]++;
			}
		}
		log.println("done.");
		
		// Compute tf-idfs
		log.print("Computing tf-idf...");
		trainingDataTfidfs = new double[numDocs][];
		final double logNumDocs = Math.log(numDocs);
		for(int docNum = 0; docNum < tf.length; docNum++) {
			trainingDataTfidfs[docNum] = new double[numTypes];
			for(int typeIdx = 0; typeIdx < trainingDataTfidfs[docNum].length; typeIdx++) {
				double idf = logNumDocs - Math.log(df[typeIdx]);
				trainingDataTfidfs[docNum][typeIdx] = tf[docNum][typeIdx] * idf;
			}
		}
		log.println("done.");
	}
	
	public final class ConceptWeight implements Comparable<ConceptWeight> {
		public final int conceptNum;
		public final double weight;
		private ConceptWeight(int conceptNum, double weight) {
			this.conceptNum = conceptNum;
			this.weight = weight;
		}
		
		@Override
		public int compareTo(ConceptWeight o) {
			return Double.compare(o.weight, this.weight);
		}
		
		@Override
		public String toString() {
			StringBuilder s = new StringBuilder();
			
			s.append('#');
			s.append(conceptNum);
			s.append(' ');
			s.append(conceptLabels.lookupObject(conceptNum));
			
			s.append(": ");
			s.append(weight);
			
			return s.toString();
		}
	}
	
	public double[] semanticInterpretationVector(int docNum) throws Exception {
		double[] semInterpVect = new double[conf.getInt(Options.NUM_CONCEPTS)];
		
		for(int typeIdx : documentTerms[docNum]) {
			double termWeight = trainingDataTfidfs[docNum][typeIdx];
			
			Iterator<TypeTopicCount> ttcs = typeTopicCounts.typeTopicCounts(typeIdx);
			while(ttcs.hasNext()) {
				TypeTopicCount ttc = ttcs.next();
				double conceptTermWeight = (double) ttc.count / (double) topicCounts.topicCount(ttc.topic);
				semInterpVect[ttc.topic] += termWeight * conceptTermWeight;
			}
		}
		
		return semInterpVect;
	}
	
	public ConceptWeight[] sortedSemanticInterpretationVector(int docNum) throws Exception {
		double[] semInterpVect = semanticInterpretationVector(docNum);
		
		ConceptWeight[] sorted = new ConceptWeight[semInterpVect.length];
		for(int i = 0; i < sorted.length; i++) {
			sorted[i] = new ConceptWeight(i, semInterpVect[i]);
		}
		Arrays.sort(sorted);
		return sorted;
	}
	
	
	public void printSemInterpVect(int docNum) throws Exception {
		log.print("Document ");
		log.print(docNum);
		log.print(": ");
		log.println(docNames[docNum]);
		
		ConceptWeight[] concepts = sortedSemanticInterpretationVector(docNum);
		for(int i = 0; i < Math.min(concepts.length, conf.getInt(Options.PRINT_NUM_TOP_CONCEPTS)); i++) {
			log.println(concepts[i]);
		}
		log.println();
	}
	
	public void printSemInterpVectors() throws Exception {
		log.println("Computing semantic interpretation vectors using configuration:");
		log.println(conf);
		for(int docNum = 0; docNum < documentTerms.length; docNum++) {
			printSemInterpVect(docNum);
		}
	}
}
