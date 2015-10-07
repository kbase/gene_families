package us.kbase.kbasegenefamilies.prepare;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.fasterxml.jackson.core.type.TypeReference;

import us.kbase.auth.AuthException;
import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.auth.TokenFormatException;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.Tuple11;
import us.kbase.common.service.Tuple2;
import us.kbase.common.service.UObject;
import us.kbase.common.service.UnauthorizedException;
// import us.kbase.kbasegenefamilies.DomainAlignments;
import us.kbase.kbasegenefamilies.DomainAnnotation;
import us.kbase.kbasegenefamilies.DomainModelSet;
import us.kbase.kbasegenefamilies.DomainModelType;
import us.kbase.kbasegenefamilies.SearchDomainsBuilder;
import us.kbase.kbasegenefamilies.DomainSearchTask;
import us.kbase.kbasegenefamilies.ObjectStorage;
import us.kbase.kbasegenefamilies.util.Utils;
import us.kbase.workspace.GetObjectInfoNewParams;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.SubObjectIdentity;
import us.kbase.workspace.WorkspaceClient;

public class NerscClusterDomainSearcher {	
    private static final String wsUrl = "http://dev04.berkeley.kbase.us:7058";  // "https://kbase.us/services/ws/";
    private static final String genomeWsName = "KBasePublicGenomesLoad";
    private static final String domainWsName = "KBasePublicGeneDomains";
    private static final String genomeWsType = "KBaseGenomes.Genome";
    private static final String defaultDomainSetObjectName = "BacterialProteinDomains.set";
    private static final String cogPfamDomainSetObjectName = "CogAndPfamDomains.set";
    private static final String domainAnnotationWsType = "KBaseGeneFamilies.DomainAnnotation";
    // private static final String domainAlignmentsWsType = "KBaseGeneFamilies.DomainAlignments";

    public static void main(String[] args) throws Exception {
	// searchDomains(args);
	//cacheDomainAnnotation(args);
	//tokenMany(args);
	//wsMany(args);
	//eukGenomes(args);
    }

