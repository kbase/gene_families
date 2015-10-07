package us.kbase.kbasegenefamilies;

import java.io.*;
import java.util.*;
import java.net.URL;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;

import us.kbase.common.service.Tuple11;
import us.kbase.common.service.Tuple2;
import us.kbase.common.service.Tuple4;
import us.kbase.common.service.Tuple5;
import us.kbase.common.utils.AlignUtil;
import us.kbase.common.utils.CorrectProcess;
import us.kbase.common.utils.FastaWriter;
import us.kbase.common.utils.RpsBlastParser;
import us.kbase.common.taskqueue.TaskQueueConfig;
import us.kbase.kbasegenefamilies.bin.BinPreparator;
import us.kbase.kbasegenefamilies.util.Utils;
import us.kbase.kbasegenomes.Feature;
import us.kbase.kbasegenomes.Genome;
import us.kbase.workspace.ObjectData;
import us.kbase.workspace.ObjectIdentity;

import org.strbio.IO;
import org.strbio.io.*;
import org.strbio.util.*;
import us.kbase.shock.client.*;

/**
   This class runs a domain search against a single genome, using
   RPS-BLAST and HMMER.  Domain hits are saved in DomainAnnotation
   workspace objects.
*/
public class DomainSearchTask {
    private static String MAX_BLAST_EVALUE = "1e-04";
	
    public static final String domainAnnotationWsType = "KBaseGeneFamilies.DomainAnnotation";
    public static final String domainAlignmentsWsType = "KBaseGeneFamilies.DomainAlignments";
	
    protected File tempDir;
    protected ObjectStorage storage;

    public DomainSearchTask(File tempDir, ObjectStorage objectStorage) {
	this.tempDir = tempDir;
	this.storage = objectStorage;
    }

    /**
       Runs a domain search on a single genome, returning annotations.
       Takes a domainModelSetRef as input, which is searched as
       individual libraries.
    */
    public DomainAnnotation runDomainSearch(String token,
					    String domainModelSetRef,
					    String genomeRef) throws Exception {
	final DomainModelSet dms = storage.getObjects(token, Arrays.asList(new ObjectIdentity().withRef(domainModelSetRef))).get(0).getData().asClassInstance(DomainModelSet.class);
	final Genome genome = storage.getObjects(token, Arrays.asList(new ObjectIdentity().withRef(genomeRef))).get(0).getData().asClassInstance(Genome.class);
	Map<String,String> domainLibMap = dms.getDomainLibs();
	DomainAnnotation rv = null;

	// collect one set of annotations per library
	for (String id : domainLibMap.values()) {
	    DomainLibrary dl = storage.getObjects(token, Arrays.asList(new ObjectIdentity().withRef(id))).get(0).getData().asClassInstance(DomainLibrary.class);
	    DomainAnnotation results = runDomainSearch(genome, genomeRef, domainModelSetRef, dl);

	    // combine all the results into one object
	    if (rv==null)
		rv = results;
	    else 
		combineData(results,rv);
	}
	return rv;
    }

    /**
       calculate statistics to store in metadata, used for quick widget drawing
    */
    public static Map<String,String> getMetadata(DomainAnnotation ann) throws Exception {
	Map<String,String> metadata = new HashMap<String,String>();

	HashSet<String> domains = new HashSet<String>();
	HashSet<String> features = new HashSet<String>();

	Map<String, List<Tuple5<String, Long, Long, Long, Map<String, List<Tuple5<Long, Long, Double, Double, Double>>>>>> data = ann.getData();
	for (String contigID : data.keySet()) {
	    List<Tuple5<String, Long, Long, Long, Map<String, List<Tuple5<Long, Long, Double, Double, Double>>>>> annElements = data.get(contigID);
	    ListIterator<Tuple5<String, Long, Long, Long, Map<String, List<Tuple5<Long, Long, Double, Double, Double>>>>> iterator = annElements.listIterator();
	    while (iterator.hasNext()) {
		Tuple5<String, Long, Long, Long, Map<String, List<Tuple5<Long, Long, Double, Double, Double>>>> elements = iterator.next();
		Map<String, List<Tuple5<Long, Long, Double, Double, Double>>> element = elements.getE5();
		if (element != null) {
		    for (String key : element.keySet()) {
			domains.add(elements.getE1());
			features.add(key);
		    }
		}
	    }
	}

	metadata.put("annotated_domains",""+domains.size());
	metadata.put("annotated_features",""+features.size());

	return metadata;
    }
    
