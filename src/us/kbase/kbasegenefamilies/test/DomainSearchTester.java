package us.kbase.kbasegenefamilies.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.TreeMap;

import us.kbase.auth.AuthException;
import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.auth.TokenFormatException;
import us.kbase.common.service.Tuple2;
import us.kbase.common.service.Tuple7;
import us.kbase.common.service.UObject;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.kbasegenefamilies.ConstructDomainClustersBuilder;
import us.kbase.kbasegenefamilies.ConstructDomainClustersParams;
import us.kbase.kbasegenefamilies.DefaultTaskBuilder;
import us.kbase.kbasegenefamilies.DomainClusterSearchResult;
import us.kbase.kbasegenefamilies.DomainClusterStat;
import us.kbase.kbasegenefamilies.GenomeStat;
import us.kbase.kbasegenefamilies.KBaseGeneFamiliesClient;
import us.kbase.kbasegenefamilies.ObjectStorage;
import us.kbase.kbasegenefamilies.SearchDomainsAndConstructClustersBuilder;
import us.kbase.kbasegenefamilies.SearchDomainsAndConstructClustersParams;
import us.kbase.kbasegenefamilies.SearchDomainsParams;
import us.kbase.kbasetrees.MSA;
import us.kbase.userandjobstate.UserAndJobStateClient;
import us.kbase.workspace.CopyObjectParams;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.ObjectData;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.SubObjectIdentity;
import us.kbase.workspace.WorkspaceClient;

public class DomainSearchTester {
    private static final String wsName = "nardevuser1:home";
    private static final String wsUrl = "http://dev04.berkeley.kbase.us:7058";
    private static final String domainWsName = "KBasePublicGeneDomains";
    private static final String defaultDCSRObjectName = "CogAndPfam.dcsr";
    private static final String geneFamiliesUrl = "http://140.221.67.204:8123";
    private static final String cogPfamDomainSetObjectName = "CogAndPfamDomains.set";
	
    public static void main(String[] args) throws Exception {
	Properties props = props(new File("config.cfg"));
	String token = token(props);
	WorkspaceClient client = client(token);
	//File tempDir = new File(get(props, "temp.dir"));
	//runDomainSearchLocally(client, token, tempDir);
	//printClusters(client, domainWsName + "/" + defaultDCSRObjectName, new File("par_dcsr_test.txt"));
	//runDomainSearchRemotely(client, token);
	//String annotRef = runDomainSearchOnly(client, token);
	//runDomainClustersExtension(client, token, wsName + "/TempAnnotation");
	//printClusters(client, wsName + "/TempDomains3", new File("dcsr3_test.txt"));
	//runDomainClustersExtensionLocally(client, token, tempDir);
	//findMSA(client);
	testSpeed2(client);
    }

