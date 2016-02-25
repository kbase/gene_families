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
import us.kbase.shock.client.*;
import us.kbase.kbasegenomes.*;
import us.kbase.kbasegenefamilies.*;
import us.kbase.common.taskqueue.TaskQueueConfig;

/**
   Test that all shock libraries can be downloaded
*/
public class ShockLibraryTest {
    private static final String domainWsName = "KBasePublicGeneDomains";
    private static final String allRef = domainWsName+"/All";

    /**
       Check that we can get the All Libraries DomainModelSet from the
       public workspace.
    */
    @Test
    public void getDMS() throws Exception {
        WorkspaceClient wc = createWsClient(getDevToken());
        DomainModelSet dms = wc.getObjects(Arrays.asList(new ObjectIdentity().withRef(allRef))).get(0).getData().asClassInstance(DomainModelSet.class);

        assertEquals(dms.getSetName(),"All libraries");
    }

    /**
       Check that we can get all the All Libraries DomainModelSet from the
       public workspace.
    */
    @Test
    public void checkDMS() throws Exception {
        WorkspaceClient wc = createWsClient(getDevToken());
        TaskQueueConfig cfg = KBaseGeneFamiliesServer.getTaskConfig();
        Map<String,String> props = cfg.getAllConfigProps();
        String shockUrl = props.get(KBaseGeneFamiliesServer.CFG_PROP_SHOCK_SRV_URL);
        if (shockUrl==null)
            shockUrl = KBaseGeneFamiliesServer.defaultShockUrl;
        System.out.println("Shock url is "+shockUrl);
        BasicShockClient client = new BasicShockClient(new URL(shockUrl));
        DomainModelSet dms = wc.getObjects(Arrays.asList(new ObjectIdentity().withRef(allRef))).get(0).getData().asClassInstance(DomainModelSet.class);

        Map<String,String> domainLibMap = dms.getDomainLibs();

        for (String id : domainLibMap.values()) {
            DomainLibrary dl = wc.getObjects(Arrays.asList(new ObjectIdentity().withRef(id))).get(0).getData().asClassInstance(DomainLibrary.class);
            System.out.println("Testing shock files for "+dl.getSource()+" "+dl.getVersion());
            for (Handle h : dl.getLibraryFiles()) {
                System.out.println("  testing "+h.getFileName()+", "+h.getShockId());
                File f = new File("/tmp/"+h.getFileName());
                OutputStream os = new BufferedOutputStream(new FileOutputStream(f));
                client.getFile(new ShockNodeId(h.getShockId()),os);
                os.close();
                assertTrue(f.length() > 0);
            }
        }
    }
    
    /**
       creates a workspace client; if token is null, client can
       only read public workspaces
    */
    public static WorkspaceClient createWsClient(AuthToken token) throws Exception {
        WorkspaceClient rv = null;

        TaskQueueConfig cfg = KBaseGeneFamiliesServer.getTaskConfig();
        Map<String,String> props = cfg.getAllConfigProps();
        String wsUrl = props.get(KBaseGeneFamiliesServer.CFG_PROP_WS_SRV_URL);
        if (wsUrl==null)
            wsUrl = KBaseGeneFamiliesServer.defaultWsUrl;
	
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
    public static AuthToken getDevToken() {
        AuthToken rv = null;
        Properties prop = new Properties();
        try {
            prop.load(EColiTest.class.getClassLoader().getResourceAsStream("auth.properties"));
        }
        catch (IOException e) {
        }
        catch (SecurityException e) {
        }
        try {
            String value = prop.getProperty("auth.token", null);
            rv = new AuthToken(value);
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            rv = null;
        }
        return rv;
    }
}
