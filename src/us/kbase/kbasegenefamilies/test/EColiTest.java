package us.kbase.kbasegenefamilies.test;

import java.io.*;
import java.util.*;
import java.net.URL;

import org.junit.Test;
import static junit.framework.Assert.*;

import org.strbio.IO;
import org.strbio.util.*;
import com.fasterxml.jackson.databind.*;

import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.common.service.*;
import us.kbase.workspace.*;
import us.kbase.kbasegenomes.*;
import us.kbase.kbasegenefamilies.*;

/**
   Tests for setting up sample db and annotating E. coli locally
*/
public class EColiTest {
    private static final String wsUrl = "https://kbase.us/services/ws/";
    private static final String genomeWsName = "KBasePublicGenomesV4";
    private static final String domainWsName = "KBasePublicGeneDomains";
    private static final String domainLibraryType = "KBaseGeneFamilies.DomainLibrary-1.0";
    private static final String domainModelSetType = "KBaseGeneFamilies.DomainModelSet-1.0";
    private static final String domainAnnotationType = "KBaseGeneFamilies.DomainAnnotation-1.0";
    private static final String ecoliRef = genomeWsName+"/kb|g.0";
    private static final String smartRef = domainWsName+"/SMART-only";
    private static final String tigrRef = domainWsName+"/TIGRFAMs-only";
    private static final String allLibsRef = domainWsName+"/All";

    /**
       check that we can read E coli genome from WS or file;
       if we get from WS, cache in file to avoid delays/loading server
    */
    @Test public void getEColi() throws Exception {
	Genome genome = null;
	
	ObjectMapper mapper = new ObjectMapper();

	File f = new File("/kb/dev_container/modules/gene_families/data/tmp/g.0");
	if (f.canRead()) {
	    System.out.println("Reading genome from file");
	    try {
		genome = mapper.readValue(f, Genome.class);
	    }
	    catch (Exception e) {
		genome = null;
	    }
	}

	if (genome==null) {
	    System.out.println("Reading genome from WS");
	    WorkspaceClient wc = createWsClient(null);
	    genome = wc.getObjects(Arrays.asList(new ObjectIdentity().withRef(ecoliRef))).get(0).getData().asClassInstance(Genome.class);
	    mapper.writeValue(f,genome);
	}
	
	System.out.println(genome.getScientificName());
	assertEquals(genome.getScientificName(), "Escherichia coli K12");
    }

    /**
       Check that we can get the SMART-only DomainModelSet from the
       public workspace.
    */
    @Test
    public void getSMART() throws Exception {
	WorkspaceClient wc = createWsClient(getDevToken());
	DomainModelSet smart = wc.getObjects(Arrays.asList(new ObjectIdentity().withRef(smartRef))).get(0).getData().asClassInstance(DomainModelSet.class);

	assertEquals(smart.getSetName(),"SMART-only");
    }

    /**
       Check that we can annotate E. coli with SMART.  This is
       fairly fast.
    */
    @Test
	public void searchEColiPSSM() throws Exception {

	AuthToken token = getDevToken();
	WorkspaceClient wc = createWsClient(token);

	ObjectStorage storage = SearchDomainsBuilder.createDefaultObjectStorage(wc);

	DomainSearchTask dst = new DomainSearchTask(new File("/kb/dev_container/modules/gene_families/data/tmp"), storage);
	
	DomainAnnotation results = dst.runDomainSearch(token.toString(),
						       smartRef,
						       ecoliRef);

	/*
	wc.saveObjects(new SaveObjectsParams()
		       .withWorkspace(domainWsName)
		       .withObjects(Arrays.asList(new ObjectSaveData()
						  .withType(domainAnnotationType)
						  .withName("SMART-g.0-2")
						  .withData(new UObject(results)))));
	*/
    }

    /**
       Check that we can annotate E. coli with TIGRFAMs.  Takes ~12 min
       on a 2-CPU Magellan instance.
    @Test
    */
	public void searchEColiHMM() throws Exception {

	AuthToken token = getDevToken();
	WorkspaceClient wc = createWsClient(token);

	ObjectStorage storage = SearchDomainsBuilder.createDefaultObjectStorage(wc);

	DomainSearchTask dst = new DomainSearchTask(new File("/kb/dev_container/modules/gene_families/data/tmp"), storage);
	
	DomainAnnotation results = dst.runDomainSearch(token.toString(),
						       tigrRef,
						       ecoliRef);

	wc.saveObjects(new SaveObjectsParams()
		       .withWorkspace(domainWsName)
		       .withObjects(Arrays.asList(new ObjectSaveData()
						  .withType(domainAnnotationType)
						  .withName("TIGR-g.0")
						  .withData(new UObject(results)))));
    }

    /**
       Check that we can annotate E. coli with all domain libraries.
       Takes ~65 min on a 2-CPU Magellan instance.
    @Test
    */
	public void searchEColiAll() throws Exception {

	AuthToken token = getDevToken();
	WorkspaceClient wc = createWsClient(token);

	ObjectStorage storage = SearchDomainsBuilder.createDefaultObjectStorage(wc);

	DomainSearchTask dst = new DomainSearchTask(new File("/kb/dev_container/modules/gene_families/data/tmp"), storage);
	
	DomainAnnotation results = dst.runDomainSearch(token.toString(),
						       allLibsRef,
						       ecoliRef);

	wc.saveObjects(new SaveObjectsParams()
		       .withWorkspace(domainWsName)
		       .withObjects(Arrays.asList(new ObjectSaveData()
						  .withType(domainAnnotationType)
						  .withName("Alldomains-g.0")
						  .withData(new UObject(results)))));
    }

    /**
       creates a workspace client; if token is null, client can
       only read public workspaces
    */
    public static WorkspaceClient createWsClient(AuthToken token) throws Exception {
	WorkspaceClient rv = null;
	if (token==null)
	    rv = new WorkspaceClient(new URL(wsUrl));
	else
	    rv = new WorkspaceClient(new URL(wsUrl),token);
	rv.setAuthAllowedForHttp(true);
	return rv;
    }

    /**
       gets the auth token out of the properties file.  To create
       it on your dev instance, do:
       <pre>
       kbase-login (your user name)
       kbase-whoami -t
       </pre>
       Take the resulting text (starting with "un=") and put it in
       the auth.properties file, as auth.token.  Replace the text
       in the file that says "paste token here" with your token.
    */
    public static AuthToken getDevToken() throws Exception {
	Properties prop = new Properties();
	try {
	    prop.load(EColiTest.class.getClassLoader().getResourceAsStream("auth.properties"));
	}
	catch (IOException e) {
	}
	catch (SecurityException e) {
	}
	String value = prop.getProperty("auth.token", null);
	return new AuthToken(value);
    }

    private static String getRefFromObjectInfo(Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>> info) {
	return info.getE7() + "/" + info.getE1() + "/" + info.getE5();
    }

    public static void main(String[] args) {
	try {
	    EColiTest t = new EColiTest();
	    t.getEColi();
	}
	catch (Exception e) {
	    e.printStackTrace();
	}
    }
}