    /*
    private static void runDomainSearchLocally(WorkspaceClient client, String token, File tempDir) throws Exception {
	String genomeRef = wsName + "/Burkholderia_YI23_uid81081.genome";
	String outId = "TempDomains";
	ObjectStorage st = DefaultTaskBuilder.createDefaultObjectStorage(client);
	SearchDomainsAndConstructClustersBuilder builder = new SearchDomainsAndConstructClustersBuilder(tempDir, st);
	String dcsrRef = domainWsName + "/" + defaultDCSRObjectName;
	builder.run(token, new SearchDomainsAndConstructClustersParams().withClustersForExtension(dcsrRef)
		    .withGenomes(Arrays.asList(genomeRef)).withOutWorkspace(wsName).withOutResultId(outId), "temp_job_id", wsName + "/" + outId);
	printClusters(client, wsName + "/" + outId, new File("dcsr_test.txt"));
    }

    private static void runDomainClustersExtensionLocally(WorkspaceClient client, String token, File tempDir) throws Exception {
	String annotRef = wsName + "/TempAnnotation";
	String outId = "TempDomains3";
	String dcsrRef = domainWsName + "/" + defaultDCSRObjectName;
	ObjectStorage st = DefaultTaskBuilder.createDefaultObjectStorage(client);
	ConstructDomainClustersBuilder builder = new ConstructDomainClustersBuilder(tempDir, st);
	builder.run(token, new ConstructDomainClustersParams().withClustersForExtension(dcsrRef)
		    .withGenomeAnnotations(Arrays.asList(annotRef)).withOutWorkspace(wsName).withOutResultId(outId),
		    "temp_job_id", wsName + "/" + outId);
	printClusters(client, wsName + "/" + outId, new File("dcsr3_test.txt"));
    }
	
    private static void printClusters(WorkspaceClient client, String dcsrRef, File out) throws Exception {
	DomainClusterSearchResult dcsr = getObject(client, dcsrRef, DomainClusterSearchResult.class);
	PrintWriter pw = new PrintWriter(out);
	pw.println("-= Genomes =-");
	for (GenomeStat gs : dcsr.getGenomeStatistics().values()) {
	    pw.println("Genome " + gs.getGenomeRef() + ", " + gs.getKbaseId() + ", " + gs.getScientificName() + ": " +
		       "f=" + gs.getFeatures() + ", fwd=" + gs.getFeaturesWithDomains() + ", d=" + gs.getDomains() + ", dm=" + gs.getDomainModels());
	}
	pw.println();
	if (dcsr.getParentRef() != null) {
	    DomainClusterSearchResult parentDcsr = getObject(client, dcsr.getParentRef(), DomainClusterSearchResult.class);
	    pw.println("-= Public genomes =-");
	    for (GenomeStat gs : parentDcsr.getGenomeStatistics().values()) {
		pw.println("Genome " + gs.getGenomeRef() + ", " + gs.getKbaseId() + ", " + gs.getScientificName() + ": " +
			   "f=" + gs.getFeatures() + ", fwd=" + gs.getFeaturesWithDomains() + ", d=" + gs.getDomains() + ", dm=" + gs.getDomainModels());
	    }
	    pw.println();
	}
	pw.println("-= Domains =-");
	for (DomainClusterStat dcs : dcsr.getDomainClusterStatistics().values()) {
	    pw.println("Domain "
	    //  + dcs.getDomainModelRef() + ", "
	    + dcs.getName() + ": g=" + dcs.getGenomes() + ", " +
		       "f=" + dcs.getFeatures() + ", d=" + dcs.getDomains());
	}
	pw.close();
    }
	
    private static void runDomainSearchRemotely(WorkspaceClient client, String token) throws Exception {
	KBaseGeneFamiliesClient gf = new KBaseGeneFamiliesClient(new URL(geneFamiliesUrl), new AuthToken(token));
	gf.setIsInsecureHttpConnectionAllowed(true);
	String genomeRef = wsName + "/Burkholderia_YI23_uid81081.genome";
	String outId = "TempDomains2";
	String dcsrRef = domainWsName + "/" + defaultDCSRObjectName;
	String jobId = gf.searchDomainsAndConstructClusters(new SearchDomainsAndConstructClustersParams().withClustersForExtension(dcsrRef)
							    .withGenomes(Arrays.asList(genomeRef)).withOutWorkspace(wsName).withOutResultId(outId));
	UserAndJobStateClient jscl = new UserAndJobStateClient(new URL("https://kbase.us/services/userandjobstate/"), new AuthToken(token));
	jscl.setAllSSLCertificatesTrusted(true);
	jscl.setIsInsecureHttpConnectionAllowed(true);
	for (int iter = 0; ; iter++) {
	    Tuple7<String, String, String, Long, String, Long, Long> data = jscl.getJobStatus(jobId);
	    String status = data.getE3();
	    Long complete = data.getE6();
	    Long wasError = data.getE7();
	    System.out.println("Status (" + iter + "): " + status);
	    if (complete == 1L) {
		if (wasError == 0L) {
		    String wsRef = jscl.getResults(jobId).getWorkspaceids().get(0);
		    printClusters(client, wsRef, new File("dcsr2_test.txt"));
		} else {
		    System.out.println("Detailed error:");
		    System.out.println(jscl.getDetailedError(jobId));
		}
		break;
	    }
	    Thread.sleep(12000);
	}
    }
*/
	