    /**
       combines annotation data from two DomainAnnotation objects;
       must be from the same genome.  Note that this will fail if
       results are in different order, or if two libraries have
       models with the same accessions
    */
    public void combineData(DomainAnnotation source,
			    DomainAnnotation target) throws Exception {
	if (!source.getGenomeRef().equals(target.getGenomeRef()))
	    throw new IllegalArgumentException("Error: DomainAnnotation objects from different genomes can't be combined");
	if (!source.getUsedDmsRef().equals(target.getUsedDmsRef()))
	    throw new IllegalArgumentException("Error: DomainAnnotation objects from different domain model sets can't be combined");
	
	Map<String, List<Tuple5<String, Long, Long, Long, Map<String, List<Tuple5<Long, Long, Double, Double, Double>>>>>> sourceData = source.getData();
	Map<String, List<Tuple5<String, Long, Long, Long, Map<String, List<Tuple5<Long, Long, Double, Double, Double>>>>>> targetData = target.getData();
	for (String contigID : sourceData.keySet()) {
	    List<Tuple5<String, Long, Long, Long, Map<String, List<Tuple5<Long, Long, Double, Double, Double>>>>> sourceElements = sourceData.get(contigID);
	    List<Tuple5<String, Long, Long, Long, Map<String, List<Tuple5<Long, Long, Double, Double, Double>>>>> targetElements = targetData.get(contigID);
	    ListIterator<Tuple5<String, Long, Long, Long, Map<String, List<Tuple5<Long, Long, Double, Double, Double>>>>> sIterator = sourceElements.listIterator();
	    ListIterator<Tuple5<String, Long, Long, Long, Map<String, List<Tuple5<Long, Long, Double, Double, Double>>>>> tIterator = targetElements.listIterator();
	    while (sIterator.hasNext()) {
		Tuple5<String, Long, Long, Long, Map<String, List<Tuple5<Long, Long, Double, Double, Double>>>> sElement = sIterator.next();
		Tuple5<String, Long, Long, Long, Map<String, List<Tuple5<Long, Long, Double, Double, Double>>>> tElement = tIterator.next();
		tElement.getE5().putAll(sElement.getE5());
	    }
	}
    }

