package us.kbase.kbasegenefamilies;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import us.kbase.common.service.Tuple2;
// import us.kbase.kbasegenefamilies.ConstructDomainClustersBuilder.GenomeAnnotationAlignmentProvider;
import us.kbase.workspace.ListObjectsParams;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.SubObjectIdentity;

/**
   This class appears to build clusters out of DomainAnnotations.
   As of 1/14/15, it does not compile because Clusters are commented
   out of the typespec.

public class SearchDomainsAndConstructClustersBuilder extends DefaultTaskBuilder<SearchDomainsAndConstructClustersParams> {

	private static final String serviceMethod = "search_domains_and_construct_clusters";
	
	public SearchDomainsAndConstructClustersBuilder() {
	}
	
	public SearchDomainsAndConstructClustersBuilder(File tempDir, ObjectStorage objectStorage) {
		this.tempDir = tempDir;
		this.storage = objectStorage;
	}

	@Override
	public Class<SearchDomainsAndConstructClustersParams> getInputDataType() {
		return SearchDomainsAndConstructClustersParams.class;
	}
	
	@Override
	public String getOutRef(SearchDomainsAndConstructClustersParams inputData) {
		return inputData.getOutWorkspace() + "/" + inputData.getOutResultId();
	}
	
	@Override
	public String getTaskDescription() {
		return "Search domains for set of genomes and construct domain clusters for produced domain annotations";
	}
	
	@Override
	public void run(String token, SearchDomainsAndConstructClustersParams inputData, String jobId,
			String outRef) throws Exception {
		List<Tuple2<DomainAnnotation, DomainAlignments>> annsAlns = 
				new ArrayList<Tuple2<DomainAnnotation, DomainAlignments>>();
		List<String> annRefs = new ArrayList<String>();
		String dmsRef = inputData.getDmsRef();
		if (dmsRef == null) {
			if (inputData.getClustersForExtension() == null)
				throw new IllegalStateException("Domain model set is not defined");
			
			DomainClusterSearchResult parent = storage.getObjects(token, Arrays.asList(new ObjectIdentity().withRef(
					inputData.getClustersForExtension()))).get(0).getData().asClassInstance(DomainClusterSearchResult.class);
			dmsRef = parent.getUsedDmsRef();
		}
		DomainSearchTask dst = new DomainSearchTask(tempDir, storage);
		boolean isGenomeAnnotationStoredOutside = inputData.getIsGenomeAnnotationStoredOutside() != null && 
				inputData.getIsGenomeAnnotationStoredOutside() != 0L;
		for (String genomeRef : inputData.getGenomes()) {
			Tuple2<DomainAnnotation, DomainAlignments> annAln = dst.runDomainSearch(token, dmsRef, genomeRef);
			if (isGenomeAnnotationStoredOutside) {
				String genomeObjectName = storage.getObjectSubset(token, Arrays.asList(new SubObjectIdentity().withRef(
						genomeRef).withIncluded(Arrays.asList("id")))).get(0).getInfo().getE2();
				String pref = inputData.getGenomeAnnotationIdPrefix();
				if (pref == null)
					pref = "";
				String suff = inputData.getGenomeAnnotationIdSuffix();
				if (suff == null)
					suff = "";
				String annObjName = pref + genomeObjectName + suff;
				String annRef = SearchDomainsBuilder.saveAnnotation(storage, token, inputData.getOutWorkspace(), 
						annObjName, annAln.getE1(), annAln.getE2(), genomeRef, inputData, serviceMethod);
				annRefs.add(annRef);
			} else {
				annsAlns.add(annAln);
			}
		}
		GenomeAnnotationAlignmentProvider annAlnProv = new GenomeAnnotationAlignmentProvider(
				storage, token, annRefs, annsAlns);
		ConstructDomainClustersBuilder.constructClusters(storage, token, tempDir, jobId, 
				serviceMethod, inputData, 
				annAlnProv, inputData.getDmsRef(), inputData.getClustersForExtension(), 
				inputData.getOutWorkspace(), inputData.getOutResultId(), 
				inputData.getIsDomainClusterDataStoredOutside() != null && 
				inputData.getIsDomainClusterDataStoredOutside() != 0L, 
				inputData.getDomainClusterDataIdPrefix(), 
				inputData.getDomainClusterDataIdSuffix());
	}
}
*/