    private static String runDomainSearchOnly(WorkspaceClient client, String token) throws Exception {
	KBaseGeneFamiliesClient gf = new KBaseGeneFamiliesClient(new URL(geneFamiliesUrl), new AuthToken(token));
	gf.setIsInsecureHttpConnectionAllowed(true);
	String genomeRef = wsName + "/Burkholderia_YI23_uid81081.genome";
	String outId = "TempAnnotation";
	String dmsRef = domainWsName + "/" + cogPfamDomainSetObjectName;
	String jobId = gf.searchDomains(new SearchDomainsParams().withDmsRef(dmsRef)
					.withGenome(genomeRef).withOutWorkspace(wsName).withOutResultId(outId));
	UserAndJobStateClient jscl = new UserAndJobStateClient(new URL("https://kbase.us/services/userandjobstate/"), new AuthToken(token));
	jscl.setAllSSLCertificatesTrusted(true);
	jscl.setIsInsecureHttpConnectionAllowed(true);
	String ret = null;
	for (int iter = 0; ; iter++) {
	    Tuple7<String, String, String, Long, String, Long, Long> data = jscl.getJobStatus(jobId);
	    String status = data.getE3();
	    Long complete = data.getE6();
	    Long wasError = data.getE7();
	    System.out.println("Status (" + iter + "): " + status);
	    if (complete == 1L) {
		if (wasError == 0L) {
		    ret = jscl.getResults(jobId).getWorkspaceids().get(0);
		    System.out.println("Annotation reference: " + ret);
		} else {
		    System.out.println("Detailed error:");
		    System.out.println(jscl.getDetailedError(jobId));
		}
		break;
	    }
	    Thread.sleep(12000);
	}
	return ret;
    }

/*
    private static void runDomainClustersExtension(WorkspaceClient client, String token, String annotRef) throws Exception {
	KBaseGeneFamiliesClient gf = new KBaseGeneFamiliesClient(new URL(geneFamiliesUrl), new AuthToken(token));
	gf.setIsInsecureHttpConnectionAllowed(true);
	//String genomeRef = wsName + "/Burkholderia_YI23_uid81081.genome";
	String outId = "TempDomains3";
	String dcsrRef = domainWsName + "/" + defaultDCSRObjectName;
	String jobId = gf.constructDomainClusters(new ConstructDomainClustersParams().withClustersForExtension(dcsrRef)
						  .withGenomeAnnotations(Arrays.asList(annotRef)).withOutWorkspace(wsName).withOutResultId(outId));
	UserAndJobStateClient jscl = new UserAndJobStateClient(new URL("https://kbase.us/services/userandjobstate/"), new AuthToken(token));
	jscl.setAllSSLCertificatesTrusted(true);
	jscl.setIsInsecureHttpConnectionAllowed(true);
	for (int iter = 0; ; iter++) {
	    Tuple7<String, String, String, Long, String, Long, Long> data = jscl.getJobStatus(jobId);
	    String status = data.getE3();
	    Long complete = data.getE6();
	    Long wasError = data.getE7();
	    System.out.println("Status (" + iter + "): " + status);
	    if (complete == 1L) {
		if (wasError == 0L) {
		    String wsRef = jscl.getResults(jobId).getWorkspaceids().get(0);
		    printClusters(client, wsRef, new File("dcsr3_test.txt"));
		} else {
		    System.out.println("Detailed error:");
		    System.out.println(jscl.getDetailedError(jobId));
		}
		break;
	    }
	    Thread.sleep(12000);
	}
    }
*/
	
    private static void findMSA(WorkspaceClient wc) throws Exception {
	String dcsrRef = domainWsName + "/" + defaultDCSRObjectName;
	DomainClusterSearchResult dcsr = getObject(wc, dcsrRef, DomainClusterSearchResult.class);
	String domainName = "pfam00325";
	String domainRef = null;
	for (DomainClusterStat stat : dcsr.getDomainClusterStatistics().values()) {
	    if (stat.getName().contains(domainName)) {
		// domainRef = stat.getDomainModelRef();
		System.out.println("Domain: " + domainName); // + ", ref=" + domainRef);
		break;
	    }
	}
	String msaRef = dcsr.getMsaRefs().get(domainRef);
	System.out.println("MSA: ref=" + msaRef);
	//MSA msa = getObject(wc, msaRef, MSA.class);
	//System.out.println(msa.getName() + ", " + msa.getAlignmentLength() + ", " + msa.getDescription());
	//msaRef = domainWsName + "/pfam00325.msa";
	ObjectData od = wc.getObjects(Arrays.asList(new ObjectIdentity().withRef(msaRef))).get(0);
	MSA msa = od.getData().asClassInstance(MSA.class);
	System.out.println("MSA: " + msa.getAlignmentLength() + ", " + od.getInfo());
    }
	
