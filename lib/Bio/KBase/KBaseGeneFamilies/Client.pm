package Bio::KBase::KBaseGeneFamilies::Client;

use JSON::RPC::Client;
use strict;
use Data::Dumper;
use URI;
use Bio::KBase::Exceptions;
use Bio::KBase::AuthToken;

# Client version should match Impl version
# This is a Semantic Version number,
# http://semver.org
our $VERSION = "0.1.0";

=head1 NAME

Bio::KBase::KBaseGeneFamilies::Client

=head1 DESCRIPTION





=cut

sub new
{
    my($class, $url, @args) = @_;
    
    if (!defined($url))
    {
	$url = 'https://kbase.us/services/gene_families';
    }

    my $self = {
	client => Bio::KBase::KBaseGeneFamilies::Client::RpcClient->new,
	url => $url,
    };

    #
    # This module requires authentication.
    #
    # We create an auth token, passing through the arguments that we were (hopefully) given.

    {
	my $token = Bio::KBase::AuthToken->new(@args);
	
	if (!$token->error_message)
	{
	    $self->{token} = $token->token;
	    $self->{client}->{token} = $token->token;
	}
    }

    my $ua = $self->{client}->ua;	 
    my $timeout = $ENV{CDMI_TIMEOUT} || (30 * 60);	 
    $ua->timeout($timeout);
    bless $self, $class;
    #    $self->_validate_version();
    return $self;
}




=head2 search_domains

  $job_id = $obj->search_domains($params)

=over 4

=item Parameter and return types

=begin html

<pre>
$params is a KBaseGeneFamilies.SearchDomainsParams
$job_id is a string
SearchDomainsParams is a reference to a hash where the following keys are defined:
	genome has a value which is a KBaseGeneFamilies.genome_ref
	dms_ref has a value which is a KBaseGeneFamilies.dms_ref
	out_workspace has a value which is a string
	out_result_id has a value which is a string
genome_ref is a string
dms_ref is a string

</pre>

=end html

=begin text

$params is a KBaseGeneFamilies.SearchDomainsParams
$job_id is a string
SearchDomainsParams is a reference to a hash where the following keys are defined:
	genome has a value which is a KBaseGeneFamilies.genome_ref
	dms_ref has a value which is a KBaseGeneFamilies.dms_ref
	out_workspace has a value which is a string
	out_result_id has a value which is a string
genome_ref is a string
dms_ref is a string


=end text

=item Description



=back

=cut

sub search_domains
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 1)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function search_domains (received $n, expecting 1)");
    }
    {
	my($params) = @args;

	my @_bad_arguments;
        (ref($params) eq 'HASH') or push(@_bad_arguments, "Invalid type for argument 1 \"params\" (value was \"$params\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to search_domains:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'search_domains');
	}
    }

    my $result = $self->{client}->call($self->{url}, {
	method => "KBaseGeneFamilies.search_domains",
	params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'search_domains',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return wantarray ? @{$result->result} : $result->result->[0];
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method search_domains",
					    status_line => $self->{client}->status_line,
					    method_name => 'search_domains',
				       );
    }
}



=head2 version

  $version = $obj->version()

=over 4

=item Parameter and return types

=begin html

<pre>
$version is a string

</pre>

=end html

=begin text

$version is a string


=end text

=item Description

returns version number of service

=back

=cut

sub version
{
    my($self, @args) = @_;

# Authentication: none

    if ((my $n = @args) != 0)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function version (received $n, expecting 0)");
    }

    my $result = $self->{client}->call($self->{url}, {
	method => "KBaseGeneFamilies.version",
	params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'version',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return wantarray ? @{$result->result} : $result->result->[0];
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method version",
					    status_line => $self->{client}->status_line,
					    method_name => 'version',
				       );
    }
}



sub version {
    my ($self) = @_;
    my $result = $self->{client}->call($self->{url}, {
        method => "KBaseGeneFamilies.version",
        params => [],
    });
    if ($result) {
        if ($result->is_error) {
            Bio::KBase::Exceptions::JSONRPC->throw(
                error => $result->error_message,
                code => $result->content->{code},
                method_name => 'version',
            );
        } else {
            return wantarray ? @{$result->result} : $result->result->[0];
        }
    } else {
        Bio::KBase::Exceptions::HTTP->throw(
            error => "Error invoking method version",
            status_line => $self->{client}->status_line,
            method_name => 'version',
        );
    }
}