    /**
       Runs a domain search on a single genome, returning annotations.
       This works on a single library, but needs metadata (references
       to Genome and DomainModelSet) to populate the annotation object.       
    */
    public DomainAnnotation runDomainSearch(Genome genome,
					    String genomeRef,
					    String domainModelSetRef,
					    DomainLibrary dl) throws Exception {
						
	String genomeName = genome.getScientificName();
	File dbFile = new File(getDomainsDir().getPath()+"/"+dl.getLibraryFiles().get(0).getFileName());
	File fastaFile = File.createTempFile("proteome", ".fasta", tempDir);
	File outFile = null;

	final Map<String,Long> modelNameToLength = new HashMap<String,Long>();

	// save the length of each model, to compute coverage.
	// This replaces modelNameToRefConsensus in Roman's legacy code:
	Map<String,DomainModel> libDomains = dl.getDomains();
	for (String accession : libDomains.keySet()) {
	    DomainModel m = libDomains.get(accession);
	    modelNameToLength.put(accession, m.getLength());
	}

	// make sure we have local copies of all library files
	prepareLibraryFiles(dl);
	
	try {
	    final Map<String, List<Tuple5<String, Long, Long, Long, Map<String, List<Tuple5<Long, Long, Double, Double, Double>>>>>> contig2prots = 
		new TreeMap<String, List<Tuple5<String, Long, Long, Long, Map<String, List<Tuple5<Long, Long, Double, Double, Double>>>>>>();
	    FastaWriter fw = new FastaWriter(fastaFile);
	    int protCount = 0;
	    final Map<Integer, Tuple2<String, Long>> posToContigFeatIndex = new LinkedHashMap<Integer, Tuple2<String, Long>>();
	    Map<String, Tuple2<String, Long>> featIdToContigFeatIndex = new TreeMap<String, Tuple2<String, Long>>();
	    // to work around genomes with missing contigs:
	    HashSet<String> realContigs = new HashSet<String>();
	    // write out each protein sequentially into a FASTA file,
	    // keeping track of its (first) position in the genome
	    try {
		List<Feature> features = genome.getFeatures();
		int pos = -1;
		for (Feature feat : features) {
		    pos++;
		    String seq = feat.getProteinTranslation();
		    if (feat.getLocation().size() < 1)
			continue;
		    Tuple4<String, Long, String, Long> loc = feat.getLocation().get(0);
		    String contigId = loc.getE1();
		    String featId = feat.getId();
		    if ((contigId==null) || (featId==null))
			continue;
		    if (seq != null && !seq.isEmpty()) {
			fw.write("" + pos, seq);
			Tuple2<String, Long> contigFeatIndex = new Tuple2<String, Long>().withE1(contigId);
			posToContigFeatIndex.put(pos, contigFeatIndex);
			featIdToContigFeatIndex.put(featId, contigFeatIndex);
			protCount++;
			realContigs.add(contigId);
		    }
		    List<Tuple5<String, Long, Long, Long, Map<String, List<Tuple5<Long, Long, Double, Double, Double>>>>> prots = contig2prots.get(contigId);
		    if (prots == null) {
			prots = new ArrayList<Tuple5<String, Long, Long, Long, Map<String, List<Tuple5<Long, Long, Double, Double, Double>>>>>();
			contig2prots.put(contigId, prots);
		    }
		    long start = loc.getE3().equals("-") ? (loc.getE2() - loc.getE4() + 1) : loc.getE2();
		    // fake the stop site based on protein length
		    long stop;
		    if (seq != null)
			stop = start - 1 + ((seq.length()+1) * 3);
		    else {
			// correct calculation for end of 1st exon:
			stop = loc.getE3().equals("-") ? loc.getE2() : (loc.getE2() + loc.getE4() - 1);
		    }
		    long dir = loc.getE3().equals("-") ? -1 : +1;
		    prots.add(new Tuple5<String, Long, Long, Long, Map<String, List<Tuple5<Long, Long, Double, Double, Double>>>>()
			      .withE1(feat.getId())
			      .withE2(start)
			      .withE3(stop)
			      .withE4(dir)
			      .withE5(new TreeMap<String, List<Tuple5<Long, Long, Double, Double, Double>>>()));
		}
	    }
	    finally {
		try { fw.close(); } catch (Exception ignore) {}
	    }
	    if (protCount == 0)
		throw new IllegalStateException("There are no protein translations in genome " + genomeName + " (" + genomeRef + ")");

	    // make contig-based indices
	    HashMap<String,Long> contigLengths = new HashMap<String,Long>();

	    // first, get the reported contigs from genome object
	    List<String> genomeContigs = genome.getContigIds();
	    List<Long> genomeContigLengths = genome.getContigLengths();
	    int nContigs = 0;
	    if (genomeContigs != null)
		nContigs = genomeContigs.size();
	    for (int contigPos = 0; contigPos < nContigs; contigPos++) {
		String contigId = genomeContigs.get(contigPos);
		if (!contig2prots.containsKey(contigId))
		    continue;
		long contigLength = 1;
		if ((genomeContigLengths != null) &&
		    (genomeContigLengths.size() > contigPos))
		    contigLength = genomeContigLengths.get(contigPos).longValue();
		contigLengths.put(contigId, new Long(contigLength));
	    }
	    // next, add any missing contigs as length 1
	    for (String contigId : realContigs) {
		if (contigLengths.get(contigId) == null)
		    contigLengths.put(contigId, new Long(1));
	    }

	    // map contigs to "size" (both length and # of proteins)
	    Map<String, Tuple2<Long, Long>> contigSizes = new TreeMap<String, Tuple2<Long, Long>>();
	    for (String contigId : contigLengths.keySet()) {
		List<Tuple5<String, Long, Long, Long, Map<String, List<Tuple5<Long, Long, Double, Double, Double>>>>> prots = contig2prots.get(contigId);
		Collections.sort(prots, new Comparator<Tuple5<String, Long, Long, Long, Map<String, List<Tuple5<Long, Long, Double, Double, Double>>>>>() {
			@Override
			    public int compare(Tuple5<String, Long, Long, Long, Map<String, List<Tuple5<Long, Long, Double, Double, Double>>>> o1,
					       Tuple5<String, Long, Long, Long, Map<String, List<Tuple5<Long, Long, Double, Double, Double>>>> o2) {
			    return Long.compare(o1.getE2(), o2.getE2());
			}
		    });
		long contigLength = contigLengths.get(contigId).longValue();
		contigSizes.put(contigId, new Tuple2<Long, Long>().withE1(contigLength).withE2((long)prots.size()));
		for (int i=0; i<prots.size(); i++) {
		    String featId = prots.get(i).getE1();
		    Tuple2<String, Long> contigFeatIndex = featIdToContigFeatIndex.get(featId);
		    if (contigFeatIndex != null)
			contigFeatIndex.setE2((long)i);
		}
	    }

	    // run the appropriate annotation program
	    String program = dl.getProgram();

	    if (program.equals("rpsblast-2.2.30")) {		
		outFile = runRpsBlast(dbFile, fastaFile);
		RpsBlastParser.processRpsOutput(outFile, new RpsBlastParser.RpsBlastCallback() {
			@Override
			    public void next(String query,
					     String subject,
					     int qstart,
					     String qseq,
					     int sstart,
					     String sseq,
					     String evalue,
					     double bitscore,
					     double ident) throws Exception {
			    Long modelLength = modelNameToLength.get(subject);
			    if (modelLength == null)
				throw new IllegalStateException("Unexpected subject name in prs blast result: " + subject);
			    int featurePos = Integer.parseInt(query);
			    String alignedSeq = AlignUtil.removeGapsFromSubject((int)(modelLength.longValue()), qseq, sstart - 1, sseq);
			    int coverage = 100 - AlignUtil.getGapPercent(alignedSeq);
			    Tuple2<String, Long> contigIdFeatIndex = posToContigFeatIndex.get(featurePos);
			    long featureIndex = contigIdFeatIndex.getE2();
			    Map<String, List<Tuple5<Long, Long, Double, Double, Double>>> domains = contig2prots.get(contigIdFeatIndex.getE1()).get((int)featureIndex).getE5();
			    List<Tuple5<Long, Long, Double, Double, Double>> places = domains.get(subject);
			    if (places == null) {
				places = new ArrayList<Tuple5<Long, Long, Double, Double, Double>>();
				domains.put(subject, places);
			    }
			    int qlen = AlignUtil.removeGaps(qseq).length();
			    places.add(new Tuple5<Long, Long, Double, Double, Double>()
				       .withE1((long)qstart)
				       .withE2((long)qstart + qlen - 1)
				       .withE3(Double.parseDouble(evalue))
				       .withE4(bitscore)
				       .withE5(coverage / 100.0));
			}
		    });
	    }
	    else if (program.equals("hmmscan-3.1b1")) {
		outFile = runHmmer(dbFile, fastaFile);
		BufferedReader infile = IO.openReader(outFile.getPath());
		if (infile==null)
		    throw new Exception("failed to open HMMER output");

		int featurePos = -1;
		while (infile.ready()) {
		    String buffer = infile.readLine();
		    if (buffer==null) {
			infile.close();
			break;
		    }
		    if (buffer.startsWith("Query:"))
			featurePos = StringUtil.atoi(buffer,7);
		    else if (buffer.startsWith("Domain annotation for each model (and alignments):")) {
			buffer = infile.readLine();

			while (buffer.startsWith(">> ")) {
			    Long modelLength = null;
			    String modelName = null;
			    StringTokenizer st = new StringTokenizer(buffer);
			    try {
				st.nextToken();
				modelName = st.nextToken();

				modelLength = modelNameToLength.get(modelName);
				if (modelLength == null)
				    throw new IllegalStateException("No recognized domain in HMMER output line '"+buffer+"'");
			    }
			    catch (NoSuchElementException e) {
				throw new Exception("Format error in HMMER output line '"+buffer+"'");
			    }
			    buffer = infile.readLine();
			    buffer = infile.readLine();
			    buffer = infile.readLine();

			    if (buffer.startsWith(">> "))
				continue;

			    while (buffer.length() > 0) {
				st = new StringTokenizer(buffer.substring(7));
				try {
				    double score = StringUtil.atod(st.nextToken());
				    st.nextToken(); // bias
				    st.nextToken(); // c-evalue

				    String eString = st.nextToken();  // i-evalue
				    // these numbers are 1-offset, for
				    // compatibility with RPS-BLAST parsing code:
				    int hStart = StringUtil.atoi(st.nextToken());
				    int hLength = StringUtil.atoi(st.nextToken()) - hStart + 1;

				    st.nextToken(); // bounds

				    // these numbers are 1-offset, for
				    // compatibility with RPS-BLAST parsing code:
				    int start = StringUtil.atoi(st.nextToken());
				    int l = StringUtil.atoi(st.nextToken()) - start + 1;

				    // save this hit
				    double coverage = (double)hLength / (double)modelLength;
				    Tuple2<String, Long> contigIdFeatIndex = posToContigFeatIndex.get(featurePos);
				    long featureIndex = contigIdFeatIndex.getE2();
				    Map<String, List<Tuple5<Long, Long, Double, Double, Double>>> domains = contig2prots.get(contigIdFeatIndex.getE1()).get((int)featureIndex).getE5();
				    List<Tuple5<Long, Long, Double, Double, Double>> places = domains.get(modelName);
				    if (places == null) {
					places = new ArrayList<Tuple5<Long, Long, Double, Double, Double>>();
					domains.put(modelName, places);
				    }
				    places.add(new Tuple5<Long, Long, Double, Double, Double>()
					       .withE1((long)start)
					       .withE2((long)start + l - 1)
					       .withE3(Double.parseDouble(eString))
					       .withE4(score)
					       .withE5(coverage));
				}
				catch (NoSuchElementException e) {
				    throw new Exception("Format error in HMMER output line '"+buffer+"'");
				}
				buffer = infile.readLine();
			    }
			}
		    }
		}
	    }
	    else
		throw new Exception("unsupported domain search program "+program);
	    
	    DomainAnnotation rv = new DomainAnnotation()
		.withGenomeRef(genomeRef)
		.withUsedDmsRef(domainModelSetRef)
		.withData(contig2prots)
		.withContigToSizeAndFeatureCount(contigSizes)
		.withFeatureToContigAndIndex(featIdToContigFeatIndex);
	    return rv;
	}
	finally {
	    try { fastaFile.delete(); } catch (Exception ignore) {}
	    if (outFile != null)
		try { outFile.delete(); } catch (Exception ignore) {}
	}
    }

