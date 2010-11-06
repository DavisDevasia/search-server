package org.musicbrainz.search.replication;

import org.kohsuke.args4j.Option;
import org.musicbrainz.search.index.IndexOptions;

public class LiveDataFeedIndexOptions extends IndexOptions {

    @Option(name="--replication-repository", usage="The base path of replication packets. (default: http://ftp.musicbrainz.org/pub/musicbrainz/data/replication/)")
    private String repositoryPath = "http://ftp.musicbrainz.org/pub/musicbrainz/data/replication/";
    public String getRepositoryPath() { return repositoryPath; }

	
}