    private static void testSpeed(WorkspaceClient wc) throws Exception {
	String wsName = "subdata_extraction_test";
	//wc.createWorkspace(new CreateWorkspaceParams().withWorkspace(wsName));
	String targetType = "KBaseGeneFamilies.DomainAnnotationPlain";
	String pathRoot = "feature_to_contig_and_index";
	String[][] refAndIncluded = {
	    {"868/65933/1", "kb|g.25133.CDS.2769"}, 
	    //				{"868/59833/1", "feature_to_contig_and_index/kb|g.24363.CDS.3268"},
	    //				{"868/48465/1", "feature_to_contig_and_index/kb|g.761.peg.5658"},
	    //				{"868/68100/1", "feature_to_contig_and_index/kb|g.28402.CDS.3500"},
	    //				{"868/69005/1", "feature_to_contig_and_index/kb|g.25130.CDS.1017"},
	    //				{"868/67361/1", "feature_to_contig_and_index/kb|g.26022.CDS.5827"},
	    //				{"868/67267/1", "feature_to_contig_and_index/kb|g.26184.CDS.202"},
	    //				{"868/66969/1", "feature_to_contig_and_index/kb|g.25119.CDS.5632"},
	    //				{"868/53543/1", "feature_to_contig_and_index/kb|g.31717.CDS.4864"},
	    //				{"868/60309/1", "feature_to_contig_and_index/kb|g.26326.CDS.7731"}
	};
	List<SubObjectIdentity> sois = new ArrayList<SubObjectIdentity>();
	for (int i = 0; i < refAndIncluded.length; i++) {
	    String[] pair = refAndIncluded[i];
	    //String ref = pair[0];
	    //UObject ann = wc.getObjects(Arrays.asList(new ObjectIdentity().withRef(ref))).get(0).getData();
	    String objName = "test" + i;
	    Map<String, Object> ftcai = new TreeMap<String, Object>();
	    ftcai.put(pair[1], new Tuple2<String, Long>().withE1("1").withE2(2L));
	    for (int j = 0; j < 100000; j++)
		ftcai.put("key" + j, new Tuple2<String, Long>().withE1("" + j).withE2(0L + j));
	    Map<String, Object> obj = new TreeMap<String, Object>();
	    obj.put(pathRoot, ftcai);
	    obj.put("genome_ref", "genome_ref");
	    obj.put("data", new TreeMap<String, Object>());
	    obj.put("contig_to_size_and_feature_count", new TreeMap<String, Object>());
	    wc.saveObjects(new SaveObjectsParams().withWorkspace(wsName).withObjects(Arrays.asList(
												   new ObjectSaveData().withType(targetType).withName(objName).withData(new UObject(obj)))));
	    //wc.copyObject(new CopyObjectParams().withFrom(new ObjectIdentity().withRef(ref))
	    //		.withTo(new ObjectIdentity().withWorkspace(wsName).withName(objName)));
	    sois.add(new SubObjectIdentity().withRef(wsName + "/" + objName).withIncluded(
											  Arrays.asList(pathRoot + "/" + pair[1])));
	    System.out.println("Object " + objName + " was saved");
	}
	long time = System.currentTimeMillis();
	List<ObjectData> ret = wc.getObjectSubset(sois);
	time = System.currentTimeMillis() - time;
	System.out.println("Time: " + time);
	for (ObjectData od : ret)
	    System.out.println(od);
    }

    private static void testSpeed2(WorkspaceClient wc) throws Exception {
	String wsName = "subdata_extraction_test";
	//wc.createWorkspace(new CreateWorkspaceParams().withWorkspace(wsName));
	String targetType = "KBaseGeneFamilies.DomainAnnotationPlain";
	String pathRoot = "feature_to_contig_and_index";
	Random rnd = new Random();
	for (int count = 10000; count <= 150000; count += 10000) {
	    String objName = "test0";
	    Map<String, Object> ftcai = new TreeMap<String, Object>();
	    for (int j = 0; j < count; j++)
		ftcai.put("key" + j, new Tuple2<String, Long>().withE1("" + j).withE2(0L + j));
	    Map<String, Object> obj = new TreeMap<String, Object>();
	    obj.put(pathRoot, ftcai);
	    obj.put("genome_ref", "genome_ref");
	    obj.put("data", new TreeMap<String, Object>());
	    obj.put("contig_to_size_and_feature_count", new TreeMap<String, Object>());
	    wc.saveObjects(new SaveObjectsParams().withWorkspace(wsName).withObjects(Arrays.asList(
												   new ObjectSaveData().withType(targetType).withName(objName).withData(new UObject(obj)))));
	    String data = UObject.getMapper().writeValueAsString(obj);
	    //System.out.println("Object " + objName + " was saved for count=" + count + ", size=" + data.length());
	    double avgTime = 0;
	    double avgLen = 0;
	    int iterations = 100;
	    for (int i = 0; i < iterations; i++) {
		long time = System.currentTimeMillis();
		String ret = "" + wc.getObjectSubset(Arrays.asList(new SubObjectIdentity().withRef(wsName + "/" + objName).withIncluded(
																	Arrays.asList(pathRoot + "/key" + rnd.nextInt(count)))));
		time = System.currentTimeMillis() - time;
		avgTime += time;
		avgLen += ret.length();
	    }
	    avgTime /= iterations;
	    avgLen /= iterations;
	    System.out.println("Map-size: " + count + ", object-length: " + data.length() + ", " +
			       "ret-length=" + avgLen + ", time: " + avgTime + " ms");
	}
    }

    private static <T> T getObject(WorkspaceClient client, String ref, Class<T> type) throws Exception {
	return client.getObjects(Arrays.asList(new ObjectIdentity().withRef(ref))).get(0).getData().asClassInstance(type);
    }

    private static Properties props(File cfgFile)
	throws FileNotFoundException, IOException {
	Properties props = new Properties();
	InputStream is = new FileInputStream(cfgFile);
	props.load(is);
	is.close();
	return props;
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
}
