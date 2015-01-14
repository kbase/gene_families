package us.kbase.kbasegenefamilies;

import us.kbase.auth.AuthToken;
import us.kbase.common.service.JsonServerMethod;
import us.kbase.common.service.JsonServerServlet;

//BEGIN_HEADER
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.ini4j.Ini;

import us.kbase.auth.TokenFormatException;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.common.taskqueue.JobStatuses;
import us.kbase.common.taskqueue.TaskQueue;
import us.kbase.common.taskqueue.TaskQueueConfig;
import us.kbase.userandjobstate.InitProgress;
import us.kbase.userandjobstate.Results;
import us.kbase.userandjobstate.UserAndJobStateClient;
//END_HEADER

/**
 * <p>Original spec-file module name: KBaseGeneFamilies</p>
 * <pre>
 * </pre>
 */
public class KBaseGeneFamiliesServer extends JsonServerServlet {
    private static final long serialVersionUID = 1L;

    //BEGIN_CLASS_HEADER
    private static TaskQueue taskHolder = null;
    private static TaskQueueConfig taskConfig = null;
    
    private static final String defaultWsUrl = "https://kbase.us/services/ws/";
    private static final String defaultUjsUrl = "https://kbase.us/services/userandjobstate/";
    
    public static final String SYS_PROP_KB_DEPLOYMENT_CONFIG = "KB_DEPLOYMENT_CONFIG";
    
    public static final String CFG_PROP_THREAD_COUNT = "thread.count";
    public static final String CFG_PROP_QUEUE_DB_DIR = "queue.db.dir";
    public static final String CFG_PROP_WS_SRV_URL = "workspace.srv.url";
    public static final String CFG_PROP_JSS_SRV_URL = "jobstatus.srv.url";
    public static final String CFG_PROP_TEMP_DIR = "scratch";
    public static final String CFG_PROP_DATA_DIR = "data.dir";
    
    public static final String SERVICE_VERSION = "1.0.0";
    public static final String SERVICE_DEPLOYMENT_NAME = "gene_families";
    public static final String SERVICE_REGISTERED_NAME = "KBaseGeneFamilies";

    public static synchronized TaskQueueConfig getTaskConfig() throws Exception {
    	if (taskConfig == null) {
    		int threadCount = 1;
    		File queueDbDir = new File(".");
    		String wsUrl = defaultWsUrl;
    		String ujsUrl = defaultUjsUrl;
    		
    		Map<String, String> allConfigProps = loadConfig();
    		if (allConfigProps.containsKey(CFG_PROP_THREAD_COUNT))
    			threadCount = Integer.parseInt(allConfigProps.get(CFG_PROP_THREAD_COUNT));
    		if (allConfigProps.containsKey(CFG_PROP_QUEUE_DB_DIR))
    			queueDbDir = new File(allConfigProps.get(CFG_PROP_QUEUE_DB_DIR));
    		if (allConfigProps.containsKey(CFG_PROP_WS_SRV_URL))
    			wsUrl = allConfigProps.get(CFG_PROP_WS_SRV_URL);
    		if (allConfigProps.containsKey(CFG_PROP_JSS_SRV_URL))
    			ujsUrl = allConfigProps.get(CFG_PROP_JSS_SRV_URL);
    		for (Object key : allConfigProps.keySet())
    			allConfigProps.put(key.toString(), allConfigProps.get(key.toString()));
    		final String finalWsUrl = wsUrl;
    		final String finalUjsUrl = ujsUrl;
    		JobStatuses jobStatuses = new JobStatuses() {
				@Override
				public String createAndStartJob(String token, String status, String desc,
						String initProgressPtype, String estComplete) throws Exception {
    				return createJobClient(finalUjsUrl, token).createAndStartJob(token, status, desc, 
    						new InitProgress().withPtype(initProgressPtype), estComplete);
				}
				@Override
				public void updateJob(String job, String token, String status,
						String estComplete) throws Exception {
    				createJobClient(finalUjsUrl, token).updateJob(job, token, status, estComplete);
				}
				@Override
				public void completeJob(String job, String token, String status,
						String error, String wsUrl, String outRef) throws Exception {
					List<String> refs = new ArrayList<String>();
					if (outRef != null)
						refs.add(outRef);
    				createJobClient(finalUjsUrl, token).completeJob(job, token, status, error, 
    						new Results().withWorkspaceurl(finalWsUrl).withWorkspaceids(refs));
				}
			};
			taskConfig = new TaskQueueConfig(threadCount, queueDbDir, jobStatuses, wsUrl, allConfigProps);
    	}
    	return taskConfig;
    }
    
