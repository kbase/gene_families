package us.kbase.kbasegenefamilies;

import java.io.File;
import java.util.Arrays;

import us.kbase.common.service.Tuple2;
import us.kbase.common.service.UObject;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.ProvenanceAction;
import us.kbase.workspace.SaveObjectsParams;

public class SearchDomainsBuilder extends DefaultTaskBuilder<SearchDomainsParams> {

    public SearchDomainsBuilder() {
    }
	
    public SearchDomainsBuilder(File tempDir, ObjectStorage objectStorage) {
	this.tempDir = tempDir;
	this.storage = objectStorage;
    }

    @Override
	public Class<SearchDomainsParams> getInputDataType() {
	return SearchDomainsParams.class;
    }
	
    @Override
	public String getOutRef(SearchDomainsParams inputData) {
	return inputData.getOutWorkspace() + "/" + inputData.getOutResultId();
    }
	
    @Override
	public String getTaskDescription() {
	return "Search domains for one genome";
    }
	
    @Override
	public void run(String token, SearchDomainsParams inputData, String jobId,
			String outRef) throws Exception {
	DomainSearchTask dst = new DomainSearchTask(tempDir, storage);
	DomainAnnotation res = dst.runDomainSearch(token, inputData.getDmsRef(), inputData.getGenome());
	saveResult(inputData.getOutWorkspace(), inputData.getOutResultId(), token, res, inputData);
    }
	
    private void saveResult(String ws, String id, String token, DomainAnnotation annRes, 
			    SearchDomainsParams inputData) throws Exception {
	saveAnnotation(storage, token, ws, id, annRes, inputData.getGenome(), inputData, "construct_multiple_alignment");
    }
	
    public static String saveAnnotation(ObjectStorage storage, String token, String ws, String id, 
					DomainAnnotation annRes, String genomeRef, 
					Object inputData, String serviceMethod) throws Exception {
	ObjectSaveData data = new ObjectSaveData().withData(new UObject(annRes))
	    .withType(DomainSearchTask.domainAnnotationWsType)
	    .withProvenance(Arrays.asList(new ProvenanceAction()
					  .withDescription("Domain annotation was calculated with rps-blast/hmmer")
					  .withService(KBaseGeneFamiliesServer.SERVICE_REGISTERED_NAME)
					  .withServiceVer(KBaseGeneFamiliesServer.SERVICE_VERSION)
					  .withMethod(serviceMethod)
					  .withMethodParams(Arrays.asList(new UObject(inputData)))));
	try {
	    long objid = Long.parseLong(id);
	    data.withObjid(objid);
	} catch (NumberFormatException ex) {
	    data.withName(id);
	}
	return DomainSearchTask.getRefFromObjectInfo(storage.saveObjects(token, new SaveObjectsParams().withWorkspace(ws).withObjects(Arrays.asList(data))).get(0));
    }
}
