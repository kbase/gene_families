package us.kbase.kbasegenefamilies.prepare;

/**
   This class no longer compiles with the new typespec, so everything
   has been commented out as of 1/14/15 --JMC

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import us.kbase.auth.AuthException;
import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.auth.TokenFormatException;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.Tuple2;
import us.kbase.common.service.Tuple4;
import us.kbase.common.service.Tuple5;
import us.kbase.common.service.UObject;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.kbasegenefamilies.DomainAlignments;
import us.kbase.kbasegenefamilies.DomainAnnotation;
import us.kbase.kbasegenefamilies.DomainCluster;
import us.kbase.kbasegenefamilies.DomainClusterSearchResult;
import us.kbase.kbasegenefamilies.DomainClusterStat;
import us.kbase.kbasegenefamilies.DomainModelSet;
import us.kbase.kbasegenefamilies.GenomeStat;
import us.kbase.kbasegenefamilies.util.Utils;
import us.kbase.kbasetrees.MSA;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.SubObjectIdentity;
import us.kbase.workspace.WorkspaceClient;

public class DomainClusterPreparation {
    private static final String wsUrl = "http://dev04.berkeley.kbase.us:7058";  // "https://kbase.us/services/ws/";
    private static final String genomeWsName = "KBasePublicGenomesLoad";
    private static final String genomeWsType = "KBaseGenomes.Genome";
    private static final String domainWsName = "KBasePublicGeneDomains";
    //private static final String defaultDomainSetObjectName = "BacterialProteinDomains.set";
    private static final String cogPfamDomainSetObjectName = "CogAndPfamDomains.set";
    private static final String domainModelWsType = "KBaseGeneFamilies.DomainModel";
    private static final String domainAnnotationWsType = "KBaseGeneFamilies.DomainAnnotation";
    private static final String domainAlignmentsWsType = "KBaseGeneFamilies.DomainAlignments";
    private static final String domainClusterWsType = "KBaseGeneFamilies.DomainCluster";
    private static final String msaWsType = "KBaseTrees.MSA";
    private static final String domainClusterSearchResultType = "KBaseGeneFamilies.DomainClusterSearchResult";
    private static final String defaultDCSRObjectName = "CogAndPfam.dcsr";

    public static void main(String[] args) throws Exception {
	Properties props = props(new File("config.cfg"));
	GenomeAnnotationChache.cacheDomainAnnotation(props);
	File tempDir = new File(get(props, "temp.dir"));
	File annotDir = new File(tempDir, "annotation");
	WorkspaceClient client = client(props);
	Set<String> domainRefs = getRestrictedDomainRefs(client);
	System.out.println("Domain count: " + domainRefs.size());
	Map<String, String> annotNameToRefMap = new TreeMap<String, String>();
	for (Tuple2<String, String> refAndName : Utils.listAllObjectsRefAndName(client, domainWsName, domainAnnotationWsType))
	    annotNameToRefMap.put(refAndName.getE2(), refAndName.getE1());
	Map<String, String> alignNameToRefMap = new TreeMap<String, String>();
	for (Tuple2<String, String> refAndName : Utils.listAllObjectsRefAndName(client, domainWsName, domainAlignmentsWsType))
	    alignNameToRefMap.put(refAndName.getE2(), refAndName.getE1());
	Map<String, String> domainModelRefToNameMap = new TreeMap<String, String>();
	for (Tuple2<String, String> refAndName : Utils.listAllObjectsRefAndName(client, domainWsName, domainModelWsType))
	    domainModelRefToNameMap.put(refAndName.getE1(), refAndName.getE2());
	File clusterDir = new File(tempDir, "clusters");
	if (!clusterDir.exists())
	    clusterDir.mkdir();
	Set<String> processedGenomeRefs = new HashSet<String>();
	File processedGenomesFile = new File(clusterDir, "processed_genomes.txt");
	if (processedGenomesFile.exists()) {
	    BufferedReader br = new BufferedReader(new FileReader(processedGenomesFile));
	    while (true) {
		String l = br.readLine();
		if (l == null)
		    break;
		if (l.isEmpty())
		    continue;
		processedGenomeRefs.add(l);
	    }
	    br.close();
	}
	//int genomeCount = 0;
	//long time1 = System.currentTimeMillis();
	Map<String, String> genomeRefToName = new TreeMap<String, String>();
	for (Tuple2<String, String> refAndName : Utils.listAllObjectsRefAndName(client, genomeWsName, genomeWsType))
	    genomeRefToName.put(refAndName.getE1(), refAndName.getE2());
	long lastFlushTime = System.currentTimeMillis();
	FileCache clusterCache = new FileCache();
	List<GenomeProcDescr> genomeCache = new ArrayList<GenomeProcDescr>();
	for (Map.Entry<String, String> refAndName : genomeRefToName.entrySet()) {
	    String genomeRef = refAndName.getKey();
	    if (processedGenomeRefs.contains(genomeRef)) {
		System.out.println("Genome was already processed: " + genomeRef);
		continue;
	    }
	    String genomeObjectName = refAndName.getValue();
	    String genomeAnnotationObjectName = genomeObjectName + ".domains";
	    String genomeAlignmentsObjectName = genomeObjectName + ".alignments";
	    String annotRef = annotNameToRefMap.get(genomeAnnotationObjectName);
	    String alignRef = alignNameToRefMap.get(genomeAlignmentsObjectName);
	    if (annotRef == null || alignRef == null)
		continue;
	    File f1 = new File(annotDir, "domains_" + annotRef.replace('/', '_') + ".json.gz");
	    File f2 = new File(annotDir, "alignments_" + alignRef.replace('/', '_') + ".json.gz");
	    if (f1.exists() && f2.exists()) {
		DomainAnnotation annot;
		try {
		    annot = readJsonFromGZip(f1, DomainAnnotation.class);
		} catch (Exception ex) {
		    System.err.println("Error loading file " + f1 + ": " + ex.getMessage());
		    continue;
		}
		DomainAlignments align;
		try {
		    align = readJsonFromGZip(f2, DomainAlignments.class);
		} catch (Exception ex) {
		    System.err.println("Error loading file " + f2 + ": " + ex.getMessage());
		    continue;
		}
		if (!annot.getGenomeRef().equals(genomeRef))
		    throw new IllegalStateException();
		if (!align.getGenomeRef().equals(genomeRef))
		    throw new IllegalStateException();
		long time2 = System.currentTimeMillis();
		Map<String, String> genome = null;
		for (int i = 0; i < 5; i++)
		    try {
			genome = client.getObjectSubset(Arrays.asList(new SubObjectIdentity().withRef(genomeRef)
								      .withIncluded(Arrays.asList("scientific_name", "domain")))).get(0).getData().asInstance();
		    } catch (Exception e) {
			System.err.println(genomeRef + " (" + i + "): " + e.getMessage());
		    }
		if (genome == null)
		    continue;
		String name = genome.get("scientific_name");
		String domain = genome.get("domain");
		if (domain == null || !(domain.equals("Bacteria") || domain.equals("Archaea"))) {
		    time2 = System.currentTimeMillis() - time2;
		    putGenomeIntoProcessedList(processedGenomesFile, genomeRef);
		    System.out.println("Genome [" + genomeObjectName + "] (" + name + "), is skipped cause domain=[" + domain + "], time=" + time2);
		    continue;
		}
		int features = 0;
		int domains = 0;
		for (Map.Entry<String, List<Tuple5<String, Long, Long, Long, Map<String, List<Tuple5<Long, Long, Double, Double, Double>>>>>> contigElements : annot.getData().entrySet()) {
		    String contigId = contigElements.getKey();
		    for (Tuple5<String, Long, Long, Long, Map<String, List<Tuple5<Long, Long, Double, Double, Double>>>> element : contigElements.getValue()) {
			String featureId = element.getE1();
			if (annot.getFeatureToContigAndIndex().get(featureId) == null) {
			    if (element.getE5().isEmpty())
				continue;
			    System.err.println("No feature index for [" + featureId + "] but domain map has size=" + element.getE5().size());
			    continue;
			}
			long featureIndex = annot.getFeatureToContigAndIndex().get(featureId).getE2();
			features++;
			for (Map.Entry<String, List<Tuple5<Long, Long, Double, Double, Double>>> domainPlaces : element.getE5().entrySet()) {
			    String domainRef = domainPlaces.getKey();
			    if (!domainRefs.contains(domainRef))
				continue;
			    File clusterFile = new File(clusterDir, "cluster_" + domainRef.replace('/', '_') + ".txt");
			    for (Tuple5<Long, Long, Double, Double, Double> domainPlace : domainPlaces.getValue()) {
				String alignedSeq = align.getAlignments().get(domainRef).get(featureId).get("" + domainPlace.getE1());
				if (alignedSeq == null)
				    throw new IllegalStateException("Can't find ailgnment for feature [" + featureId + "] in file: " + f2);
				clusterCache.addLine(clusterFile, "[*]\t" + genomeRef + "\t" + contigId + "\t" + featureId + "\t" + 
						     featureIndex + "\t" + domainPlace.getE1() + "\t" + domainPlace.getE2() + "\t" + 
						     domainPlace.getE3() + "\t" + domainPlace.getE4() + "\t" + domainPlace.getE5() + "\t" + alignedSeq);
				domains++;
			    }
			}
		    }
		}
		genomeCache.add(new GenomeProcDescr(genomeRef, genomeObjectName, features, domains));
		if (genomeCache.size() >= 50)
		    lastFlushTime = flushCaches(processedGenomesFile, genomeCache, clusterCache, lastFlushTime);
	    }
	}
	flushCaches(processedGenomesFile, genomeCache, clusterCache, lastFlushTime);
	if (args.length < 1 || !args[0].equals("true"))
	    return;
	System.out.println("==== Saving domain clusters ====");
	long time = System.currentTimeMillis();
	int clusterCount = 0;
	Map<String, String> domainClusterNameToRef = new TreeMap<String, String>();
	for (Tuple2<String, String> refAndName : Utils.listAllObjectsRefAndName(client, domainWsName, domainClusterWsType))
	    domainClusterNameToRef.put(refAndName.getE2(), refAndName.getE1());
	for (String domainRef : domainRefs) {
	    File clusterFile = new File(clusterDir, "cluster_" + domainRef.replace('/', '_') + ".txt");
	    if (!clusterFile.exists())
		continue;
	    String domainModelName = domainModelRefToNameMap.get(domainRef);
	    if (domainModelName == null)
		throw new IllegalStateException("No domain model name for reference: " + domainRef);
	    String domainClusterName = domainModelName + ".cluster";
	    if (domainClusterNameToRef.containsKey(domainClusterName)) {
		System.out.println("Cluster and msa " + domainClusterName + " were already saved");
		continue;
	    }
	    System.out.println("Saving cluster and msa for domain model: " + domainModelName + "(" + domainRef + ")");
	    Set<String> genomeRefAndFeatureIdAndStart = new HashSet<String>();
	    Map<String, Tuple4<String, String, Long, List<Tuple5<Long, Long, Double, Double, Double>>>> genomeRefAndFeatureIdToElement = 
		new HashMap<String, Tuple4<String, String, Long, List<Tuple5<Long, Long, Double, Double, Double>>>>();
	    DomainCluster cluster = new DomainCluster().withModel(domainRef).withData(new TreeMap<String, 
										      List<Tuple4<String, String, Long, List<Tuple5<Long, Long, Double, Double, Double>>>>>());
	    MSA msa = new MSA().withAlignment(new TreeMap<String, String>());
	    int alnLen = 0;
	    BufferedReader br = new BufferedReader(new FileReader(clusterFile));
	    try {
		while (true) {
		    String l = br.readLine();
		    if (l == null)
			break;
		    String[] parts = l.split("\t");
		    if (parts.length != 11) {
			if (l.lastIndexOf("[*]\t") <= 0)
			    throw new IllegalStateException("Wrong line format: \"" + l + "\"");
			l = l.substring(l.lastIndexOf("[*]\t"));
			parts = l.split("\t");
		    }
		    String genomeRef = parts[1];
		    String contigId = parts[2];
		    String featureId = parts[3]; 
		    long featureIndex = Long.parseLong(parts[4]);
		    Tuple5<Long, Long, Double, Double, Double> domainPlace = new Tuple5<Long, Long, Double, Double, Double>()
			.withE1(Long.parseLong(parts[5])).withE2(Long.parseLong(parts[6])).withE3(Double.parseDouble(parts[7]))
			.withE4(Double.parseDouble(parts[8])).withE5(Double.parseDouble(parts[9]));
		    String alignedSeq = parts[10];
		    String key = genomeRef + "_" + featureId + "_" + domainPlace.getE1();
		    if (genomeRefAndFeatureIdAndStart.contains(key))
			continue;
		    msa.getAlignment().put(key, alignedSeq);
		    alnLen = alignedSeq.length();
		    List<Tuple4<String, String, Long, List<Tuple5<Long, Long, Double, Double, Double>>>> elements = 
			cluster.getData().get(genomeRef);
		    if (elements == null) {
			elements = new ArrayList<Tuple4<String, String, Long, List<Tuple5<Long, Long, Double, Double, Double>>>>();
			cluster.getData().put(genomeRef, elements);
		    }
		    String key2 = genomeRef + "_" + featureId;
		    Tuple4<String, String, Long, List<Tuple5<Long, Long, Double, Double, Double>>> element = 
			genomeRefAndFeatureIdToElement.get(key2);
		    if (element == null) {
			element = new Tuple4<String, String, Long, List<Tuple5<Long, Long, Double, Double, Double>>>()
			    .withE1(contigId).withE2(featureId).withE3(featureIndex)
			    .withE4(new ArrayList<Tuple5<Long, Long, Double, Double, Double>>());
			elements.add(element);
			genomeRefAndFeatureIdToElement.put(key2, element);
		    }
		    element.getE4().add(domainPlace);
		}
	    } finally {
		br.close();
	    }
	    msa.setAlignmentLength((long)alnLen);
	    Exception error = null;
	    for (int i = 0; i < 5; i++) {
		try {
		    saveObject(client, domainWsName, msaWsType, domainModelName + ".msa", msa);
		    cluster.setMsaRef(domainWsName + "/" + domainModelName + ".msa");
		    saveObject(client, domainWsName, domainClusterWsType, domainClusterName, cluster);
		    error = null;
		    break;
		} catch (ServerException ex) {
		    System.err.println("Server error for cluster " + domainModelName + ":\n" + ex.getData());
		    error = ex; 
		}
	    }
	    if (error != null)
		throw error;
	    clusterCount++;
	    if (clusterCount % 100 == 0) {
		System.out.println("Info: " + clusterCount + " clusters were processed in " + (System.currentTimeMillis() - time) + 
				   " ms (average=" + ((System.currentTimeMillis() - time) / clusterCount) + ")");
	    }
	}
	System.out.println("==== Collecting genome list and domain stat from domain clusters ====");
	//Map<String, GenomeStat> genomeStat = loadGenomeStatCreatedBefore(client);
	//System.out.println("Genome stats: " + genomeStat.size());
	Map<String, String> genomeRefToAnnotRef = new LinkedHashMap<String, String>();
	time = System.currentTimeMillis();
	Map<String, DomainClusterStat> clusterStat = new TreeMap<String, DomainClusterStat>();
	for (String domainRef : domainRefs) {
	    Map<String, Map<String, Set<Integer>>> genomeRefToFeatureIdToStart = new TreeMap<String, Map<String, Set<Integer>>>();
	    DomainClusterStat stat = new DomainClusterStat() // .withDomainModelRef(domainRef)
		.withName(domainModelRefToNameMap.get(domainRef));
	    int features = 0;
	    int domains = 0;
	    File clusterFile = new File(clusterDir, "cluster_" + domainRef.replace('/', '_') + ".txt");
	    if (!clusterFile.exists())
		continue;
	    BufferedReader br = new BufferedReader(new FileReader(clusterFile));
	    try {
		while (true) {
		    String l = br.readLine();
		    if (l == null)
			break;
		    String[] parts = l.split("\t");
		    if (parts.length != 11) {
			if (l.lastIndexOf("[*]\t") <= 0)
			    throw new IllegalStateException("Wrong line format: \"" + l + "\"");
			l = l.substring(l.lastIndexOf("[*]\t"));
			parts = l.split("\t");
		    }
		    String genomeRef = parts[1];
		    if (!genomeRefToAnnotRef.containsKey(genomeRef)) {
			String genomeName = genomeRefToName.get(genomeRef);
			if (genomeName == null) {
			    System.err.println("Unknown genome reference: " + genomeRef);
			    continue;
			}
			String genomeAnnotationObjectName = genomeName + ".domains";
			String annotRef = annotNameToRefMap.get(genomeAnnotationObjectName);
			if (annotRef == null) {
			    System.err.println("Annotation is not found for genome: " + genomeName + "(" + genomeRef + ")");
			    continue;
			}
			genomeRefToAnnotRef.put(genomeRef, annotRef);
		    }
		    String featureId = parts[3]; 
		    int start = Integer.parseInt(parts[5]);
		    Map<String, Set<Integer>> startsInFeatures = genomeRefToFeatureIdToStart.get(genomeRef);
		    if (startsInFeatures == null) {
			startsInFeatures = new TreeMap<String, Set<Integer>>();
			genomeRefToFeatureIdToStart.put(genomeRef, startsInFeatures);
		    }
		    Set<Integer> startsInFeature = startsInFeatures.get(featureId);
		    if (startsInFeature == null) {
			startsInFeature = new TreeSet<Integer>();
			startsInFeatures.put(featureId, startsInFeature);
			features++;
		    }
		    if (!startsInFeature.contains(start)) {
			startsInFeature.add(start);
			domains++;
		    }
		}
	    } finally {
		br.close();
	    }
	    stat.setGenomes((long)genomeRefToFeatureIdToStart.size());
	    stat.setFeatures((long)features);
	    stat.setDomains((long)domains);
	    clusterStat.put(domainRef, stat);
	    System.out.println("Domain stat: g=" + genomeRefToFeatureIdToStart.size() + ", f=" + features + ", d=" + domains);
	    if (clusterStat.size() % 1000 == 0)
		System.out.println("\t" + clusterStat.size() + " domains were processed");
	}
	time = System.currentTimeMillis() - time;
	System.out.println("Time: " + time);
	System.out.println("==== Collecting genome stat from annotations ====");
	time = System.currentTimeMillis();
	Map<String, GenomeStat> genomeStat = collectGenomeStat(annotDir, client, domainRefs, genomeRefToAnnotRef);
	time = System.currentTimeMillis() - time;
	System.out.println("Time: " + time);
	domainClusterNameToRef = new TreeMap<String, String>();
	for (Tuple2<String, String> refAndName : Utils.listAllObjectsRefAndName(client, domainWsName, domainClusterWsType))
	    domainClusterNameToRef.put(refAndName.getE2(), refAndName.getE1());
	Map<String, String> msaNameToRef = new TreeMap<String, String>();
	for (Tuple2<String, String> refAndName : Utils.listAllObjectsRefAndName(client, domainWsName, msaWsType))
	    msaNameToRef.put(refAndName.getE2(), refAndName.getE1());
	Map<String, String> domainModelRefToClusterRef = new TreeMap<String, String>();
	Map<String, String> domainModelRefToMsaRef = new TreeMap<String, String>();
	for (String domainRef : domainRefs) {
	    File clusterFile = new File(clusterDir, "cluster_" + domainRef.replace('/', '_') + ".txt");
	    if (!clusterFile.exists())
		continue;
	    String domainModelName = domainModelRefToNameMap.get(domainRef);
	    if (domainModelName == null)
		throw new IllegalStateException("No domain model name for reference: " + domainRef);
	    String domainClusterName = domainModelName + ".cluster";
	    if (domainClusterNameToRef.containsKey(domainClusterName)) {
		String domainClusterRef = domainClusterNameToRef.get(domainClusterName);
		domainModelRefToClusterRef.put(domainRef, domainClusterRef);
	    } else {
		System.err.println("Can not find domain cluster for domain: " + domainModelName);
	    }
	    String msaObjName = domainModelName + ".msa";
	    String msaRef = msaNameToRef.get(msaObjName);
	    if (msaRef == null) {
		System.err.println("Can not find MSA for domain: " + domainModelName);
	    } else {
		domainModelRefToMsaRef.put(domainRef, msaRef);
	    }
	}
	DomainClusterSearchResult res = new DomainClusterSearchResult();
	res.setUsedDmsRef(domainWsName + "/" + cogPfamDomainSetObjectName);
	res.setAnnotationRefs(genomeRefToAnnotRef);
	res.setDomainClusterRefs(domainModelRefToClusterRef);
	res.setMsaRefs(domainModelRefToMsaRef);
	res.setGenomeStatistics(genomeStat);
	res.setDomainClusterStatistics(clusterStat);
	UObject.getMapper().writeValue(new File("dcsr.json"), res);
	saveObject(client, domainWsName, domainClusterSearchResultType, defaultDCSRObjectName, res);
	System.out.println("Domain cluster search result object [" + defaultDCSRObjectName + "] was stored into workspace " + domainWsName);
    }

    private static Map<String, GenomeStat> loadGenomeStatCreatedBefore(WorkspaceClient client) throws Exception {
	DomainClusterSearchResult dcsr = getObject(client, domainWsName + "/" + defaultDCSRObjectName, DomainClusterSearchResult.class);
	return dcsr.getGenomeStatistics();
    }
	
    private static Map<String, GenomeStat> collectGenomeStat(File annotDir,
							     WorkspaceClient client, Set<String> domainRefs,
							     Map<String, String> genomeRefToAnnotRef) throws IOException,
													     FileNotFoundException, JsonParseException, JsonMappingException,
													     Exception {
	Map<String, GenomeStat> genomeStat = new TreeMap<String, GenomeStat>();
	for (String genomeRef : genomeRefToAnnotRef.keySet()) {
	    String annotRef = genomeRefToAnnotRef.get(genomeRef);
	    File f1 = new File(annotDir, "domains_" + annotRef.replace('/', '_') + ".json.gz");
	    DomainAnnotation annot = readJsonFromGZip(f1, DomainAnnotation.class);
	    int features = 0;
	    int featuresWithDomains = 0;
	    int domains = 0;
	    Set<String> domainModels = new TreeSet<String>();
	    for (Map.Entry<String, List<Tuple5<String, Long, Long, Long, Map<String, List<Tuple5<Long, Long, Double, Double, Double>>>>>> contigElements : annot.getData().entrySet()) {
		for (Tuple5<String, Long, Long, Long, Map<String, List<Tuple5<Long, Long, Double, Double, Double>>>> element : contigElements.getValue()) {
		    String featureId = element.getE1();
		    features++;
		    if (annot.getFeatureToContigAndIndex().get(featureId) == null)
			continue;
		    int domainsInFeature = 0;
		    for (Map.Entry<String, List<Tuple5<Long, Long, Double, Double, Double>>> domainPlaces : element.getE5().entrySet()) {
			String domainRef = domainPlaces.getKey();
			if (!domainRefs.contains(domainRef))
			    continue;
			domainModels.add(domainRef);
			domains += domainPlaces.getValue().size();
			domainsInFeature += domainPlaces.getValue().size();
		    }
		    if (domainsInFeature > 0)
			featuresWithDomains++;
		}
	    }
	    String[] idAndName = getGenomeIdAndScientificName(client, genomeRef);
	    GenomeStat stat = new GenomeStat().withGenomeRef(genomeRef).withKbaseId(idAndName[0])
		.withScientificName(idAndName[1]).withFeatures((long)features)
		.withFeaturesWithDomains((long)featuresWithDomains).withDomains((long)domains)
		.withDomainModels((long)domainModels.size());
	    genomeStat.put(genomeRef, stat);
	    if (genomeStat.size() % 1000 == 0)
		System.out.println("\t" + genomeStat.size() + " genomes were processed");
	}
	return genomeStat;
    }

    private static String[] getGenomeIdAndScientificName(WorkspaceClient client,
							 String genomeRef) throws Exception {
	Map<String, String> genome = client.getObjectSubset(Arrays.asList(
									  new SubObjectIdentity().withRef(genomeRef).withIncluded(Arrays.asList(
																		"id", "scientific_name")))).get(0).getData().asInstance();
	String id = genome.get("id");
	String name = genome.get("scientific_name");
	return new String[] {id, name};
    }
	
    private static long flushCaches(File processedGenomesFile, List<GenomeProcDescr> genomeCache, FileCache clusterCache, long lastFlushTime) throws Exception {
	if (!genomeCache.isEmpty()) {
	    clusterCache.flush();
	    for (GenomeProcDescr gpd : genomeCache) {
		putGenomeIntoProcessedList(processedGenomesFile, gpd.genomeRef);
		System.out.println("Genome [" + gpd.genomeObjectName + "], features=" + gpd.features + ", domains=" + gpd.domains);
	    }
	    System.out.println("Info: " + genomeCache.size() + " genomes were processed in " + (System.currentTimeMillis() - lastFlushTime) + 
			       " ms (average=" + ((System.currentTimeMillis() - lastFlushTime) / genomeCache.size()) + ")");
	    genomeCache.clear();
	}
	return System.currentTimeMillis();
    }
	
    public static <T> T readJsonFromGZip(File f, Class<T> type)
	throws IOException, FileNotFoundException, JsonParseException,
	       JsonMappingException {
	try {
	    InputStream is = new GZIPInputStream(new FileInputStream(f));
	    return UObject.getMapper().readValue(is, type);
	} catch (Exception ex) {
	    throw new IllegalStateException("Error reading json from file: " + f, ex);
	}
    }

    private static void putGenomeIntoProcessedList(File processedGenomesFile,
						   String genomeRef) throws IOException {
	PrintWriter pw = new PrintWriter(new FileWriter(processedGenomesFile, true));
	pw.println(genomeRef);
	pw.close();
    }
	
    private static Set<String> getRestrictedDomainRefs(WorkspaceClient client) throws Exception {
	Set<String> ret = new HashSet<String>();
	DomainModelSet rootSet = getObject(client, domainWsName + "/" + cogPfamDomainSetObjectName, DomainModelSet.class);
	for (String parRef : rootSet.getParentRefs()) {
	    DomainModelSet subSet = getObject(client, parRef, DomainModelSet.class);
	    if (subSet.getSetName().endsWith(" pfam") || subSet.getSetName().endsWith(" COG")) {
		System.out.println("Domain count from [" + subSet.getSetName() + "]: " + subSet.getDomainModelRefs().size());
		ret.addAll(subSet.getDomainModelRefs());
	    }
	}
	return ret;
    }
	
    private static <T> T getObject(WorkspaceClient client, String ref, Class<T> type) throws Exception {
	return client.getObjects(Arrays.asList(new ObjectIdentity().withRef(ref))).get(0).getData().asClassInstance(type);
    }
	
    private static void saveObject(WorkspaceClient client, String wsName, String type, String objName, Object data) throws Exception {
	client.saveObjects(new SaveObjectsParams().withWorkspace(wsName)
			   .withObjects(Arrays.asList(new ObjectSaveData()
						      .withType(type).withName(objName).withData(new UObject(data)))));
    }

    private static WorkspaceClient client(Properties props)
	throws UnauthorizedException, IOException, MalformedURLException,
	       TokenFormatException, AuthException {
	return client(token(props));
    }
	
    private static WorkspaceClient client(String token)
	throws UnauthorizedException, IOException, MalformedURLException,
	       TokenFormatException, AuthException {
	WorkspaceClient client = new WorkspaceClient(new URL(wsUrl), new AuthToken(token));
	client.setAuthAllowedForHttp(true);
	return client;
    }

    private static String token(Properties props) throws AuthException,
							 IOException {
	return AuthService.login(get(props, "user"), get(props, "password")).getToken().toString();
    }
	
    private static String get(Properties props, String propName) {
	String ret = props.getProperty(propName);
	if (ret == null)
	    throw new IllegalStateException("Property is not defined: " + propName);
	return ret;
    }
	
    private static Properties props(File configFile)
	throws FileNotFoundException, IOException {
	Properties props = new Properties();
	InputStream is = new FileInputStream(configFile);
	props.load(is);
	is.close();
	return props;
    }

    private static class FileCache {
	Map<File, List<String>> file2lines = new LinkedHashMap<File, List<String>>();
		
	public void addLine(File f, String l) {
	    List<String> lines = file2lines.get(f);
	    if (lines == null) {
		lines = new ArrayList<String>();
		file2lines.put(f, lines);
	    }
	    lines.add(l);
	}
		
	public void flush() throws IOException {
	    for (File f : file2lines.keySet()) {
		PrintWriter clusterPw = new PrintWriter(new FileWriter(f, true));
		try {
		    for (String l : file2lines.get(f))
			clusterPw.println(l);
		    file2lines.get(f).clear();
		} finally {
		    clusterPw.close();
		}
	    }
	}
    }
	
    private static class GenomeProcDescr {
	String genomeRef;
	String genomeObjectName;
	int features;
	int domains;
		
	public GenomeProcDescr(String genomeRef, String genomeObjectName, int features, int domains) {
	    this.genomeRef = genomeRef;
	    this.genomeObjectName = genomeObjectName;
	    this.features = features;
	    this.domains = domains;
	}
    }
}
*/