    private static void tokenMany(final String[] args) throws Exception {
	for (int n = 0; n < 32; n++) {
	    new Thread(new Runnable() {
		    @Override
			public void run() {
			for (int i = 0; i < 1000; i++)
			    try {
				String token = token(props(args));
				System.out.println(Thread.currentThread().getName() + ", i=" + i + ": token=" + token);
			    } catch (AuthException e) {
				e.printStackTrace();
				System.err.println("Server side:\n" + e.getData());
				System.exit(0);
			    } catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			    }
		    }
		}, "t" + n).start();
	}
    }

    private static void wsMany(final String[] args) throws Exception {
	final String token = token(props(args));
	final WorkspaceClient client = client(token);
	final List<Tuple2<String, String>> refAndNameList = Utils.listAllObjectsRefAndName(client, genomeWsName, genomeWsType);
	for (int n = 0; n < 20; n++) {
	    new Thread(new Runnable() {
		    @Override
			public void run() {
			for (int i = 0; i < refAndNameList.size(); i++)
			    try {
				String genomeObjectName = refAndNameList.get(i).getE2();
				boolean ret = objectExists(client, genomeWsName, genomeObjectName);
				System.out.println(Thread.currentThread().getName() + ", i=" + i + ": ret=" + ret);
			    } catch (ServerException e) {
				e.printStackTrace();
				System.err.println("Server side:\n" + e.getData());
				System.exit(0);
			    } catch (Exception e) {
				e.printStackTrace();
			    }
		    }
		}, "t" + n).start();
	}
    }

    private static void eukGenomes(String[] args) throws Exception {
	WorkspaceClient client = client(token(props(args)));
	int count = 0;
	int bacteria = 0;
	int eukaryota = 0;
	int viruses = 0;
	for (Tuple2<String, String> refAndName : Utils.listAllObjectsRefAndName(client, genomeWsName, genomeWsType)) {
	    System.out.println("Genome [" + refAndName.getE2() + "]");
	    String ref = refAndName.getE1();
	    Map<String, String> genome = null;
	    for (int i = 0; i < 5; i++)
		try {
		    genome = client.getObjectSubset(Arrays.asList(new SubObjectIdentity().withRef(ref)
								  .withIncluded(Arrays.asList("scientific_name", "domain")))).get(0).getData().asInstance();
		} catch (Exception e) {
		    System.err.println(ref + " (" + i + "): " + e.getMessage());
		}
	    if (genome == null)
		continue;
	    String name = genome.get("scientific_name");
	    String domain = genome.get("domain");
	    count++;
	    if (domain == null) {
		System.out.println(domain + ", " + name);
	    } else if (domain.equals("Bacteria")) {
		bacteria++;
	    } else if (domain.equals("Eukaryota")) {
		eukaryota++;
	    } else if (domain.equals("Viruses")) {
		viruses++;
	    } else {
		System.out.println(domain + ", " + name);
	    }
	    if (count % 100 == 0) {
		System.out.println("Stat: count=" + count + ", bact=" + bacteria + ", eukar=" + eukaryota + ", vir=" + viruses);
	    }
	}
	System.out.println("Stat: count=" + count + ", bact=" + bacteria + ", eukar=" + eukaryota);
    }
	
    private static void cacheDomainAnnotation(String[] args) throws Exception {
	Properties props = props(args);
	//getDomainSetRef(client(props));
	GenomeAnnotationChache.cacheDomainAnnotation(props);
    }

    /*
    private static void searchDomains(String[] args) throws Exception {
	if (args.length < 1 || args.length > 3) {
	    System.err.println("Usage: <program> <config_file> [{<genome_ref_list_file> | <core_count> <out_dir>}]");
	    return;
	}
	Properties props = props(args);
	File tempDir = new File(get(props, "temp.dir"));
	String token = null;
	for (int i = 0; i < 10; i++) {
	    try {
		token = token(props);
		break;
	    } catch (Exception ex) {
		Thread.sleep(1000);
	    }
	}
	if (token == null)
	    token = token(props);
	WorkspaceClient client = client(token);
	System.out.println(getDomainSetRef(client));
	if (args.length == 3) {
	    Set<String> allRefs = new TreeSet<String>();
	    Map<String, String> annotNameToRefMap = new TreeMap<String, String>();
	    for (Tuple2<String, String> refAndName : Utils.listAllObjectsRefAndName(client, domainWsName, domainAnnotationWsType))
		annotNameToRefMap.put(refAndName.getE2(), refAndName.getE1());
	    Map<String, String> alignNameToRefMap = new TreeMap<String, String>();
	    for (Tuple2<String, String> refAndName : Utils.listAllObjectsRefAndName(client, domainWsName, domainAlignmentsWsType))
		alignNameToRefMap.put(refAndName.getE2(), refAndName.getE1());
	    for (Tuple2<String, String> refAndName : Utils.listAllObjectsRefAndName(client, genomeWsName, genomeWsType)) {
		String genomeRef = refAndName.getE1();
		String genomeObjectName = refAndName.getE2();
		String genomeAnnotationObjectName = genomeObjectName + ".domains";
		String genomeAlignmentsObjectName = genomeObjectName + ".alignments";
		if (annotNameToRefMap.containsKey(genomeAnnotationObjectName) &&
		    alignNameToRefMap.containsKey(genomeAlignmentsObjectName)) {
		    System.out.println("Genome was already processed and skipped: ref=" + genomeRef + ", name=" + genomeObjectName);
		    continue;
		}
		allRefs.add(genomeRef);
	    }
	    int coreCount = Integer.parseInt(args[1]);
	    List<List<String>> refListChunks = new ArrayList<List<String>>();
	    int chunkPos = 0;
	    for (String genomeRef : allRefs) {
		List<String> list;
		if (chunkPos < refListChunks.size()) {
		    list = refListChunks.get(chunkPos);
		} else {
		    list = new ArrayList<String>();
		    refListChunks.add(list);
		}
		list.add(genomeRef);
		chunkPos = (chunkPos + 1) % coreCount;
	    }
	    for (chunkPos = 0; chunkPos < coreCount; chunkPos++) {
		File f = new File(args[2], "genome_refs_" + chunkPos + ".txt");
		UObject.getMapper().writeValue(f, refListChunks.get(chunkPos));
	    }
	    return;
	}
	ObjectStorage objectStorage = SearchDomainsBuilder.createDefaultObjectStorage(client);
	String domainModelSetRef = getDomainSetRef(client);
	DomainSearchTask task = new DomainSearchTask(tempDir, objectStorage);
	if (args.length == 1) {
	    task.prepareDomainModels(token, domainModelSetRef);
	    return;
	}
	List<String> refList = UObject.getMapper().readValue(new File(args[1]), new TypeReference<List<String>>() {});
	for (String genomeRef : refList) {
	    try {
		Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>> info = 
		    client.getObjectInfoNew(new GetObjectInfoNewParams().withObjects(
										     Arrays.asList(new ObjectIdentity().withRef(genomeRef)))).get(0);
		String genomeObjectName = info.getE2();
		String genomeAnnotationObjectName = genomeObjectName + ".domains";
		String genomeAlignmentsObjectName = genomeObjectName + ".alignments";
		if (objectExists(client, domainWsName, genomeAnnotationObjectName) &&
		    objectExists(client, domainWsName, genomeAlignmentsObjectName)) {
		    System.out.println("Genome was already processed and skipped: ref=" + genomeRef + ", name=" + genomeObjectName);
		    continue;
		}
		long time = System.currentTimeMillis();
		Map<String, String> genomeProps = client.getObjectSubset(Arrays.asList(new SubObjectIdentity().withRef(genomeRef)
										       .withIncluded(Arrays.asList("scientific_name", "domain")))).get(0).getData().asInstance();
		String genomeDomain = genomeProps.get("domain");
		if (genomeDomain == null || !(genomeDomain.equals("Bacteria") || genomeDomain.equals("Archaea"))) {
		    System.out.println("Genome [" + genomeProps.get("scientific_name") + "] is skipped cause " +
				       "it has wrong domain: [" + genomeDomain + "]");
		    continue;
		}
		Tuple2<DomainAnnotation, DomainAlignments> domainsAndAlignments = 
		    task.runDomainSearch(token, domainModelSetRef, genomeRef);
		DomainAlignments alignments = domainsAndAlignments.getE2();
		String alignmentsRef = DomainSearchTask.getRefFromObjectInfo(client.saveObjects(
												new SaveObjectsParams().withWorkspace(domainWsName).withObjects(
																				Arrays.asList(new ObjectSaveData().withType(domainAlignmentsWsType)
																					      .withName(genomeAlignmentsObjectName).withData(new UObject(alignments))))).get(0));
		DomainAnnotation domains = domainsAndAlignments.getE1();
		domains.setAlignmentsRef(alignmentsRef);
		client.saveObjects(new SaveObjectsParams().withWorkspace(domainWsName).withObjects(
												   Arrays.asList(new ObjectSaveData().withType(domainAnnotationWsType)
														 .withName(genomeAnnotationObjectName).withData(new UObject(domains)))));
		System.out.println("Time: " + (System.currentTimeMillis() - time));
	    } catch (ServerException ex) {
		System.err.println("Error processing genome " + genomeRef + ":");
		ex.printStackTrace();
		System.out.println("Server side: " + ex.getData());
	    }
	}
    }
    */

    private static String getDomainSetRef(WorkspaceClient client) throws Exception {
	//return domainWsName + "/" + defaultDomainSetObjectName;
	if (!objectExists(client, domainWsName, cogPfamDomainSetObjectName)) {
	    DomainModelSet rootSet = getObject(client, domainWsName + "/" + defaultDomainSetObjectName, DomainModelSet.class);
	    DomainModelSet cogPfam = new DomainModelSet().withSetName("COG and PFAM domain models");
		// .withDomainModelRefs(new ArrayList<String>())
		// .withParentRefs(new ArrayList<String>())
		// .withTypes(new ArrayList<String>());
	    /*
	    for (String parRef : rootSet.getParentRefs()) {
		DomainModelSet subSet = getObject(client, parRef, DomainModelSet.class);
		if (subSet.getSetName().endsWith(" pfam") || subSet.getSetName().endsWith(" COG"))
		    cogPfam.getParentRefs().add(parRef);
	    }
	    for (String typeRef : rootSet.getTypes()) {
		DomainModelType type = getObject(client, typeRef, DomainModelType.class);
		if (type.getTypeName().toLowerCase().contains("cog") || type.getTypeName().toLowerCase().contains("pfam")) {
		    System.out.println("Domain module type: " + type.getTypeName());
		    cogPfam.getTypes().add(typeRef);
		}
	    }
	    */
	    saveObject(client, domainWsName, "KBaseGeneFamilies.DomainModelSet", cogPfamDomainSetObjectName, cogPfam);
	    System.out.println("DomainSet " + cogPfamDomainSetObjectName + " was saved into db");
	} else {
	    System.out.println("DomainSet " + cogPfamDomainSetObjectName + " is already present in db");
	}
	return domainWsName + "/" + cogPfamDomainSetObjectName;
    }
	
    private static <T> T getObject(WorkspaceClient client, String ref, Class<T> type) throws Exception {
	return client.getObjects(Arrays.asList(new ObjectIdentity().withRef(ref))).get(0).getData().asClassInstance(type);
    }
	
    private static void saveObject(WorkspaceClient client, String wsName, String type, String objName, Object data) throws Exception {
	client.saveObjects(new SaveObjectsParams().withWorkspace(wsName)
			   .withObjects(Arrays.asList(new ObjectSaveData()
						      .withType(type).withName(objName).withData(new UObject(data)))));
    }

    private static Properties props(String[] args)
	throws FileNotFoundException, IOException {
	Properties props = new Properties();
	InputStream is = new FileInputStream(new File(args[0]));
	props.load(is);
	is.close();
	return props;
    }
	
    private static boolean objectExists(WorkspaceClient client, String wsName, String objectName) throws Exception {
	List<?> ret = client.getObjectInfoNew(new GetObjectInfoNewParams().withIgnoreErrors(1L).withObjects(
													    Arrays.asList(new ObjectIdentity().withWorkspace(wsName).withName(objectName))));
	System.out.println(ret);
	return ret != null && ret.size() > 0 && ret.get(0) != null;
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