    /**
       Formats a library made from a user-defined set of SMP (PSSM)
       files.  Not used by current code, which requires a pre-formatted
       library.
    private void prepareScoremats(String token,
				  String domainModelSetRef,
				  List<File> smpFiles,
				  Map<String,String> modelRefToNameRet,
				  Map<String,Tuple2<String,String>> optionalModelNameToRefConsensus)
	throws Exception {
	DomainModelSet set;
	File domainSetFile = getDomainModelSetJsonFile(domainModelSetRef);
	if (domainSetFile.exists()) {
	    set = Utils.getMapper().readValue(domainSetFile, DomainModelSet.class);
	}
	else {
	    set = storage.getObjects(token, Arrays.asList(new ObjectIdentity().withRef(domainModelSetRef))).get(0).getData().asClassInstance(DomainModelSet.class);
	    Utils.getMapper().writeValue(domainSetFile, set);
	}
	for (String parentRef : set.getParentRefs())
	    prepareScoremats(token, parentRef, smpFiles, modelRefToNameRet, optionalModelNameToRefConsensus);
	List<ObjectIdentity> modelRefCache = new ArrayList<ObjectIdentity>();
	for (String modelRef : set.getDomainModelRefs()) {
	    File modelFile = getDomainModelJsonFile(modelRef);
	    if (modelFile.exists())
		try {
		    DomainModel model = Utils.getMapper().readValue(modelFile, DomainModel.class);
		    prepareModel(modelRef, model, smpFiles, modelRefToNameRet, optionalModelNameToRefConsensus);
		}
		catch (Exception ignore) {
		    modelFile.delete();
		}
	    if (!modelFile.exists())
		modelRefCache.add(new ObjectIdentity().withRef(modelRef));
	    if (modelRefCache.size() >= modelBufferMaxSize)
		cacheDomainModels(token, modelRefCache);
	}
	if (modelRefCache.size() > 0)
	    cacheDomainModels(token, modelRefCache);
	for (String modelRef : set.getDomainModelRefs()) {
	    if (modelRefToNameRet.containsKey(modelRef))
		return;
	    File modelFile = getDomainModelJsonFile(modelRef);
	    DomainModel model = Utils.getMapper().readValue(modelFile, DomainModel.class);
	    prepareModel(modelRef, model, smpFiles, modelRefToNameRet, optionalModelNameToRefConsensus);			
	}
    }
     */

