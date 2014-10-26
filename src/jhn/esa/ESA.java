package jhn.esa;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;

import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import jhn.counts.d.i.IntDoubleCounter;
import jhn.counts.d.i.IntDoubleRAMCounter;
import jhn.counts.i.i.IntIntCounter;
import jhn.counts.i.i.IntIntRAMCounter;
import jhn.eda.topiccounts.TopicCounts;
import jhn.eda.typetopiccounts.TopicCount;
import jhn.eda.typetopiccounts.TypeTopicCounts;
import jhn.idx.Index;
import jhn.idx.IntIndex;
import jhn.idx.RAMIndex;
import jhn.idx.ReverseIndex;
import jhn.util.Config;
import jhn.util.Log;
import jhn.util.Util;

public class ESA implements AutoCloseable {
	private final Log log;
	
	protected int numDocs;
	
	private Alphabet wordTypes;
	
	private String[] docNames;
	
	private int[][] documentTerms;
	
	private int df[];
	private IntIntCounter[] tf;
	private double logNumDocs;
	
	private final TopicCounts topicCounts;
	
	private final TypeTopicCounts typeTopicCounts;
	
	public final Config conf = new Config();
	
	// Classification helpers
	protected String[] docLabels;
	protected Index<String> allLabels;
	
	public ESA(TopicCounts topicCounts, TypeTopicCounts typeTopicCounts, String logDir) throws FileNotFoundException {
		this.topicCounts = topicCounts;
		this.typeTopicCounts = typeTopicCounts;
		new File(logDir).mkdirs();
		this.log = new Log(System.out, logDir + "/main.log");

		conf.putInt(Options.PRINT_NUM_TOP_DOC_CONCEPTS, 10);
		conf.putInt(Options.PRINT_NUM_TOP_OVERALL_CONCEPTS, 100);
	}
	
	public void setTrainingData(InstanceList trainingData) {
		wordTypes = trainingData.getAlphabet();
		
		numDocs = trainingData.size();
		logNumDocs = Math.log(numDocs);
		
		final int numTypes = wordTypes.size();
		
		// Count term frequencies and document frequencies. Also store which terms are in each document.
		log.print("Counting frequencies...");
		df = new int[numTypes];
		tf = new IntIntCounter[numDocs];
		
		documentTerms = new int[numDocs][];
		docNames = new String[numDocs];
		
		docLabels = new String[numDocs];
		SortedSet<String> labels = new TreeSet<>();
		String label;
		
		for(int docNum = 0; docNum < tf.length; docNum++) {
			Instance doc = trainingData.get(docNum);
			docNames[docNum] = doc.getName().toString();
			
			FeatureSequence feats = (FeatureSequence) doc.getData();
			
			tf[docNum] = new IntIntRAMCounter(new Int2IntArrayMap());
			IntSet typesInDoc = new IntOpenHashSet();
			for(int typeIdx : feats.getFeatures()) {
				tf[docNum].inc(typeIdx);
				typesInDoc.add(typeIdx);
			}
			
			label = doc.getTarget().toString();
			docLabels[docNum] = label;
			labels.add(label);
			
			int[] docTerms = typesInDoc.toIntArray();
			Arrays.sort(docTerms);
			
			documentTerms[docNum] = docTerms;
			
			// Increment document frequencies
			for(int type : typesInDoc) {
				df[type]++;
			}
		}
		log.println("done.");
		
		allLabels = new RAMIndex<>();
		allLabels.indexOf("none");//Needed for use in SparseInstance
		for(String theLabel : labels) {
			allLabels.indexOf(theLabel);
		}
	}
	
	private double tfidf(int docNum, int typeIdx) {
		double idf = logNumDocs - Math.log(df[typeIdx]);
		return tf[docNum].getCount(typeIdx) * idf;
	}
	
	public int numDocs() {
		return numDocs;
	}
	
	public IntDoubleCounter semanticInterpretationVector(int docNum) throws Exception {
		return semanticInterpretationVector(docNum, true);
	}
	
	public IntDoubleCounter semanticInterpretationVector(int docNum, boolean trim) throws Exception {
		double termWeight, topicCount, conceptTermWeight;
		TopicCount ttc;
		Iterator<TopicCount> ttcs;
		IntDoubleRAMCounter semInterp = new IntDoubleRAMCounter();
		
		for(int typeIdx : documentTerms[docNum]) {
			termWeight = tfidf(docNum, typeIdx);
			
			ttcs = typeTopicCounts.typeTopicCounts(typeIdx);
			while(ttcs.hasNext()) {
				ttc = ttcs.next();
				topicCount = topicCounts.topicCount(ttc.topic);
				if(topicCount > 0.0) {
					conceptTermWeight = ttc.count / topicCount;
					semInterp.inc(ttc.topic, termWeight * conceptTermWeight);
				}
			}
		}
		
		if(trim) {
			semInterp.trim();
		}
		
		return semInterp;
	}
	
	public IntDoubleCounter semanticInterpretationVector(int docNum, IntIndex features) throws Exception {
		int featureIdx;
		double termWeight, topicCount, conceptTermWeight;
		TopicCount ttc;
		Iterator<TopicCount> ttcs;
		IntDoubleRAMCounter semInterp = new IntDoubleRAMCounter();
		for(int typeIdx : documentTerms[docNum]) {
			termWeight = tfidf(docNum, typeIdx);
			
			ttcs = typeTopicCounts.typeTopicCounts(typeIdx);
			while(ttcs.hasNext()) {
				ttc = ttcs.next();
				featureIdx = features.indexOfI(ttc.topic, false);
				if(featureIdx != ReverseIndex.KEY_NOT_FOUND) {
					topicCount = topicCounts.topicCount(ttc.topic);
					if(topicCount > 0.0) {
						conceptTermWeight = ttc.count / topicCount;
						semInterp.inc(featureIdx, termWeight * conceptTermWeight);
					}
				}
			}
		}
		
		semInterp.trim();
		
		return semInterp;
	}
	
	public static final Comparator<Int2DoubleMap.Entry> fastKeyCmp = new Comparator<Int2DoubleMap.Entry>(){
		@Override
		public int compare(Int2DoubleMap.Entry o1, Int2DoubleMap.Entry o2) {
			return Util.compareInts(o1.getIntKey(), o2.getIntKey());
		}
	};
	
	public int docLabelIdx(int docNum) {
		return allLabels.indexOf(docLabels[docNum]);
	}

	@Override
	public void close() throws Exception {
		log.close();
	}
	
}
