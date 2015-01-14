The Gene Families service finds domains (from public protein domain
libraries such as COGs and Pfam) in proteins in a user-supplied
genome.

It requires binaries from HMMER and RPS-BLAST, which are retrieved
using the "download_3rd_party_bins.sh" script, and stored in the
JAR file.  This is done automatically as part of the "make" process.

It requires several public PSSM and HMM libraries, which are retrieved
using the "prepare_3rd_party_dbs.sh" script.  The script also formats
the libraries for use by HMMER or RPS-BLAST.  This is done
automatically as part of the "make" process.

The libraries have to be parsed and stored in DomainModelSet objects
in a public KBase workspace called "KBasePublicGeneDomains."  This
needs to be done once every time a new version of the libraries is
desired.  The code to do this is in
us.kbase.kbasegenefamilies.prepare.DomainModelLibPreparation
and needs to be run by a developer with write access to that workspace.
(A token needs to be stored in auth.properties to grant this access.)
Once the libraries are downloaded using the script above, the
developer needs to run "make prepare_library_objects"