    /**
       Gets a reference to the object, from object info returned by
       the workspace client
    */
    public static String getRefFromObjectInfo(Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>> info) {
	return info.getE7() + "/" + info.getE1() + "/" + info.getE5();
    }

    /**
       Has to do with formatting a domain model library.  Not used
       in current code:
       
    private void cacheDomainModels(String token, List<ObjectIdentity> refs) throws Exception {
	for (ObjectData data : storage.getObjects(token, refs)) {
	    String ref = getRefFromObjectInfo(data.getInfo());
	    DomainModel model = data.getData().asClassInstance(DomainModel.class);
	    File smpOutputFile = getDomainModelSmpFile(ref);
	    saveModelSmpIntoFile(model, smpOutputFile);
	    model.setCddScorematGzipFile("");
	    Utils.getMapper().writeValue(getDomainModelJsonFile(ref), model);
	}
	refs.clear();
    }
    */

    /*
      Doesn't work for now; model libraries must be pre-formatted:
      
    public static void saveModelSmpIntoFile(DomainModel model, File smpOutputFile)
	throws IOException {
	String smpText = Utils.unbase64ungzip(model.getCddScorematGzipFile());
	Writer smpW = new FileWriter(smpOutputFile);
	smpW.write(smpText);
	smpW.close();
    }
    */
	
