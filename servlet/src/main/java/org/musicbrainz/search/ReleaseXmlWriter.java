/* Copyright (c) 2009 Lukas Lalinsky
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the MusicBrainz project nor the names of the
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.musicbrainz.search;

import com.jthink.brainz.mmd.*;
import org.apache.commons.lang.StringUtils;
import org.apache.lucene.document.Document;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.Locale;

public class ReleaseXmlWriter extends XmlWriter {


    public void write(PrintWriter out, Results results) throws IOException {


        try {

            Marshaller m = context.createMarshaller();
            ObjectFactory of = new ObjectFactory();

            Metadata metadata = of.createMetadata();
            ReleaseList releaseList = of.createReleaseList();

            for (Result result : results.results) {
                Document doc = result.doc;
                Release release = of.createRelease();
                release.setId(doc.get(ReleaseIndexField.RELEASE_ID.getName()));

                release.getOtherAttributes().put(new QName("ext:score"), String.valueOf((int) (result.score * 100)));

                String name = doc.get(ReleaseIndexField.RELEASE.getName());
                if (name != null) {
                    release.setTitle(name);

                }

                String asin = doc.get(ReleaseIndexField.AMAZON_ID.getName());
                if (asin != null) {
                    release.setAsin(asin);

                }

                TextRepresentation tr = of.createTextRepresentation();
                String script = doc.get(ReleaseIndexField.SCRIPT.getName());
                if (script != null) {
                    tr.setScript(script);
                }
                String lang = doc.get(ReleaseIndexField.LANGUAGE.getName());
                if (lang != null) {
                    tr.setLanguage(lang.toUpperCase(Locale.US));
                }

                if (script != null || lang != null) {
                    release.setTextRepresentation(tr);
                }

                String[] countries = doc.getValues(ReleaseIndexField.COUNTRY.getName());
                if (countries.length > 0) {
                    ReleaseEventList eventList = of.createReleaseEventList();
                    String[] dates = doc.getValues(ReleaseIndexField.DATE.getName());
                    String[] labels = doc.getValues(ReleaseIndexField.LABEL.getName());
                    String[] catnos = doc.getValues(ReleaseIndexField.CATALOG_NO.getName());
                    String[] barcodes = doc.getValues(ReleaseIndexField.BARCODE.getName());

                    for (int i = 0; i < countries.length; i++) {
                        Event event = of.createEvent();
                        if (!countries[i].equals("-")) {
                            event.setCountry(StringUtils.upperCase(countries[i]));
                        }

                        if (!dates[i].equals("-")) {
                            event.setDate(dates[i]);
                        }

                        if (!labels[i].equals("-")) {
                            Label label = of.createLabel();
                            label.setName(labels[i]);
                            event.setLabel(label);
                        }

                        if (!catnos[i].equals("-")) {
                            event.setCatalogNumber(catnos[i]);
                        }

                        if (!barcodes[i].equals("-")) {
                            event.setBarcode(barcodes[i]);
                        }

                        eventList.getEvent().add(event);
                    }
                    release.setReleaseEventList(eventList);
                }

                String artistName = doc.get(ReleaseIndexField.ARTIST.getName());
                if (artistName != null) {

                    Artist artist = of.createArtist();
                    artist.setName(artistName);
                    artist.setId(doc.get(ReleaseIndexField.ARTIST_ID.getName()));
                    release.setArtist(artist);
                }

                String discIds = doc.get(ReleaseIndexField.NUM_DISC_IDS.getName());
                if (discIds != null) {
                    DiscList discList = of.createDiscList();
                    discList.setCount(BigInteger.valueOf(Long.parseLong(discIds)));
                    release.setDiscList(discList);
                }

                String tracks = doc.get(ReleaseIndexField.NUM_TRACKS.getName());
                if (tracks != null) {
                    TrackList trackList = of.createTrackList();
                    trackList.setCount(BigInteger.valueOf(Long.parseLong(tracks)));
                    release.setTrackList(trackList);
                }

                releaseList.getRelease().add(release);
            }
            releaseList.setCount(BigInteger.valueOf(results.results.size()));
            releaseList.setOffset(BigInteger.valueOf(results.offset));
            metadata.setReleaseList(releaseList);
            m.marshal(metadata, out);
        }
        catch (JAXBException je) {
            throw new IOException(je);
        }
    }

}
