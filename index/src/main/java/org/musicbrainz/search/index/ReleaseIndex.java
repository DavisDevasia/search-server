/* Copyright (c) 2009 Aurélien Mino
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

package org.musicbrainz.search.index;

import com.google.common.base.Strings;
import org.apache.commons.lang.time.StopWatch;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.musicbrainz.mmd2.*;
import org.musicbrainz.search.MbDocument;

import java.io.IOException;
import java.math.BigInteger;
import java.sql.*;
import java.util.*;

public class  ReleaseIndex extends DatabaseIndex {


    public  static final String BARCODE_NONE = "none";
    private StopWatch labelClock = new StopWatch();
    private StopWatch mediumClock = new StopWatch();
    private StopWatch puidClock = new StopWatch();
    private StopWatch artistClock = new StopWatch();
    private StopWatch releaseClock = new StopWatch();
    private StopWatch buildClock = new StopWatch();
    private StopWatch storeClock = new StopWatch();

    public static final String INDEX_NAME = "release";

    public ReleaseIndex(Connection dbConnection) {
        super(dbConnection);
        labelClock.start();
        mediumClock.start();
        puidClock.start();
        artistClock.start();
        releaseClock.start();
        buildClock.start();
        storeClock.start();
        labelClock.suspend();
        mediumClock.suspend();
        puidClock.suspend();
        artistClock.suspend();
        releaseClock.suspend();
        buildClock.suspend();
        storeClock.suspend();
    }

    public ReleaseIndex() {
    }

    public Analyzer getAnalyzer() {
        return DatabaseIndex.getAnalyzer(ReleaseIndexField.class);
    }

    public String getName() {
        return ReleaseIndex.INDEX_NAME;
    }

	@Override
	public IndexField getIdentifierField() {
		return ReleaseIndexField.ID;
	}

    public int getMaxId() throws SQLException {
        Statement st = dbConnection.createStatement();
        ResultSet rs = st.executeQuery("SELECT MAX(id) FROM release");
        rs.next();
        return rs.getInt(1);
    }

    public int getNoOfRows(int maxId) throws SQLException {
        Statement st = dbConnection.createStatement();
        ResultSet rs = st.executeQuery("SELECT count(*) FROM release WHERE id<="+maxId);
        rs.next();
        return rs.getInt(1);
    }

    @Override
    public void init(IndexWriter indexWriter, boolean isUpdater) throws SQLException {

        if(!isUpdater) {
           addPreparedStatement("PUIDS",
                "SELECT release, puid " +
                "FROM   tmp_release_puid " +
                "WHERE  release BETWEEN ? AND ? ");
        }
        else {
            addPreparedStatement("PUIDS",
                "SELECT m.release, p.puid " +
                "FROM medium m " +
                " INNER JOIN track t ON (t.medium=m.id AND m.release BETWEEN ? AND ?) " +
                " INNER JOIN recording_puid rp ON rp.recording = t.recording " +
                " INNER JOIN puid p ON rp.puid=p.id");
        }

        addPreparedStatement("LABELINFOS",
               "SELECT rl.release as releaseId, l.gid as labelId, ln.name as labelName, catalog_number " +
               " FROM release_label rl " +
               "  LEFT JOIN label l ON rl.label=l.id " +
               "  LEFT JOIN label_name ln ON l.name = ln.id " +
               " WHERE rl.release BETWEEN ? AND ?");

        addPreparedStatement("MEDIUMS",
              "SELECT m.release as releaseId, mf.name as format, m.track_count as numTracksOnMedium, count(mc.id) as discidsOnMedium " +
              " FROM medium m " +
              "  LEFT JOIN medium_format mf ON m.format=mf.id " +
              "  LEFT JOIN medium_cdtoc mc ON mc.medium=m.id "  +
              " WHERE m.release BETWEEN ? AND ? " +
              " GROUP BY m.release, m.position, m.id, mf.name, m.track_count" +
              " ORDER BY m.release, m.position, m.id "
        );

        addPreparedStatement("ARTISTCREDITS",
                "SELECT r.id as releaseId, " +
                "  a.artist_credit, " +
                "  a.pos, " +
                "  a.joinphrase, " +
                "  a.artistId,  " +
                "  a.comment, " +
                "  a.artistName, " +
                "  a.artistCreditName, " +
                "  a.artistSortName, " +
                "  a.aliasName " +
                " FROM release AS r " +
                "  INNER JOIN tmp_artistcredit a ON r.artist_credit=a.artist_credit " +
                " WHERE r.id BETWEEN ? AND ?  " +
                " ORDER BY r.id, a.pos");

        addPreparedStatement("SECONDARYTYPES",
                "SELECT rg.name as type, r.id as rid" +
                " FROM tmp_release r " +
                " INNER JOIN release_group_secondary_type_join  rgj " +
                " ON r.rg_id=rgj.release_group " +
                " INNER JOIN release_group_secondary_type rg  " +
                " ON rgj.secondary_type = rg.id " +
                " WHERE r.id BETWEEN ? AND ?");

        addPreparedStatement("RELEASES",
                " SELECT id, gid, name, " +
                "  barcode, " +
                "  type, rg_gid, amazon_asin, " +
                "  language, language_2t, script, status, comment " +
                " FROM tmp_release rl " +
                " WHERE id BETWEEN ? AND ? ");

        addPreparedStatement("RELEASE_COUNTRY",
                " SELECT release, r2.code as country, " +
                        "  date_year, date_month, date_day"+
                        " FROM release_country r1 " +
                        " LEFT JOIN iso_3166_1 r2 " +
                        " ON r1.country = r2.area " +
                        " WHERE release BETWEEN ? AND ? ");

        addPreparedStatement("TAGS",
                "SELECT release_tag.release, tag.name as tag, release_tag.count as count " +
                        " FROM release_tag " +
                        "  INNER JOIN tag ON tag=id " +
                        " WHERE release between ? AND ?");

    }


    public void destroy() throws SQLException {
        try
        {
            super.destroy();
            System.out.println(this.getName()+":Label Queries "    + Utils.formatClock(labelClock));
            System.out.println(this.getName()+":Mediums Queries "  + Utils.formatClock(mediumClock));
            System.out.println(this.getName()+":Artists Queries "  + Utils.formatClock(artistClock));
            System.out.println(this.getName()+":Puids Queries "    + Utils.formatClock(puidClock));
            System.out.println(this.getName()+":Releases Queries " + Utils.formatClock(releaseClock));
            System.out.println(this.getName() + ":Build Index " + Utils.formatClock(buildClock));
            System.out.println(this.getName() + ":Build Store " + Utils.formatClock(storeClock));

        }
        catch(Exception ex)
        {
            ex.printStackTrace();
        }
    }


    /**
     * Load work iswcs
     *
     * @param min
     * @param max
     * @return
     * @throws SQLException
     * @throws IOException
     */
    private Map<Integer, List<String>> loadSecondaryTypes(int min, int max) throws SQLException, IOException {
        Map<Integer, List<String>> secondaryTypes = new HashMap<Integer, List<String>>();
        PreparedStatement st = getPreparedStatement("SECONDARYTYPES");
        st.setInt(1, min);
        st.setInt(2, max);
        ResultSet rs = st.executeQuery();
        while (rs.next()) {
            int releaseId = rs.getInt("rid");

            List<String> list;
            if (!secondaryTypes.containsKey(releaseId)) {
                list = new LinkedList<String>();
                secondaryTypes.put(releaseId, list);
            } else {
                list = secondaryTypes.get(releaseId);
            }
            list.add(rs.getString("type"));
        }
        rs.close();
        return secondaryTypes;
    }

    /**
     * Load Tags
     *
     * @param min
     * @param max
     * @return
     * @throws SQLException
     * @throws IOException
     */
    private Map<Integer, List<Tag>> loadTags(int min, int max) throws SQLException, IOException {

        // Get Tags
        PreparedStatement st = getPreparedStatement("TAGS");
        st.setInt(1, min);
        st.setInt(2, max);
        ResultSet rs = st.executeQuery();
        Map<Integer,List<Tag>> tags = TagHelper.completeTagsFromDbResults(rs,"release");
        rs.close();
        return tags;

    }

    private Map<Integer, List<ReleaseEvent>> loadReleaseEvents(int min, int max) throws SQLException, IOException {

        // Get Release Country
        PreparedStatement st = getPreparedStatement("RELEASE_COUNTRY");
        st.setInt(1, min);
        st.setInt(2, max);
        ResultSet rs = st.executeQuery();
        Map<Integer,List<ReleaseEvent>> releaseEvents = ReleaseEventHelper.completeReleaseEventsFromDbResults(rs,"release");
        rs.close();
        return releaseEvents;

    }

    public void indexData(IndexWriter indexWriter, int min, int max) throws SQLException, IOException {

        Map<Integer, List<Tag>> tags                        = loadTags(min, max);
        Map<Integer, List<ReleaseEvent>> releaseEvents      = loadReleaseEvents(min, max);

        //A particular release can have multiple catalog nos, labels when released as an imprint, typically used
        //by major labels
        labelClock.resume();
        Map<Integer, List<List<String>>> labelInfo = new HashMap<Integer, List<List<String>>>();
        PreparedStatement st = getPreparedStatement("LABELINFOS");
        st.setInt(1, min);
        st.setInt(2, max);
        ResultSet rs = st.executeQuery();
        while (rs.next()) {
            int releaseId = rs.getInt("releaseId");
            List<List<String>> list;
            if (!labelInfo.containsKey(releaseId)) {
                list = new LinkedList<List<String>>();
                labelInfo.put(releaseId, list);
            } else {
                list = labelInfo.get(releaseId);
            }
            List<String> entry = new ArrayList<String>(3);
            entry.add(rs.getString("labelId"));
            entry.add(rs.getString("labelName"));
            entry.add(rs.getString("catalog_number"));
            list.add(entry);
        }
        rs.close();
        labelClock.suspend();


        //Medium, NumTracks a release can be released on multiple mediums, and possibly involving different mediums,
        //i.e a release is on CD with
        //a special 7" single included. We also need total tracks and discs ids per medium
        mediumClock.resume();
        Map<Integer, List<List<String>>> mediums = new HashMap<Integer, List<List<String>>>();
        st = getPreparedStatement("MEDIUMS");
        st.setInt(1, min);
        st.setInt(2, max);
        rs = st.executeQuery();
        while (rs.next()) {
            int releaseId = rs.getInt("releaseId");
            List<List<String>> list;
            if (!mediums.containsKey(releaseId)) {
                list = new LinkedList<List<String>>();
                mediums.put(releaseId, list);
            } else {
                list = mediums.get(releaseId);
            }
            List<String> entry = new ArrayList<String>(3);
            entry.add(rs.getString("format"));
            entry.add(String.valueOf(rs.getInt("numTracksOnMedium")));
            entry.add(String.valueOf(rs.getInt("discIdsOnMedium")));
            list.add(entry);
        }
        rs.close();
        mediumClock.suspend();


        //Puids
        Map<Integer, List<String>> puidWrapper = new HashMap<Integer, List<String>>();
        puidClock.resume();
        st = getPreparedStatement("PUIDS");
        st.setInt(1, min);
        st.setInt(2, max);
        rs = st.executeQuery();
        while (rs.next()) {
            int releaseId = rs.getInt("release");
            List<String> list;
            if (!puidWrapper.containsKey(releaseId)) {
                list = new LinkedList<String>();
                puidWrapper.put(releaseId, list);
            } else {
                list = puidWrapper.get(releaseId);
            }
            String puid = new String(rs.getString("puid"));
            list.add(puid);
        }
        rs.close();
        puidClock.suspend();



        //Artist Credits
        artistClock.resume();
        st = getPreparedStatement("ARTISTCREDITS");
        st.setInt(1, min);
        st.setInt(2, max);
        rs = st.executeQuery();
        Map<Integer, ArtistCreditWrapper> artistCredits
                = ArtistCreditHelper.completeArtistCreditFromDbResults
                     (rs,
                      "releaseId",
                      "artist_Credit",
                      "artistId",
                      "artistName",
                      "artistSortName",
                      "comment",
                      "joinphrase",
                      "artistCreditName",
                      "aliasName");
        rs.close();
        artistClock.suspend();

        Map<Integer, List<String>> secondaryTypes = loadSecondaryTypes(min, max);
        st = getPreparedStatement("RELEASES");
        st.setInt(1, min);
        st.setInt(2, max);
        releaseClock.resume();
        rs = st.executeQuery();
        releaseClock.suspend();
        while (rs.next()) {
            indexWriter.addDocument(documentFromResultSet(rs, secondaryTypes, tags, releaseEvents, labelInfo, mediums, puidWrapper, artistCredits));
        }
        rs.close();
    }

    public Document documentFromResultSet(ResultSet rs,
                                          Map<Integer, List<String>> secondaryTypes,
                                          Map<Integer,List<Tag>> tags,
                                          Map<Integer,List<ReleaseEvent>> releaseEvents,
                                          Map<Integer,List<List<String>>> labelInfo,
                                          Map<Integer,List<List<String>>> mediums,
                                          Map<Integer, List<String>> puids,
                                          Map<Integer, ArtistCreditWrapper> artistCredits) throws SQLException {
        buildClock.resume();

        MbDocument doc = new MbDocument();
        ObjectFactory of = new ObjectFactory();
        Release release = of.createRelease();

        int id = rs.getInt("id");
        doc.addField(ReleaseIndexField.ID, id);
        doc.addField(ReleaseIndexField.RELEASE_ID, rs.getString("gid"));
        release.setId(rs.getString("gid"));

        String name = rs.getString("name");
        doc.addField(ReleaseIndexField.RELEASE, name );
        doc.addField(ReleaseIndexField.RELEASE_ACCENT, name);
        release.setTitle(name);

        String primaryType = rs.getString("type");
        doc.addFieldOrUnknown(ReleaseIndexField.PRIMARY_TYPE, primaryType);
        ReleaseGroup rg = of.createReleaseGroup();
        release.setReleaseGroup(rg);
        if (primaryType!=null && !primaryType.isEmpty()){
            release.getReleaseGroup().setType(primaryType);
        }

        if (secondaryTypes.containsKey(id)) {
            SecondaryTypeList stl = of.createSecondaryTypeList();
            for (String secondaryType : secondaryTypes.get(id)) {
                doc.addField(ReleaseIndexField.SECONDARY_TYPE, secondaryType);
                stl.getSecondaryType().add(secondaryType);
            }
            release.getReleaseGroup().setSecondaryTypeList(stl);
        }

        String type = ReleaseGroupHelper.calculateOldTypeFromPrimaryType(primaryType, secondaryTypes.get(id));
        doc.addFieldOrUnknown(ReleaseIndexField.TYPE, type);
        if (type!=null && !type.isEmpty()) {
            release.getReleaseGroup().setType(type);
        }

        String releaseGroupId = rs.getString("rg_gid");
        doc.addNonEmptyField(ReleaseIndexField.RELEASEGROUP_ID, releaseGroupId);
        release.getReleaseGroup().setId(releaseGroupId);

        String status = rs.getString("status");
        doc.addFieldOrUnknown(ReleaseIndexField.STATUS, status);
        if (status!=null && !status.isEmpty()) {
            release.setStatus(type);
        }

        String barcode = rs.getString("barcode");
        if(barcode==null) {
            doc.addField(ReleaseIndexField.BARCODE,Index.NO_VALUE);
        }
        else if(barcode.equals("")) {
            doc.addField(ReleaseIndexField.BARCODE, BARCODE_NONE);
            release.setBarcode(barcode);
        }
        else {
            doc.addField(ReleaseIndexField.BARCODE,barcode);
            release.setBarcode(barcode);
        }

        String asin = rs.getString("amazon_asin");
        doc.addFieldOrNoValue(ReleaseIndexField.AMAZON_ID, asin);
        if (asin!=null && !asin.isEmpty()) {
            release.setAsin(asin);
        }

        boolean isScriptOrLanguage = false;
        TextRepresentation tr = of.createTextRepresentation();
        String script = rs.getString("script");
        doc.addFieldOrUnknown(ReleaseIndexField.SCRIPT, script) ;
        if (script!=null && !script.isEmpty()) {
            tr.setScript(script);
            isScriptOrLanguage=true;
        }

        String lang3= rs.getString("language");
        String lang2= rs.getString("language_2t");
        if(lang3!=null)
        {
            doc.addFieldOrUnknown(ReleaseIndexField.LANGUAGE, lang3);
            tr.setLanguage(lang3.toLowerCase(Locale.US));
            isScriptOrLanguage=true;
        }
        else if(lang2!=null)
        {
            doc.addFieldOrUnknown(ReleaseIndexField.LANGUAGE, lang2);
            tr.setLanguage(lang2.toLowerCase(Locale.US));
            isScriptOrLanguage=true;
        }
        else {
            doc.addFieldOrUnknown(ReleaseIndexField.LANGUAGE, null);
        }
        if(isScriptOrLanguage) {
            release.setTextRepresentation(tr);
        }

        String comment = rs.getString("comment");
        doc.addFieldOrNoValue(ReleaseIndexField.COMMENT, comment);
        if (comment!=null && !comment.isEmpty()) {
            release.setDisambiguation(comment);
        }

        if (labelInfo.containsKey(id)) {
            LabelInfoList labelInfoList = of.createLabelInfoList();
            for (List<String> entry : labelInfo.get(id)) {
                LabelInfo li = of.createLabelInfo();
                Label label = of.createLabel();
                li.setLabel(label);
                labelInfoList.getLabelInfo().add(li);
                doc.addFieldOrNoValue(ReleaseIndexField.LABEL_ID, entry.get(0));
                if(entry.get(0)!=null && !entry.get(0).isEmpty()) {
                    label.setId(entry.get(0));
                }

                doc.addFieldOrNoValue(ReleaseIndexField.LABEL, entry.get(1));
                if(entry.get(1)!=null && !entry.get(1).isEmpty()) {
                    label.setName(entry.get(1));
                }

                doc.addFieldOrUnknown(ReleaseIndexField.CATALOG_NO, entry.get(2));
                if(entry.get(2)!=null && !entry.get(2).isEmpty()) {
                    li.setCatalogNumber(entry.get(2));
                }
            }
            release.setLabelInfoList(labelInfoList);
        }
        else {
            doc.addFieldOrNoValue(ReleaseIndexField.LABEL, null);
            doc.addFieldOrNoValue(ReleaseIndexField.CATALOG_NO, null);
        }

        int trackCount = 0;
        int discCount = 0;
        int mediumCount = 0;
        if (mediums.containsKey(id)) {
            MediumList mediumList = of.createMediumList();
            for (List<String> entry : mediums.get(id)) {
                Medium medium = of.createMedium();

                String mediumFormat = entry.get(0);
                doc.addFieldOrNoValue(ReleaseIndexField.FORMAT, mediumFormat);
                if(mediumFormat!=null && !mediumFormat.isEmpty()) {
                    medium.setFormat(mediumFormat);
                }

                //Num of tracks on the Medium
                int numTracksOnMedium = Integer.parseInt(entry.get(1));
                doc.addNumericField(ReleaseIndexField.NUM_TRACKS_MEDIUM, numTracksOnMedium);
                org.musicbrainz.mmd2.Medium.TrackList trackList = of.createMediumTrackList();
                trackList.setCount(BigInteger.valueOf(numTracksOnMedium));
                trackCount += numTracksOnMedium;
                medium.setTrackList(trackList);

                //Num of discids associated with medium
                int numDiscsOnMedium = Integer.parseInt(entry.get(2));
                doc.addNumericField(ReleaseIndexField.NUM_DISCIDS_MEDIUM, numDiscsOnMedium);
                discCount += numDiscsOnMedium;
                mediumCount++;
                DiscList discList = of.createDiscList();
                discList.setCount(BigInteger.valueOf(numDiscsOnMedium));
                medium.setDiscList(discList);

                mediumList.getMedium().add(medium);

            }

            //Num of mediums on the release
            doc.addNumericField(ReleaseIndexField.NUM_MEDIUMS, mediumCount);

            //Num Tracks over the whole release
            doc.addNumericField(ReleaseIndexField.NUM_TRACKS, trackCount);
            mediumList.setTrackCount(BigInteger.valueOf(trackCount));

            //Num Discs Ids over the whole release
            doc.addNumericField(ReleaseIndexField.NUM_DISCIDS, discCount);
        }
        else
        {
            //No mediums on release
            doc.addNumericField(ReleaseIndexField.NUM_MEDIUMS, 0);
        }

        if (puids.containsKey(id)) {
            for (String puid : puids.get(id)) {
                 doc.addField(ReleaseIndexField.PUID, puid);
            }
        }


        ArtistCreditWrapper ac = artistCredits.get(id);
        if(ac!=null) {
            ArtistCreditHelper.buildIndexFieldsOnlyFromArtistCredit
                   (doc,
                    ac.getArtistCredit(),
                    ReleaseIndexField.ARTIST,
                    ReleaseIndexField.ARTIST_NAMECREDIT,
                    ReleaseIndexField.ARTIST_ID,
                    ReleaseIndexField.ARTIST_NAME);
            release.setArtistCredit(ac.getArtistCredit());
        }
        else {
            System.out.println("\nNo artist credit found for release:"+rs.getString("gid"));
        }

        if (tags.containsKey(id)) {
            TagList tagList = of.createTagList();
            for (Tag nextTag : tags.get(id)) {
                Tag tag = of.createTag();
                doc.addField(ReleaseIndexField.TAG, nextTag.getName());
                tag.setName(nextTag.getName());
                tag.setCount(new BigInteger(nextTag.getCount().toString()));
                tagList.getTag().add(tag);
            }
            release.setTagList(tagList);
        }

        if (releaseEvents.containsKey(id)) {
            ReleaseEventList rel = of.createReleaseEventList();
            for (ReleaseEvent releaseEvent : releaseEvents.get(id)) {

                String nextCountry  = releaseEvent.getCountry();
                doc.addFieldOrUnknown(ReleaseIndexField.COUNTRY,nextCountry);

                String nextDate     = releaseEvent.getDate();
                doc.addFieldOrUnknown(ReleaseIndexField.DATE, nextDate );
                rel.getReleaseEvent().add(releaseEvent);
            }
            release.setReleaseEventList(rel);

            //backwards compatibility
            ReleaseEvent firstReleaseEvent = rel.getReleaseEvent().get(0);
            if (!Strings.isNullOrEmpty(firstReleaseEvent.getCountry())) {
                release.setCountry(firstReleaseEvent.getCountry());
            }
            if (!Strings.isNullOrEmpty(firstReleaseEvent.getDate())) {
                release.setDate(firstReleaseEvent.getDate());
            }
        }
        else {
            doc.addFieldOrUnknown(ReleaseIndexField.COUNTRY, null);
            doc.addFieldOrUnknown(ReleaseIndexField.DATE, null );
        }
        buildClock.suspend();
        storeClock.resume();
        String json = MMDSerializer.serialize(release);
        doc.addField(ReleaseIndexField.RELEASE_STORE, json);
        storeClock.suspend();
        return doc.getLuceneDocument();
    }

}