    public File getBinDir() {
	File ret = new File(tempDir, "bin");
	if (!ret.exists())
	    ret.mkdir();
	return ret;
    }

    private File getDomainsDir() {
	File ret = new File(tempDir, "domains");
	if (!ret.exists())
	    ret.mkdir();
	return ret;
    }

    /**
       gets all the required library files out of shock.  Only
       supports publicly readable libraries for now (private libraries
       cannot currently be uploaded)
    */
    private void prepareLibraryFiles(DomainLibrary dl) throws Exception {
	TaskQueueConfig cfg = KBaseGeneFamiliesServer.getTaskConfig();
	Map<String,String> props = cfg.getAllConfigProps();
	String shockUrl = props.get(KBaseGeneFamiliesServer.CFG_PROP_SHOCK_SRV_URL);
	if (shockUrl==null)
	    shockUrl = KBaseGeneFamiliesServer.defaultShockUrl;
	BasicShockClient client = new BasicShockClient(new URL(shockUrl));
	File dir = getDomainsDir();
	for (Handle h : dl.getLibraryFiles()) {
	    File f = new File(dir.getPath()+"/"+h.getFileName());
	    if (f.canRead())
		continue;
	    OutputStream os = new BufferedOutputStream(new FileOutputStream(f));
	    client.getFile(new ShockNodeId(h.getShockId()),os);
	    os.close();
	}
    }

    private File getFormatRpsDbBin() throws Exception {
	return BinPreparator.prepareBin(getBinDir(), "makeprofiledb");
    }

    private File getRpsBlastBin() throws Exception {
	return BinPreparator.prepareBin(getBinDir(), "rpsblast");
    }

    private File getHmmerBin() throws Exception {
	return BinPreparator.prepareBin(getBinDir(), "hmmscan");
    }
    
    /**
       Formats a RPS-BLAST database, using the same parameters
       used by CDD, according to:
       ftp://ftp.ncbi.nih.gov/pub/mmdb/cdd/README
    */
    public void formatRpsDb(List<File> scorematFiles, File dbFile) throws Exception {
	//File tempInputFile = File.createTempFile("rps", ".db", tempDir);
	PrintWriter pw = new PrintWriter(dbFile);
	for (File f : scorematFiles) {
	    pw.println(f.getAbsolutePath());
	}
	pw.close();
	CorrectProcess cp = null;
	ByteArrayOutputStream errBaos = null;
	Exception err = null;
	String binPath = getFormatRpsDbBin().getAbsolutePath();
	int procExitValue = -1;
	try {
	    Process p = Runtime.getRuntime().exec(CorrectProcess.arr(binPath,
								     "-in", dbFile.getAbsolutePath(), "-threshold", "9.82", 
								     "-scale", "100.0", "-dbtype", "rps", "-index", "true"));
	    errBaos = new ByteArrayOutputStream();
	    cp = new CorrectProcess(p, null, "formatrpsdb", errBaos, "");
	    p.waitFor();
	    errBaos.close();
	    procExitValue = p.exitValue();
	}
	catch(Exception ex) {
	    try{ 
		errBaos.close(); 
	    }
	    catch (Exception ignore) {}
	    try{ 
		if(cp!=null) 
		    cp.destroy(); 
	    }
	    catch (Exception ignore) {}
	    err = ex;
	}
	if (errBaos != null) {
	    String err_text = new String(errBaos.toByteArray());
	    if (err_text.length() > 0)
		err = new Exception("FastTree: " + err_text, err);
	}
	if (procExitValue != 0) {
	    if (err == null)
		err = new IllegalStateException("FastTree exit code: " + procExitValue);
	    throw err;
	}
    }

