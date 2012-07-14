package jhn.esa;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
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

import jhn.counts.doubles.IntDoubleCounter;
import jhn.counts.doubles.IntDoubleRAMCounter;
import jhn.counts.ints.IntIntCounter;
import jhn.counts.ints.IntIntRAMCounter;
import jhn.eda.topiccounts.TopicCounts;
import jhn.eda.typetopiccounts.TypeTopicCount;
import jhn.eda.typetopiccounts.TypeTopicCounts;
import jhn.idx.Index;
import jhn.idx.IntIndex;
import jhn.idx.IntRAMIndex;
import jhn.idx.RAMIndex;
import jhn.idx.ReverseIndex;
import jhn.util.Config;
import jhn.util.Log;
import jhn.util.Util;

public class ESA {
	private final String logDir;
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
		this.logDir = logDir;
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
	
	private IntDoubleCounter semInterp(int docNum) throws Exception {
		IntDoubleRAMCounter semInterp = new IntDoubleRAMCounter();
		
		for(int typeIdx : documentTerms[docNum]) {
			double termWeight = tfidf(docNum, typeIdx);
			
			Iterator<TypeTopicCount> ttcs = typeTopicCounts.typeTopicCounts(typeIdx);
			while(ttcs.hasNext()) {
				TypeTopicCount ttc = ttcs.next();
				final double topicCount = topicCounts.topicCount(ttc.topic);
				if(topicCount > 0.0) {
					double conceptTermWeight = ttc.count / topicCount;
					semInterp.inc(ttc.topic, termWeight * conceptTermWeight);
				}
			}
		}
		
		semInterp.trim();
		
		return semInterp;
	}
	
	private IntDoubleCounter semInterp(int docNum, IntIndex features) throws Exception {
		IntDoubleRAMCounter semInterp = new IntDoubleRAMCounter();
		int featureIdx;
		for(int typeIdx : documentTerms[docNum]) {
			double termWeight = tfidf(docNum, typeIdx);
			
			Iterator<TypeTopicCount> ttcs = typeTopicCounts.typeTopicCounts(typeIdx);
			while(ttcs.hasNext()) {
				TypeTopicCount ttc = ttcs.next();
				featureIdx = features.indexOfI(ttc.topic, false);
				if(featureIdx != ReverseIndex.KEY_NOT_FOUND) {
					final double topicCount = topicCounts.topicCount(ttc.topic);
					if(topicCount > 0.0) {
						double conceptTermWeight = ttc.count / topicCount;
						semInterp.inc(featureIdx, termWeight * conceptTermWeight);
					}
				}
			}
		}
		
		semInterp.trim();
		
		return semInterp;
	}
	
	public void selectFeatures(String outputFilename, int topN) throws Exception {
		log.print("Selecting features...");
		IntIndex topics = new IntRAMIndex();
		
		IntDoubleCounter semInterpVector;
		for(int docNum = 0; docNum < numDocs; docNum++) {
			semInterpVector = semInterp(docNum);
			
			for(Int2DoubleMap.Entry entry : semInterpVector.fastTopN(topN)) {
				topics.indexOfI(entry.getIntKey());
			}
			
			log.print('.');
			if(docNum % 120 == 0) {
				log.println(docNum);
			}
		}
		log.println("done.");
		
		log.println("Selected " + topics.size() + " features.");
		
		log.print("Serializing selected features...");
		Util.serialize(topics, outputFilename);
		log.println("done.");
	}
	
	public static final Comparator<Int2DoubleMap.Entry> fastKeyCmp = new Comparator<Int2DoubleMap.Entry>(){
		@Override
		public int compare(Int2DoubleMap.Entry o1, Int2DoubleMap.Entry o2) {
			return Util.compareInts(o1.getIntKey(), o2.getIntKey());
		}
	};
	
	public void printReducedDocsLibSvm(IntIndex features) throws Exception {
		PrintStream out = new PrintStream(new FileOutputStream(logDir + "/reduced.libsvm"));
		printReducedDocsLibSvm(features, out);
		out.close();
	}
	
	private void printReducedDocsLibSvm(IntIndex features, PrintStream out) throws Exception {
		int classNum;
		IntDoubleCounter semInterpVector;
		for(int docNum = 0; docNum < numDocs; docNum++) {
			semInterpVector = semInterp(docNum, features);
			
			classNum = allLabels.indexOf(docLabels[docNum]);
			
			out.print(classNum);
			
			Int2DoubleMap.Entry[] entries = semInterpVector.int2DoubleEntrySet().toArray(new Int2DoubleMap.Entry[0]);
			Arrays.sort(entries, fastKeyCmp);
			
			for(Int2DoubleMap.Entry entry : entries) {
				out.print(' ');
				out.print(entry.getIntKey());
				out.print(':');
				out.print(entry.getDoubleValue());
			}
			out.println();
			
			log.print('.');
			if(docNum > 0 && docNum % 120 == 0) {
				log.println(docNum);
			}
		}
	}
	
}
