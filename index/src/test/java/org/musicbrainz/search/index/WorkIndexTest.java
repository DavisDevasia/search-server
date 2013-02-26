package org.musicbrainz.search.index;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.RAMDirectory;
import org.junit.Test;
import org.musicbrainz.mmd2.RelationList;

import java.sql.Statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class WorkIndexTest extends AbstractIndexTest {

    public void setUp() throws Exception {
        super.setup();
    }

    private void createIndex(RAMDirectory ramDir) throws Exception {
        IndexWriter writer = createIndexWriter(ramDir,WorkIndexField.class);
        WorkIndex wi = new WorkIndex(conn);
        CommonTables ct = new CommonTables(conn,  wi.getName());
        ct.createTemporaryTables(false);

        wi.init(writer, false);
        wi.addMetaInformation(writer);
        wi.indexData(writer, 0, Integer.MAX_VALUE);
        wi.destroy();
        writer.close();

    }

    private void addWorkOne() throws Exception {

        Statement stmt = conn.createStatement();

        stmt.addBatch("INSERT INTO artist_name (id, name) VALUES (1, 'Echo & The Bunnymen')");
        stmt.addBatch("INSERT INTO artist_name (id, name) VALUES (2, 'Echo and The Bunnymen')");

        stmt.addBatch("INSERT INTO artist (id, gid, name, sort_name, comment)" +
                " VALUES (16153, 'ccd4879c-5e88-4385-b131-bf65296bf245', 1, 2, 'a comment')");
        stmt.addBatch("INSERT INTO artist_credit (id, name, artist_count, ref_count) VALUES (1, 1, 1, 1)");
        stmt.addBatch("INSERT INTO artist_credit_name (artist_credit, position, artist, name) " +
                " VALUES (1, 0, 16153, 1)");

        stmt.addBatch("INSERT INTO work_name (id, name) VALUES (1, 'Work')");
        stmt.addBatch("INSERT INTO work_name (id, name) VALUES (2, 'Play')");
        stmt.addBatch("INSERT INTO work (id, gid, name, artist_credit, comment, language)" +
                " VALUES (1, 'a539bb1e-f2e1-4b45-9db8-8053841e7503', 1, 1,  'demo', 1)");
        stmt.addBatch("INSERT INTO language (id, iso_code_3, iso_code_2t, iso_code_2b, iso_code_2, name, frequency) " +
                " VALUES (1, 'eng', 'eng', 'eng', 'en', 'English', 1)");
        stmt.addBatch("INSERT INTO work_alias (work, name) VALUES (1, 2)");
        stmt.addBatch("INSERT INTO iswc(work,iswc) VALUES(1,'T-101779304-1')");

        stmt.addBatch("INSERT INTO tag (id, name, ref_count) VALUES (1, 'Classical', 2);");
        stmt.addBatch("INSERT INTO work_tag (work, tag, count) VALUES (1, 1, 10)");
        stmt.addBatch("INSERT INTO l_artist_work(id, link, entity0, entity1) VALUES (1, 1, 16153, 1)");
        stmt.addBatch("INSERT INTO link(id, link_type)VALUES (1, 1)");
        stmt.addBatch("INSERT INTO link_type(id,name) VALUES (1, 'composer')");

        stmt.executeBatch();
        stmt.close();
    }

    private void addWorkTwo() throws Exception {

        Statement stmt = conn.createStatement();

        stmt.addBatch("INSERT INTO artist_name (id, name) VALUES (1, 'Echo & The Bunnymen')");
        stmt.addBatch("INSERT INTO artist_name (id, name) VALUES (2, 'Echo and The Bunnymen')");

        stmt.addBatch("INSERT INTO artist (id, gid, name, sort_name, comment)" +
                " VALUES (16153, 'ccd4879c-5e88-4385-b131-bf65296bf245', 1, 2, 'a comment')");

        stmt.addBatch("INSERT INTO artist_credit (id, name, artist_count, ref_count) VALUES (1, 1, 1, 1)");
        stmt.addBatch("INSERT INTO artist_credit_name (artist_credit, position, artist, name) " +
                " VALUES (1, 0, 16153, 1)");

        stmt.addBatch("INSERT INTO work_name (id, name) VALUES (1, 'Work')");
        stmt.addBatch("INSERT INTO work_type (id, name) VALUES (1, 'Opera')");
        
        stmt.addBatch("INSERT INTO work (id, gid, name, artist_credit, type)" +
                " VALUES (1, 'a539bb1e-f2e1-4b45-9db8-8053841e7503', 1, 1, 1)");
        stmt.addBatch("INSERT INTO iswc(work,iswc) VALUES(1,'T-101779304-1')");
        stmt.addBatch("INSERT INTO iswc(work,iswc) VALUES(1,'B-101779304-1')");


        stmt.addBatch("INSERT INTO l_artist_work(id, link, entity0, entity1) VALUES (1, 1, 16153, 1);");
        stmt.addBatch("INSERT INTO link(id, link_type)VALUES (1, 1)");
        stmt.addBatch("INSERT INTO link_type(id,name) VALUES (1, 'composer')");
        stmt.addBatch("INSERT INTO link_attribute(link, attribute_type, created) VALUES (1, 1, null)");
       stmt.addBatch("INSERT INTO link_attribute_type( id, parent, root, child_order, gid, name, description, last_updated) " +
                "VALUES (1, 1, 1, 1, 'ccd4879c-5e88-4385-b131-bf65296bf245', 'additional', null,null);");

        ;

        stmt.executeBatch();
        stmt.close();
    }

    @Test
    public void testIndexWork() throws Exception {

        addWorkOne();
        RAMDirectory ramDir = new RAMDirectory();
        createIndex(ramDir);
        IndexReader ir = DirectoryReader.open(ramDir);
        assertEquals(2, ir.numDocs());
        {
            Document doc = ir.document(1);
            assertEquals(1, doc.getFields(WorkIndexField.WORK.getName()).length);
            assertEquals("Work", doc.getField(WorkIndexField.WORK.getName()).stringValue());
            assertEquals(1, doc.getFields(WorkIndexField.ISWC.getName()).length);
            assertEquals("T-101779304-1", doc.getField(WorkIndexField.ISWC.getName()).stringValue());
            assertEquals(1, doc.getFields(WorkIndexField.TYPE.getName()).length);
            assertEquals("-", doc.getField(WorkIndexField.TYPE.getName()).stringValue());
            assertEquals(1, doc.getFields(WorkIndexField.ARTIST_RELATION.getName()).length);
            ir.close();
        }
    }

    @Test
    public void testIndexWorkWithoutType() throws Exception {

        addWorkOne();
        RAMDirectory ramDir = new RAMDirectory();
        createIndex(ramDir);
        IndexReader ir = DirectoryReader.open(ramDir);
        assertEquals(2, ir.numDocs());
        {
            Document doc = ir.document(1);
            assertEquals(1, doc.getFields(WorkIndexField.TYPE.getName()).length);
            assertEquals("-", doc.getField(WorkIndexField.TYPE.getName()).stringValue());
            ir.close();
        }
    }

    @Test
    public void testIndexWorkWithType() throws Exception {

        addWorkTwo();
        RAMDirectory ramDir = new RAMDirectory();
        createIndex(ramDir);
        IndexReader ir = DirectoryReader.open(ramDir);
        assertEquals(2, ir.numDocs());
        {
            Document doc = ir.document(1);
            assertEquals(1, doc.getFields(WorkIndexField.TYPE.getName()).length);
            assertEquals("Opera", doc.getField(WorkIndexField.TYPE.getName()).stringValue());
            ir.close();
        }
    }

    @Test
    public void testIndexWorkWithComment() throws Exception {

        addWorkOne();
        RAMDirectory ramDir = new RAMDirectory();
        createIndex(ramDir);
        IndexReader ir = DirectoryReader.open(ramDir);
        assertEquals(2, ir.numDocs());
        {
            Document doc = ir.document(1);
            assertEquals(1, doc.getFields(WorkIndexField.COMMENT.getName()).length);
            assertEquals("demo", doc.getField(WorkIndexField.COMMENT.getName()).stringValue());
            ir.close();
        }
    }

    @Test
    public void testIndexWorkWithLanguage() throws Exception {

        addWorkOne();
        RAMDirectory ramDir = new RAMDirectory();
        createIndex(ramDir);
        IndexReader ir = DirectoryReader.open(ramDir);
        assertEquals(2, ir.numDocs());
        {
            Document doc = ir.document(1);
            assertEquals(1, doc.getFields(WorkIndexField.LYRICS_LANG.getName()).length);
            assertEquals("eng", doc.getField(WorkIndexField.LYRICS_LANG.getName()).stringValue());
            ir.close();
        }
    }

    @Test
    public void testIndexWorkWithMultipleIswc() throws Exception {

        addWorkTwo();
        RAMDirectory ramDir = new RAMDirectory();
        createIndex(ramDir);
        IndexReader ir = DirectoryReader.open(ramDir);
        assertEquals(2, ir.numDocs());
        {
            Document doc = ir.document(1);
            assertEquals(2, doc.getFields(WorkIndexField.ISWC.getName()).length);
            assertEquals("T-101779304-1", doc.getFields(WorkIndexField.ISWC.getName())[0].stringValue());
            assertEquals("B-101779304-1", doc.getFields(WorkIndexField.ISWC.getName())[1].stringValue());
            ir.close();
        }
    }

    @Test
    public void testIndexWorkWithArtistRelation() throws Exception {

        addWorkOne();
        RAMDirectory ramDir = new RAMDirectory();
        createIndex(ramDir);
        IndexReader ir = DirectoryReader.open(ramDir);
        assertEquals(2, ir.numDocs());
        {
            Document doc = ir.document(1);
            RelationList rc = (RelationList) MMDSerializer
                    .unserialize(doc.get(WorkIndexField.ARTIST_RELATION.getName()), RelationList.class);
            assertNotNull(rc);
            assertEquals("ccd4879c-5e88-4385-b131-bf65296bf245",rc.getRelation().get(0).getArtist().getId());
            assertEquals("Echo & The Bunnymen",rc.getRelation().get(0).getArtist().getName());
            assertEquals("composer",rc.getRelation().get(0).getType());

            ir.close();
        }
    }

    @Test
    public void testIndexWorkWithArtistRelationAttribute() throws Exception {

        addWorkTwo();
        RAMDirectory ramDir = new RAMDirectory();
        createIndex(ramDir);
        IndexReader ir = DirectoryReader.open(ramDir);
        assertEquals(2, ir.numDocs());
        {
            Document doc = ir.document(1);
            RelationList rc = (RelationList) MMDSerializer
                    .unserialize(doc.get(WorkIndexField.ARTIST_RELATION.getName()), RelationList.class);
            assertNotNull(rc);
            assertEquals("ccd4879c-5e88-4385-b131-bf65296bf245",rc.getRelation().get(0).getArtist().getId());
            assertEquals("Echo & The Bunnymen",rc.getRelation().get(0).getArtist().getName());
            assertEquals("composer",rc.getRelation().get(0).getType());
            assertEquals("additional",rc.getRelation().get(0).getAttributeList().getAttribute().get(0));
            ir.close();
        }
    }


    @Test
    public void testIndexWorkWithAlias() throws Exception {

        addWorkOne();
        RAMDirectory ramDir = new RAMDirectory();
        createIndex(ramDir);
        IndexReader ir = DirectoryReader.open(ramDir);
        assertEquals(2, ir.numDocs());
        {
            Document doc = ir.document(1);
            assertEquals(1, doc.getFields(WorkIndexField.ALIAS.getName()).length);
            assertEquals("Play", doc.getField(WorkIndexField.ALIAS.getName()).stringValue());
            ir.close();
        }
    }

    @Test
    public void testIndexWorkWithNoAlias() throws Exception {

        addWorkTwo();
        RAMDirectory ramDir = new RAMDirectory();
        createIndex(ramDir);
        IndexReader ir = DirectoryReader.open(ramDir);
        assertEquals(2, ir.numDocs());
        {
            Document doc = ir.document(1);
            assertEquals(0, doc.getFields(WorkIndexField.ALIAS.getName()).length);
            ir.close();
        }
    }

    /**
     * Checks fields with different sort name to name is indexed correctly
     *
     * @throws Exception exception
     */
    @Test
    public void testIndexWorkWithTag() throws Exception {

        addWorkOne();
        RAMDirectory ramDir = new RAMDirectory();
        createIndex(ramDir);

        IndexReader ir = DirectoryReader.open(ramDir);
        assertEquals(2, ir.numDocs());
        {
            Document doc = ir.document(1);
            assertEquals(1, doc.getFields(WorkIndexField.WORK.getName()).length);
            assertEquals(1, doc.getFields(WorkIndexField.TAG.getName()).length);
            assertEquals("Classical", doc.getField(WorkIndexField.TAG.getName()).stringValue());
            assertEquals(1, doc.getFields(WorkIndexField.TAGCOUNT.getName()).length);
            assertEquals("10", doc.getField(LabelIndexField.TAGCOUNT.getName()).stringValue());
        }
        ir.close();
    }

}