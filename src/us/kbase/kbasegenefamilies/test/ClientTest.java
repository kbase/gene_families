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
import us.kbase.userandjobstate.UserAndJobStateClient;

/**
   Tests for annotating genome (DvH) via the client
*/
public class ClientTest {
    private static final String wsUrl = "https://kbase.us/services/ws/";
    private static final String gfUrl = "http://localhost:8123";
    private static final String genomeWsName = "KBasePublicGenomesV4";
    private static final String domainWsName = "KBasePublicGeneDomains";
    private static final String privateWsName = "jmc:gene_domains_test";
    private static final String domainAnnotationType = "KBaseGeneFamilies.DomainAnnotation-1.0";
    private static final String dvID = "kb|g.3562";
    private static final String smartRef = domainWsName+"/SMART-only";
    private static final String allLibsRef = domainWsName+"/All";

    /**
       check that we can read DvH genome from private WS
    */
    @Test public void getDV() throws Exception {
	Genome genome = null;
	
	WorkspaceClient wc = createWsClient(getDevToken());
	genome = wc.getObjects(Arrays.asList(new ObjectIdentity().withRef(privateWsName+"/"+dvID))).get(0).getData().asClassInstance(Genome.class);
	
	System.out.println(genome.getScientificName());
	assertEquals(genome.getScientificName(), "Desulfovibrio vulgaris str. Hildenborough");
    }

    /**
       check that we can read version
    */
    @Test public void getVersion() throws Exception {
	KBaseGeneFamiliesClient gf = new KBaseGeneFamiliesClient(new URL(gfUrl));
	gf.setIsInsecureHttpConnectionAllowed(true);
	String version = gf.version();
	System.out.println("service version is "+version);
	assertNotNull(version);
    }

    /**
       Check that we can annotate DvH with SMART.  This is
       fairly fast.
    */
    @Test
    public void searchDVPSSM() throws Exception {

	AuthToken token = getDevToken();

	String genomeRef = privateWsName+"/"+dvID;

	KBaseGeneFamiliesClient gf = new KBaseGeneFamiliesClient(new URL(gfUrl), token);
	gf.setIsInsecureHttpConnectionAllowed(true);
	String jobId = gf.searchDomains(new SearchDomainsParams()
					.withDmsRef(smartRef)
					.withGenome(genomeRef)
					.withOutWorkspace(privateWsName)
					.withOutResultId("DvH-SMART"));
	UserAndJobStateClient jscl = new UserAndJobStateClient(new URL("https://kbase.us/services/userandjobstate/"), token);
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
		    String rv = jscl.getResults(jobId).getWorkspaceids().get(0);
		    System.out.println("Annotation reference: " + rv);
		}
		else {
		    System.out.println("Detailed error:");
		    System.out.println(jscl.getDetailedError(jobId));
		}
		break;
	    }
	    Thread.sleep(12000);
	}
    }

    /**
       Check that we can annotate DvH with all domains.  This
       should take about an hour.
    @Test
    */
    public void searchDVAll() throws Exception {

	AuthToken token = getDevToken();

	String genomeRef = privateWsName+"/"+dvID;

	KBaseGeneFamiliesClient gf = new KBaseGeneFamiliesClient(new URL(gfUrl), token);
	gf.setIsInsecureHttpConnectionAllowed(true);
	String jobId = gf.searchDomains(new SearchDomainsParams()
					.withDmsRef(allLibsRef)
					.withGenome(genomeRef)
					.withOutWorkspace(privateWsName)
					.withOutResultId("DvH-AllDomains"));
	UserAndJobStateClient jscl = new UserAndJobStateClient(new URL("https://kbase.us/services/userandjobstate/"), token);
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
		    String rv = jscl.getResults(jobId).getWorkspaceids().get(0);
		    System.out.println("Annotation reference: " + rv);
		}
		else {
		    System.out.println("Detailed error:");
		    System.out.println(jscl.getDetailedError(jobId));
		}
		break;
	    }
	    Thread.sleep(12000);
	}
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
}