    public static synchronized TaskQueue getTaskQueue() throws Exception {
    	if (taskHolder == null) {
    		TaskQueueConfig cfg = getTaskConfig();
			taskHolder = new TaskQueue(cfg, new SearchDomainsBuilder(), new ConstructDomainClustersBuilder(),
					new SearchDomainsAndConstructClustersBuilder());
			System.out.println("Initial queue size: " + TaskQueue.getDbConnection(cfg.getQueueDbDir()).collect(
					"select count(*) from " + TaskQueue.QUEUE_TABLE_NAME, new us.kbase.common.utils.DbConn.SqlLoader<Integer>() {
				public Integer collectRow(java.sql.ResultSet rs) throws java.sql.SQLException { return rs.getInt(1); }
			}));
    	}
    	return taskHolder;
    }
    
    private static Map<String, String> loadConfig() throws Exception {
		String configPath = System.getProperty(SYS_PROP_KB_DEPLOYMENT_CONFIG);
		System.out.println(KBaseGeneFamiliesServer.class.getName() + ": Deployment config path was defined: " + configPath);
		return new Ini(new File(configPath)).get(SERVICE_DEPLOYMENT_NAME);
    }
    
	private static UserAndJobStateClient createJobClient(String jobSrvUrl, String token) throws IOException, JsonClientException {
		try {
			UserAndJobStateClient ret = new UserAndJobStateClient(new URL(jobSrvUrl), new AuthToken(token));
			ret.setIsInsecureHttpConnectionAllowed(true);
			ret.setAllSSLCertificatesTrusted(true);
			return ret;
		} catch (TokenFormatException e) {
			throw new JsonClientException(e.getMessage(), e);
		} catch (UnauthorizedException e) {
			throw new JsonClientException(e.getMessage(), e);
		}
	}
    //END_CLASS_HEADER

    public KBaseGeneFamiliesServer() throws Exception {
        super("KBaseGeneFamilies");
        //BEGIN_CONSTRUCTOR
        //END_CONSTRUCTOR
    }

    /**
     * <p>Original spec-file function name: search_domains</p>
     * <pre>
     * </pre>
     * @param   params   instance of type {@link us.kbase.kbasegenefamilies.SearchDomainsParams SearchDomainsParams}
     * @return   parameter "job_id" of String
     */
    @JsonServerMethod(rpc = "KBaseGeneFamilies.search_domains")
    public String searchDomains(SearchDomainsParams params, AuthToken authPart) throws Exception {
        String returnVal = null;
        //BEGIN search_domains
        returnVal = getTaskQueue().addTask(params, authPart.toString());
        //END search_domains
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: version</p>
     * <pre>
     * returns version number of service
     * </pre>
     * @return   parameter "version" of String
     */
    @JsonServerMethod(rpc = "KBaseGeneFamilies.version")
    public String version() throws Exception {
        String returnVal = null;
        //BEGIN version
	returnVal = SERVICE_VERSION;
        //END version
        return returnVal;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: <program> <server_port>");
            return;
        }
        new KBaseGeneFamiliesServer().startupServer(Integer.parseInt(args[0]));
    }
}
