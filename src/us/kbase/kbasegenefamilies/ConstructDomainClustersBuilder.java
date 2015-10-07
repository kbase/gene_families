package us.kbase.kbasegenefamilies;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import us.kbase.common.service.Tuple11;
import us.kbase.common.service.Tuple2;
import us.kbase.common.service.Tuple3;
import us.kbase.common.service.Tuple4;
import us.kbase.common.service.Tuple5;
import us.kbase.common.service.UObject;
import us.kbase.kbasegenefamilies.util.Utils;
import us.kbase.kbasetrees.MSA;
import us.kbase.workspace.ListObjectsParams;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.ProvenanceAction;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.SubObjectIdentity;

/**
   This class appears to build clusters out of DomainAnnotations.
   As of 1/14/15, it does not compile because Clusters are commented
   out of the typespec.

public class ConstructDomainClustersBuilder extends DefaultTaskBuilder<ConstructDomainClustersParams> {
    private static final String msaWsType = "KBaseTrees.MSA";
    private static final String domainClusterWsType = "KBaseGeneFamilies.DomainCluster";
    private static final String domainClusterSearchResultWsType = "KBaseGeneFamilies.DomainClusterSearchResult";

    public ConstructDomainClustersBuilder() {
    }
	
    public ConstructDomainClustersBuilder(File tempDir, ObjectStorage objectStorage) {
	this.tempDir = tempDir;
	this.storage = objectStorage;
    }

    @Override
	public Class<ConstructDomainClustersParams> getInputDataType() {
	return ConstructDomainClustersParams.class;
    }
	
    @Override
	public String getOutRef(ConstructDomainClustersParams inputData) {
	return inputData.getOutWorkspace() + "/" + inputData.getOutResultId();
    }
	
    @Override
	public String getTaskDescription() {
	return "Construct domain clusters for domain annotations prepared before";
    }
	
    @Override
	public void run(String token, ConstructDomainClustersParams inputData, String jobId,
			String outRef) throws Exception {
	List<String> annotRefs = inputData.getGenomeAnnotations();
	GenomeAnnotationAlignmentProvider annAlnProv = new GenomeAnnotationAlignmentProvider(storage, token, annotRefs, null);
	constructClusters(storage, token, tempDir, jobId, "construct_domain_clusters",
			  inputData, annAlnProv, inputData.getDmsRef(), inputData.getClustersForExtension(), 
			  inputData.getOutWorkspace(), inputData.getOutResultId(), 
			  inputData.getIsDomainClusterDataStoredOutside() != null && 
			  inputData.getIsDomainClusterDataStoredOutside() != 0L, 
			  inputData.getDomainClusterDataIdPrefix(), 
			  inputData.getDomainClusterDataIdSuffix());
    }
	
    public static void constructClusters(ObjectStorage storage, String token, File tempDir, String jobId, String serviceMethod, 
					 Object inputParams, GenomeAnnotationAlignmentProvider annAlnProv, 
					 String domainSetRef, String parentSearchRef, String outWsName, String outId, 
					 boolean isDomainClusterDataStoredOutside, String domainClusterDataIdPrefix, 
					 String domainClusterDataIdSuffix) throws Exception {
	String parentDomainSetRef = null;
	Map<String, String> domainModelToParentClusterRef = null;
	Map<String, String> domainModelToParentMSARef = null;
	Map<String, DomainClusterStat> domainModelToParentStat = null;
	if (parentSearchRef != null) {
	    DomainClusterSearchResult parent = storage.getObjects(token, Arrays.asList(new ObjectIdentity().withRef(parentSearchRef))).get(0).getData().asClassInstance(DomainClusterSearchResult.class);
	    parentDomainSetRef = parent.getUsedDmsRef();
	    domainModelToParentClusterRef = parent.getDomainClusterRefs();
	    domainModelToParentMSARef = parent.getMsaRefs();
	    domainModelToParentStat = parent.getDomainClusterStatistics();
	}
	if (domainSetRef == null)
	    domainSetRef = parentDomainSetRef;
	if (domainSetRef == null)
	    throw new IllegalStateException("Domain model set is not defined");
	File clusterDir = null;
	try {
	    if (annAlnProv.size() > 5) {
		clusterDir = new File(new File(tempDir, "clusters"), "" + System.currentTimeMillis() + "_" + jobId);
		clusterDir.mkdirs();
	    }
	    FileCache clusterCache = new FileCache(clusterDir);
	    Map<String, GenomeStat> genomeRefToStat = new TreeMap<String, GenomeStat>();
	    Set<String> domainRefs = new TreeSet<String>();
	    Map<String, DomainAnnotation> genomeRefToAnnot = new TreeMap<String, DomainAnnotation>();
	    Map<String, DomainAlignments> genomeRefToAlign = new TreeMap<String, DomainAlignments>();
	    Map<String, String> genomeRefToAnnotRef = new TreeMap<String, String>();
	    // Iterate over genomes (genome domain annotation objects)
	    for (int pos = 0; pos < annAlnProv.size(); pos++) {
		Tuple3<String, DomainAnnotation, DomainAlignments> entry = annAlnProv.get(pos);
		String annRef = entry.getE1();
		DomainAnnotation annot = entry.getE2();
		String genomeRef = annot.getGenomeRef();
		String[] kBaseIdAndSciName = getGenomeIdAndScientificName(storage, token, genomeRef);
		DomainAlignments align = entry.getE3();
		if (annRef == null) {
		    genomeRefToAnnot.put(genomeRef, annot);
		    genomeRefToAlign.put(genomeRef, align);
		}
		else {
		    genomeRefToAnnotRef.put(genomeRef, annRef);
		}
		int features = 0;
		int featuresWithDomains = 0;
		int domains = 0;
		Set<String> domainModelsForGenome = new TreeSet<String>();

		// Iterate over contigs
		for (Map.Entry<String, List<Tuple5<String, Long, Long, Long, Map<String, List<Tuple5<Long, Long, Double, Double, Double>>>>>> contigElements : annot.getData().entrySet()) {
		    String contigId = contigElements.getKey();

		    // Iterate over features
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
			boolean featureWithDomains = false;

			// Iterate over domains
			for (Map.Entry<String, List<Tuple5<Long, Long, Double, Double, Double>>> domainPlaces : element.getE5().entrySet()) {
			    String domainRef = domainPlaces.getKey();
			    String clusterFile = "cluster_" + domainRef.replace('/', '_') + ".txt";
			    for (Tuple5<Long, Long, Double, Double, Double> domainPlace : domainPlaces.getValue()) {
				String alignedSeq = align.getAlignments().get(domainRef).get(featureId).get("" + domainPlace.getE1());
				if (alignedSeq == null)
				    throw new IllegalStateException("Can't find ailgnment for feature [" + featureId + "] for domain: " + domainRef);
				clusterCache.addLine(clusterFile, "[*]\t" + genomeRef + "\t" + contigId + "\t" + featureId + "\t" + 
						     featureIndex + "\t" + domainPlace.getE1() + "\t" + domainPlace.getE2() + "\t" + 
						     domainPlace.getE3() + "\t" + domainPlace.getE4() + "\t" + domainPlace.getE5() + "\t" + alignedSeq);
				domains++;
				featureWithDomains = true;
				domainRefs.add(domainRef);
				domainModelsForGenome.add(domainRef);
			    }
			}
			if (featureWithDomains)
			    featuresWithDomains++;
		    }
		}
		genomeRefToStat.put(genomeRef, new GenomeStat().withGenomeRef(genomeRef)
				    .withKbaseId(kBaseIdAndSciName[0]).withScientificName(kBaseIdAndSciName[1]) 
				    .withFeatures((long)features).withFeaturesWithDomains((long)featuresWithDomains)
				    .withDomains((long)domains).withDomainModels((long)domainModelsForGenome.size()));
		if (genomeRefToStat.size() % 30 == 0)
		    clusterCache.flush();
	    }
	    clusterCache.flush();

	    /////////////////////////////////////////////// Domains //////////////////////////////////////////////
	    Map<String, DomainClusterStat> domainModelRefToStat = new TreeMap<String, DomainClusterStat>();
	    Map<String, String> domainModelRefToNameMap = loadDomainRefToNameMap(tempDir, storage, token, domainSetRef);
	    Map<String, String> domainModelRefToClusterRef = new TreeMap<String, String>();
	    Map<String, DomainCluster> domainModelRefToCluster = new TreeMap<String, DomainCluster>();
	    Map<String, String> msaRefs = new TreeMap<String, String>();
	    Map<String, MSA> msas = new TreeMap<String, MSA>();
	    for (String domainRef : domainRefs) {
		String clusterFile = "cluster_" + domainRef.replace('/', '_') + ".txt";
		String domainModelName = domainModelRefToNameMap.get(domainRef);
		if (domainModelName == null)
		    throw new IllegalStateException("No domain model name for reference: " + domainRef);
		Set<String> genomeRefAndFeatureIdAndStart = new HashSet<String>();
		Map<String, Tuple4<String, String, Long, List<Tuple5<Long, Long, Double, Double, Double>>>> genomeRefAndFeatureIdToElement = 
		    new HashMap<String, Tuple4<String, String, Long, List<Tuple5<Long, Long, Double, Double, Double>>>>();
		DomainCluster cluster = new DomainCluster().withModel(domainRef).withData(new TreeMap<String, 
											  List<Tuple4<String, String, Long, List<Tuple5<Long, Long, Double, Double, Double>>>>>());
		if (domainModelToParentClusterRef != null)
		    cluster.setParentRef(domainModelToParentClusterRef.get(domainRef));
		MSA msa = new MSA().withAlignment(new TreeMap<String, String>());
		if (domainModelToParentMSARef != null)
		    msa.setParentMsaRef(domainModelToParentMSARef.get(domainRef));
		int alnLen = 0;
		DomainClusterStat stat = null;
		if (domainModelToParentStat != null)
		    stat = domainModelToParentStat.get(domainRef);
		if (stat == null)
		    stat = new DomainClusterStat() // .withDomainModelRef(domainRef).withName(domainModelName)
			.withGenomes(0L).withFeatures(0L).withDomains(0L);
		domainModelRefToStat.put(domainRef, stat);
		Set<String> genomeRefs = new HashSet<String>();
		for (String l : clusterCache.getLines(clusterFile)) {
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
		    Tuple4<String, String, Long, List<Tuple5<Long, Long, Double, Double, Double>>> featureElement = 
			genomeRefAndFeatureIdToElement.get(key2);
		    if (featureElement == null) {
			featureElement = new Tuple4<String, String, Long, List<Tuple5<Long, Long, Double, Double, Double>>>()
			    .withE1(contigId).withE2(featureId).withE3(featureIndex)
			    .withE4(new ArrayList<Tuple5<Long, Long, Double, Double, Double>>());
			elements.add(featureElement);
			genomeRefAndFeatureIdToElement.put(key2, featureElement);
			stat.setFeatures(stat.getFeatures() + 1);
			genomeRefs.add(genomeRef);
		    }
		    featureElement.getE4().add(domainPlace);
		    stat.setDomains(stat.getDomains() + 1);
		}
		stat.setGenomes(stat.getGenomes() + genomeRefs.size());
		msa.setAlignmentLength((long)alnLen);
		if (isDomainClusterDataStoredOutside) {
		    String domainClusterName = domainModelName;
		    if (domainClusterDataIdPrefix != null && !domainClusterDataIdPrefix.isEmpty())
			domainClusterName = domainClusterDataIdPrefix + domainClusterName;
		    if (domainClusterDataIdSuffix != null && !domainClusterDataIdSuffix.isEmpty())
			domainClusterName = domainClusterName + domainClusterDataIdSuffix;

		    ///// MSA
		    String msaRef = saveObject(storage, token, outWsName, msaWsType, domainClusterName + ".msa", 
					       msa, "Object was constructed as part of domain clusters construction procedure",
					       serviceMethod, inputParams);
		    msaRefs.put(domainRef, msaRef);

		    ///// DomainCluster
		    cluster.setMsaRef(msaRef);
		    String clusterRef = saveObject(storage, token, outWsName, domainClusterWsType, domainClusterName + ".cluster", 
						   msa, "Object was constructed as part of domain clusters construction procedure",
						   serviceMethod, inputParams);
		    domainModelRefToClusterRef.put(domainRef, clusterRef);
		} else {
		    msas.put(domainRef, msa);
		    domainModelRefToCluster.put(domainRef, cluster);
		}
	    }
	    DomainClusterSearchResult res = new DomainClusterSearchResult();
	    res.setUsedDmsRef(domainSetRef);
	    res.setAnnotations(genomeRefToAnnot);
	    res.setAlignments(genomeRefToAlign);
	    res.setAnnotationRefs(genomeRefToAnnotRef);
	    res.setGenomeStatistics(genomeRefToStat);
	    res.setDomainClusters(domainModelRefToCluster);
	    res.setDomainClusterRefs(domainModelRefToClusterRef);
	    res.setMsas(msas);
	    res.setMsaRefs(msaRefs);
	    res.setDomainClusterStatistics(domainModelRefToStat);
	    res.setParentRef(parentSearchRef);
	    saveObject(storage, token, outWsName, domainClusterSearchResultWsType, outId, res,
		       "Object was constructed as part of domain clusters construction procedure", 
		       serviceMethod, inputParams);
	}
	finally {
	    if (clusterDir != null) {
		for (File f : clusterDir.listFiles())
		    f.delete();
		clusterDir.delete();
	    }
	}
    }
		
    private static String saveObject(ObjectStorage storage, String token, String ws, String type, String id,
				     Object obj, String provDescr, String serviceMethod, Object inputParams) throws Exception {
	ProvenanceAction prov = new ProvenanceAction().withDescription(provDescr)
	    .withService(KBaseGeneFamiliesServer.SERVICE_REGISTERED_NAME)
	    .withServiceVer(KBaseGeneFamiliesServer.SERVICE_VERSION)
	    .withMethod(serviceMethod).withMethodParams(Arrays.asList(new UObject(inputParams)));
	ObjectSaveData data = new ObjectSaveData().withData(new UObject(obj))
	    .withType(type).withProvenance(Arrays.asList(prov));
	try {
	    long objid = Long.parseLong(id);
	    data.withObjid(objid);
	}
	catch (NumberFormatException ex) {
	    data.withName(id);
	}
	return DomainSearchTask.getRefFromObjectInfo(storage.saveObjects(token,new SaveObjectsParams().withWorkspace(ws).withObjects(Arrays.asList(data))).get(0));
    }

    private static String[] getGenomeIdAndScientificName(ObjectStorage storage, String token, 
							 String genomeRef) throws Exception {
	Map<String, String> genome = storage.getObjectSubset(token, Arrays.asList(new SubObjectIdentity().withRef(genomeRef).withIncluded(Arrays.asList("id", "scientific_name")))).get(0).getData().asInstance();
	String id = genome.get("id");
	String name = genome.get("scientific_name");
	return new String[] {id, name};
    }
	
    private static Map<String, String> loadDomainRefToNameMap(File tempDir, ObjectStorage storage, String token, String domainModelSetRef) throws Exception {
	return new DomainSearchTask(tempDir, storage).loadDomainModelRefToNameMap(token, domainModelSetRef);
    }
	
    public static List<Tuple2<String, String>> listAllObjectsRefAndName(ObjectStorage client, 
									String token, String wsName, String wsType) throws Exception {
	List<Tuple2<String, String>> ret = new ArrayList<Tuple2<String, String>>();
	for (int partNum = 0; ; partNum++) {
	    int sizeOfPart = 0;
	    for (Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>> info : 
		     client.listObjects(token, new ListObjectsParams().withWorkspaces(Arrays.asList(wsName)).withType(wsType).withLimit(10000L).withSkip(partNum * 10000L))) {
		String ref = Utils.getRefFromObjectInfo(info);
		String objectName = info.getE2();
		ret.add(new Tuple2<String, String>().withE1(ref).withE2(objectName));
		sizeOfPart++;
	    }
	    if (sizeOfPart == 0)
		break;
	}
	return ret;
    }

    public static class GenomeAnnotationAlignmentProvider {
	private List<String> annotRefs;
	private List<Tuple2<DomainAnnotation, DomainAlignments>> annotAndAln;
	private ObjectStorage storage;
	private String token;
		
	public GenomeAnnotationAlignmentProvider(ObjectStorage storage, String token, List<String> annotRefs, List<Tuple2<DomainAnnotation, DomainAlignments>> annotAndAln) {
	    this.annotRefs = annotRefs;
	    if (this.annotRefs == null)
		this.annotRefs = Collections.<String>emptyList();
	    this.annotAndAln = annotAndAln;
	    if (this.annotAndAln == null)
		this.annotAndAln = Collections.<Tuple2<DomainAnnotation, DomainAlignments>>emptyList();
	    this.storage = storage;
	    this.token = token;
	}
		
	public int size() {
	    return annotRefs.size() + annotAndAln.size();
	}
		
	public Tuple3<String, DomainAnnotation, DomainAlignments> get(int index) throws Exception {
	    if (index < annotRefs.size()) {
		String annotRef = annotRefs.get(index);
		DomainAnnotation ann = storage.getObjects(token, Arrays.asList(new ObjectIdentity().withRef(annotRef))).get(0).getData().asClassInstance(DomainAnnotation.class);
		DomainAlignments aln = storage.getObjects(token, Arrays.asList(new ObjectIdentity().withRef(ann.getAlignmentsRef()))).get(0).getData().asClassInstance(DomainAlignments.class);
		return new Tuple3<String, DomainAnnotation, DomainAlignments>().withE1(annotRef).withE2(ann).withE3(aln);
	    }
	    else {
		Tuple2<DomainAnnotation, DomainAlignments> ret = annotAndAln.get(index - annotRefs.size());
		return new Tuple3<String, DomainAnnotation, DomainAlignments>().withE2(ret.getE1()).withE3(ret.getE2());
	    }
	}
    }
	
    public static class FileCache {
	private File workDir;
	private Map<String, List<String>> file2lines = new LinkedHashMap<String, List<String>>();
		
	public FileCache(File dir) {
	    workDir = dir;
	}
		
	public void addLine(String file, String l) {
	    List<String> lines = file2lines.get(file);
	    if (lines == null) {
		lines = new ArrayList<String>();
		file2lines.put(file, lines);
	    }
	    lines.add(l);
	}
		
	public void flush() throws IOException {
	    if (workDir == null)
		return;
	    for (String fileName : file2lines.keySet()) {
		File f = new File(workDir, fileName);
		PrintWriter clusterPw = new PrintWriter(new FileWriter(f, true));
		try {
		    for (String l : file2lines.get(f))
			clusterPw.println(l);
		    file2lines.get(f).clear();
		}
		finally {
		    clusterPw.close();
		}
	    }
	}
		
	public List<String> getLines(String file) throws IOException {
	    if(workDir == null) {
		return file2lines.get(file);
	    }
	    else {
		List<String> ret = new ArrayList<String>();
		BufferedReader br = new BufferedReader(new FileReader(new File(workDir, file)));
		while (true) {
		    String l = br.readLine();
		    if (l == null)
			break;
		    ret.add(l);
		}
		br.close();
		return ret;
	    }
	}
    }
}
*/