sub _validate_version {
    my ($self) = @_;
    my $svr_version = $self->version();
    my $client_version = $VERSION;
    my ($cMajor, $cMinor) = split(/\./, $client_version);
    my ($sMajor, $sMinor) = split(/\./, $svr_version);
    if ($sMajor != $cMajor) {
        Bio::KBase::Exceptions::ClientServerIncompatible->throw(
            error => "Major version numbers differ.",
            server_version => $svr_version,
            client_version => $client_version
        );
    }
    if ($sMinor < $cMinor) {
        Bio::KBase::Exceptions::ClientServerIncompatible->throw(
            error => "Client minor version greater than Server minor version.",
            server_version => $svr_version,
            client_version => $client_version
        );
    }
    if ($sMinor > $cMinor) {
        warn "New client version available for Bio::KBase::KBaseGeneFamilies::Client\n";
    }
    if ($sMajor == 0) {
        warn "Bio::KBase::KBaseGeneFamilies::Client version is $svr_version. API subject to change.\n";
    }
}

=head1 TYPES



=head2 domain_library_id

=over 4



=item Definition

=begin html

<pre>
a string
</pre>

=end html

=begin text

a string

=end text

=back



=head2 domain_source

=over 4



=item Description

enum: CDD, SMART, Pfam, etc


=item Definition

=begin html

<pre>
a string
</pre>

=end html

=begin text

a string

=end text

=back



=head2 date

=over 4



=item Description

date in ISO 8601 format; e.g., 2014-11-26


=item Definition

=begin html

<pre>
a string
</pre>

=end html

=begin text

a string

=end text

=back



=head2 program_version

=over 4



=item Description

enum: hmmscan-3.1b1, rpsblast-2.2.30


=item Definition

=begin html

<pre>
a string
</pre>

=end html

=begin text

a string

=end text

=back



=head2 model_type

=over 4



=item Description

enum: PSSM, HMM-Family, HMM-Domain, HMM-Repeat, HMM-Motif


=item Definition

=begin html

<pre>
a string
</pre>

=end html

=begin text

a string

=end text

=back



=head2 domain_accession

=over 4



=item Definition

=begin html

<pre>
a string
</pre>

=end html

=begin text

a string

=end text

=back



=head2 DomainModel

=over 4



=item Description

accession - accession of domain model (e.g., PF00244.1, or COG0001)
cdd_id - (optional) in case of CDD it's inner id which is reported by rps-blast program
name - name of domain model
description - description of domain model
length - length of profile
model_type - domain model type
trusted_cutoff - (optional) trusted cutoff of domain model for HMM libraries
@optional cdd_id trusted_cutoff


=item Definition

=begin html

<pre>
a reference to a hash where the following keys are defined:
accession has a value which is a KBaseGeneFamilies.domain_accession
cdd_id has a value which is a string
name has a value which is a string
description has a value which is a string
length has a value which is an int
model_type has a value which is a KBaseGeneFamilies.model_type
trusted_cutoff has a value which is a float

</pre>

=end html

=begin text

a reference to a hash where the following keys are defined:
accession has a value which is a KBaseGeneFamilies.domain_accession
cdd_id has a value which is a string
name has a value which is a string
description has a value which is a string
length has a value which is an int
model_type has a value which is a KBaseGeneFamilies.model_type
trusted_cutoff has a value which is a float


=end text

=back



=head2 Handle

=over 4



=item Definition

=begin html

<pre>
a reference to a hash where the following keys are defined:
file_name has a value which is a string
shock_id has a value which is a string

</pre>

=end html

=begin text

a reference to a hash where the following keys are defined:
file_name has a value which is a string
shock_id has a value which is a string


=end text

=back



=head2 ws_lib_id

=over 4



=item Description

@id ws KBaseGeneFamilies.DomainLibrary


=item Definition

=begin html

<pre>
a string
</pre>

=end html

=begin text

a string

=end text

=back



=head2 DomainLibrary

=over 4



=item Description

id - id of library
source - source of library (e.g., Cog, Pfam, ...)
source_url - ftp/http url where library can be downloaded 
version - version of library release
release_date - release date of library
program - program for running domain search
domain_prefix - prefix of domain accession defining library
dbxref_prefix - url prefix for db-external referencing
library_files - library files stored in Shock storage
domains - domain information


=item Definition

=begin html

<pre>
a reference to a hash where the following keys are defined:
id has a value which is a KBaseGeneFamilies.domain_library_id
source has a value which is a KBaseGeneFamilies.domain_source
source_url has a value which is a string
version has a value which is a string
release_date has a value which is a KBaseGeneFamilies.date
program has a value which is a KBaseGeneFamilies.program_version
domain_prefix has a value which is a string
dbxref_prefix has a value which is a string
library_files has a value which is a reference to a list where each element is a KBaseGeneFamilies.Handle
domains has a value which is a reference to a hash where the key is a KBaseGeneFamilies.domain_accession and the value is a KBaseGeneFamilies.DomainModel

</pre>