    /**
       Runs RPS-BLAST on a file
    */
    public File runRpsBlast(File dbFile, File fastaQuery) throws Exception {
	File tempOutputFile = File.createTempFile("rps", ".tab", tempDir);
	CorrectProcess cp = null;
	ByteArrayOutputStream errBaos = null;
	Exception err = null;
	String binPath = getRpsBlastBin().getAbsolutePath();
	int procExitValue = -1;
	FileOutputStream fos = new FileOutputStream(tempOutputFile);
	try {
	    Process p = Runtime.getRuntime().exec(CorrectProcess.arr(binPath,
								     "-db", dbFile.getAbsolutePath(),
								     "-query", fastaQuery.getAbsolutePath(), 
								     "-outfmt", RpsBlastParser.OUTPUT_FORMAT_STRING, 
								     "-evalue", MAX_BLAST_EVALUE));
	    errBaos = new ByteArrayOutputStream();
	    cp = new CorrectProcess(p, fos, "", errBaos, "");
	    p.waitFor();
	    errBaos.close();
	    procExitValue = p.exitValue();
	}
	catch(Exception ex) {
	    try{ 
		errBaos.close(); 
	    }
	    catch (Exception ignore) {}
	    try{ 
		if(cp!=null) 
		    cp.destroy(); 
	    }
	    catch (Exception ignore) {}
	    err = ex;
	}
	finally {
	    try { fos.close(); } catch (Exception ignore) {}
	}
	if (errBaos != null) {
	    String err_text = new String(errBaos.toByteArray());
	    if (err_text.length() > 0)
		err = new Exception("RPS-BLAST: " + err_text, err);
	}
	if (procExitValue != 0) {
	    if (err == null)
		err = new IllegalStateException("RPS-BLAST exit code: " + procExitValue);
	    throw err;
	}
	return tempOutputFile;
    }

    /**
       Runs HMMER on a file
    */
    public File runHmmer(File dbFile, File fastaQuery) throws Exception {
	File tempOutputFile = File.createTempFile("hmmer", ".txt", tempDir);
	CorrectProcess cp = null;
	ByteArrayOutputStream errBaos = null;
	Exception err = null;
	String binPath = getHmmerBin().getAbsolutePath();
	int procExitValue = -1;
	FileOutputStream fos = new FileOutputStream(tempOutputFile);
	try {
	    Process p = Runtime.getRuntime().exec(CorrectProcess.arr(binPath,
								     "--acc",
								     "--notextw",
								     "--cut_tc",
								     dbFile.getAbsolutePath(),
								     fastaQuery.getAbsolutePath()));
	    errBaos = new ByteArrayOutputStream();
	    cp = new CorrectProcess(p, fos, "", errBaos, "");
	    p.waitFor();
	    errBaos.close();
	    procExitValue = p.exitValue();
	}
	catch(Exception ex) {
	    try{ 
		errBaos.close(); 
	    }
	    catch (Exception ignore) {}
	    try{ 
		if(cp!=null) 
		    cp.destroy(); 
	    }
	    catch (Exception ignore) {}
	    err = ex;
	}
	finally {
	    try { fos.close(); } catch (Exception ignore) {}
	}
	if (errBaos != null) {
	    String err_text = new String(errBaos.toByteArray());
	    if (err_text.length() > 0)
		err = new Exception("HMMSCAN: " + err_text, err);
	}
	if (procExitValue != 0) {
	    if (err == null)
		err = new IllegalStateException("HMMSCAN exit code: " + procExitValue);
	    throw err;
	}
	return tempOutputFile;
    }
    
    public void processRpsOutput(File results, RpsBlastParser.RpsBlastCallback callback) throws Exception {
	RpsBlastParser.processRpsOutput(results, callback);
    }
}
