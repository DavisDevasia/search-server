package org.musicbrainz.search.servlet.mmd2;

import org.musicbrainz.mmd2.Metadata;
import org.musicbrainz.mmd2.ObjectFactory;
import org.musicbrainz.search.servlet.Results;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Take the output from multiple results sets and merged into single output
 *
 */
public class AllWriter extends ResultsWriter {

    Results artistResults;
    Results releaseResults;
    Results releaseGroupResults;
    Results labelResults;
    Results recordingResults;
    Results workResults;

    public AllWriter(Results artistResults,
                     Results releaseResults,
                     Results releaseGroupResults,
                     Results labelResults,
                     Results recordingResults,
                     Results workResults) {
        this.artistResults=artistResults;
        this.releaseResults=releaseResults;
        this.releaseGroupResults=releaseGroupResults;
        this.labelResults=labelResults;
        this.recordingResults=recordingResults;
        this.workResults=workResults;
    }

    //TODO we dont need this method but have to put in because need to subclass from ReleaseWriter
    public void write(Metadata metadata, Results results) throws IOException {
    }


    public Metadata write(Results results) throws IOException {

        //Sort by best max score, then set this as the max score for each entity
        List<Results> resultsList = new ArrayList<Results>();
        resultsList.add(artistResults);
        resultsList.add(releaseResults);
        resultsList.add(releaseGroupResults);
        resultsList.add(labelResults);
        resultsList.add(recordingResults);
        resultsList.add(workResults);
        Collections.sort(resultsList);
        Collections.reverse(resultsList);
        float bestMaxScore=resultsList.get(0).maxScore;
        for(Results next:resultsList)
        {
            System.out.println(next.maxScore);
            next.maxScore=bestMaxScore;
        }
        ObjectFactory of  = new ObjectFactory();
        Metadata metadata = of.createMetadata();

        ArtistWriter artistWriter = new ArtistWriter();
        artistWriter.write(metadata, artistResults);

        ReleaseWriter releaseWriter = new ReleaseWriter();
        releaseWriter.write(metadata, releaseResults);

        ReleaseGroupWriter releaseGroupWriter = new ReleaseGroupWriter();
        releaseGroupWriter.write(metadata, releaseGroupResults);

        LabelWriter labelWriter = new LabelWriter();
        labelWriter.write(metadata, labelResults);

        RecordingWriter recordingWriter = new RecordingWriter();
        recordingWriter.write(metadata, recordingResults);

        WorkWriter workWriter = new WorkWriter();
        workWriter.write(metadata, workResults);
        return metadata;
    }
}