=end html

=begin text

a reference to a hash where the following keys are defined:
id has a value which is a KBaseGeneFamilies.domain_library_id
source has a value which is a KBaseGeneFamilies.domain_source
source_url has a value which is a string
version has a value which is a string
release_date has a value which is a KBaseGeneFamilies.date
program has a value which is a KBaseGeneFamilies.program_version
domain_prefix has a value which is a string
dbxref_prefix has a value which is a string
library_files has a value which is a reference to a list where each element is a KBaseGeneFamilies.Handle
domains has a value which is a reference to a hash where the key is a KBaseGeneFamilies.domain_accession and the value is a KBaseGeneFamilies.DomainModel


=end text

=back



=head2 dms_ref

=over 4



=item Description

@id ws KBaseGeneFamilies.DomainModelSet


=item Definition

=begin html

<pre>
a string
</pre>

=end html

=begin text

a string

=end text

=back



=head2 DomainModelSet

=over 4



=item Description

string set_name - name of model set


=item Definition

=begin html

<pre>
a reference to a hash where the following keys are defined:
set_name has a value which is a string
domain_libs has a value which is a reference to a hash where the key is a string and the value is a KBaseGeneFamilies.ws_lib_id
domain_prefix_to_dbxref_url has a value which is a reference to a hash where the key is a string and the value is a string
domain_accession_to_description has a value which is a reference to a hash where the key is a KBaseGeneFamilies.domain_accession and the value is a string

</pre>

=end html

=begin text

a reference to a hash where the following keys are defined:
set_name has a value which is a string
domain_libs has a value which is a reference to a hash where the key is a string and the value is a KBaseGeneFamilies.ws_lib_id
domain_prefix_to_dbxref_url has a value which is a reference to a hash where the key is a string and the value is a string
domain_accession_to_description has a value which is a reference to a hash where the key is a KBaseGeneFamilies.domain_accession and the value is a string


=end text

=back



=head2 genome_ref

=over 4



=item Description

@id ws KBaseGenomes.Genome


=item Definition

=begin html

<pre>
a string
</pre>

=end html

=begin text

a string

=end text

=back



=head2 domain_place

=over 4



=item Description

@id ws KBaseGeneFamilies.DomainCluster
        typedef string domain_cluster_ref;


=item Definition

=begin html

<pre>
a reference to a list containing 5 items:
0: (start_in_feature) an int
1: (stop_in_feature) an int
2: (evalue) a float
3: (bitscore) a float
4: (domain_coverage) a float

</pre>

=end html

=begin text

a reference to a list containing 5 items:
0: (start_in_feature) an int
1: (stop_in_feature) an int
2: (evalue) a float
3: (bitscore) a float
4: (domain_coverage) a float


=end text

=back



=head2 annotation_element

=over 4



=item Description

domain_accession model - reference to domain model
domain_cluster_ref parent_ref - optional reference to parent cluster (containing data 
        describing some common set of genomes)
mapping<genome_ref,list<domain_cluster_element>> data - list of entrances of this domain 
        into different genomes (domain_cluster_element -> ;
        domain_place -> tuple<int start_in_feature,int stop_in_feature,float evalue,
                float bitscore,float domain_coverage>).
ws_alignment_id msa_ref - reference to multiple alignment object where all domain 
        sequences are collected (keys in this MSA object are constructed according to this 
        pattern: <genome_ref>_<feature_id>_<start_in_feature>), field is not set in case
        clusters are stored inside DomainClusterSearchResult object, use 'msas' field of
        DomainClusterSearchResult object instead.
@optional parent_ref
@optional msa_ref
        typedef structure {
domain_accession model;
domain_cluster_ref parent_ref;
mapping<genome_ref,list<domain_cluster_element>> data;
ws_alignment_id msa_ref;
        } DomainCluster;


=item Definition

=begin html

<pre>
a reference to a list containing 5 items:
0: (feature_id) a string
1: (feature_start) an int
2: (feature_stop) an int
3: (feature_dir) an int
4: a reference to a hash where the key is a KBaseGeneFamilies.domain_accession and the value is a reference to a list where each element is a KBaseGeneFamilies.domain_place

</pre>

=end html

=begin text

a reference to a list containing 5 items:
0: (feature_id) a string
1: (feature_start) an int
2: (feature_stop) an int
3: (feature_dir) an int
4: a reference to a hash where the key is a KBaseGeneFamilies.domain_accession and the value is a reference to a list where each element is a KBaseGeneFamilies.domain_place


=end text

=back



=head2 contig_id

=over 4



=item Definition

=begin html

<pre>
a string
</pre>

=end html

=begin text

a string

=end text

=back



=head2 DomainAnnotation

=over 4



=item Description

domain_alignments_ref alignments_ref;


=item Definition

=begin html

<pre>
a reference to a hash where the following keys are defined:
genome_ref has a value which is a KBaseGeneFamilies.genome_ref
used_dms_ref has a value which is a KBaseGeneFamilies.dms_ref
data has a value which is a reference to a hash where the key is a KBaseGeneFamilies.contig_id and the value is a reference to a list where each element is a KBaseGeneFamilies.annotation_element
contig_to_size_and_feature_count has a value which is a reference to a hash where the key is a KBaseGeneFamilies.contig_id and the value is a reference to a list containing 2 items:
0: (size) an int
1: (features) an int

feature_to_contig_and_index has a value which is a reference to a hash where the key is a string and the value is a reference to a list containing 2 items:
0: a KBaseGeneFamilies.contig_id
1: (feature_index) an int


</pre>

=end html

=begin text

a reference to a hash where the following keys are defined:
genome_ref has a value which is a KBaseGeneFamilies.genome_ref
used_dms_ref has a value which is a KBaseGeneFamilies.dms_ref
data has a value which is a reference to a hash where the key is a KBaseGeneFamilies.contig_id and the value is a reference to a list where each element is a KBaseGeneFamilies.annotation_element
contig_to_size_and_feature_count has a value which is a reference to a hash where the key is a KBaseGeneFamilies.contig_id and the value is a reference to a list containing 2 items:
0: (size) an int
1: (features) an int

feature_to_contig_and_index has a value which is a reference to a hash where the key is a string and the value is a reference to a list containing 2 items:
0: a KBaseGeneFamilies.contig_id
1: (feature_index) an int



=end text

=back



=head2 domain_annotation_ref

=over 4



=item Description

@id ws KBaseGeneFamilies.DomainAnnotation


=item Definition

=begin html

<pre>
a string
</pre>

=end html

=begin text

a string

=end text

=back



=head2 SearchDomainsParams

=over 4



=item Description

genome_ref genome - genome for domain annotation process
dms_ref dms_ref - set of domain models that will be searched in defined genome
string out_workspace - output workspace
string out_result_id - id of resulting object of type DomainAnnotation


=item Definition

=begin html

<pre>
a reference to a hash where the following keys are defined:
genome has a value which is a KBaseGeneFamilies.genome_ref
dms_ref has a value which is a KBaseGeneFamilies.dms_ref
out_workspace has a value which is a string
out_result_id has a value which is a string

</pre>

=end html

=begin text

a reference to a hash where the following keys are defined:
genome has a value which is a KBaseGeneFamilies.genome_ref
dms_ref has a value which is a KBaseGeneFamilies.dms_ref
out_workspace has a value which is a string
out_result_id has a value which is a string


=end text

=back



=cut

package Bio::KBase::KBaseGeneFamilies::Client::RpcClient;
use base 'JSON::RPC::Client';

#
# Override JSON::RPC::Client::call because it doesn't handle error returns properly.
#

sub call {
    my ($self, $uri, $obj) = @_;
    my $result;

    if ($uri =~ /\?/) {
       $result = $self->_get($uri);
    }
    else {
        Carp::croak "not hashref." unless (ref $obj eq 'HASH');
        $result = $self->_post($uri, $obj);
    }

    my $service = $obj->{method} =~ /^system\./ if ( $obj );

    $self->status_line($result->status_line);

    if ($result->is_success) {

        return unless($result->content); # notification?

        if ($service) {
            return JSON::RPC::ServiceObject->new($result, $self->json);
        }

        return JSON::RPC::ReturnObject->new($result, $self->json);
    }
    elsif ($result->content_type eq 'application/json')
    {
        return JSON::RPC::ReturnObject->new($result, $self->json);
    }
    else {
        return;
    }
}


sub _post {
    my ($self, $uri, $obj) = @_;
    my $json = $self->json;

    $obj->{version} ||= $self->{version} || '1.1';

    if ($obj->{version} eq '1.0') {
        delete $obj->{version};
        if (exists $obj->{id}) {
            $self->id($obj->{id}) if ($obj->{id}); # if undef, it is notification.
        }
        else {
            $obj->{id} = $self->id || ($self->id('JSON::RPC::Client'));
        }
    }
    else {
        # $obj->{id} = $self->id if (defined $self->id);
	# Assign a random number to the id if one hasn't been set
	$obj->{id} = (defined $self->id) ? $self->id : substr(rand(),2);
    }

    my $content = $json->encode($obj);

    $self->ua->post(
        $uri,
        Content_Type   => $self->{content_type},
        Content        => $content,
        Accept         => 'application/json',
	($self->{token} ? (Authorization => $self->{token}) : ()),
    );
}



1;
