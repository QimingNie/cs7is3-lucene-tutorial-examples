import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.file.*;

public class CranfieldIndexer {

    /*
     * Main
     * Validate args: need [path_to_cran.all.1400, index_dir].
     * Build EnglishAnalyzer and IndexWriterConfig in CREATE mode (overwrite).
     * Open IndexWriter on index_dir.
     * Call parseAndIndex(filePath, writer) to parse raw Cranfield data and add documents.
     * Print completion message.
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java CranfieldIndexer <cran.all.1400 path> <indexDir>");
            System.exit(1);
        }
        // raw Cranfield file
        String cranPath = args[0];        
        // output index directory
        Path indexPath = Paths.get(args[1]); 

        Analyzer analyzer = new EnglishAnalyzer();             
        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
        // Initialization
        iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);   

        try (IndexWriter writer = new IndexWriter(FSDirectory.open(indexPath), iwc)) {
            // parse the Cranfield file and index each document we extract
            parseAndIndex(cranPath, writer);
        }

        System.out.println("Indexing complete -> " + indexPath);
    }

    /*
     * Open the file for UTF-8 reading.
     * Prepare 4 buffers for sections: T (title), A (authors), B (bibliography), W (abstract).
     * Keep current doc id (docno) and current section tag.
     * After reading all lines ,if a final document is buffered (docno != null), add it to the index.
     */
    private static void parseAndIndex(String file, IndexWriter writer) throws Exception {
        try (BufferedReader br = Files.newBufferedReader(Paths.get(file), StandardCharsets.UTF_8)) {
            String line;
            String section = "";      
            // current section flag: "T"/"A"/"B"/"W"/""
            StringBuilder T = new StringBuilder(); 
            StringBuilder A = new StringBuilder(); 
            StringBuilder B = new StringBuilder(); 
            StringBuilder W = new StringBuilder(); 
            String docno = null;                

            while ((line = br.readLine()) != null) {
                //When encountering a new document, initialize.
                if (line.startsWith(".I ")) {
                    if (docno != null) {
                        addDoc(writer, docno, T.toString(), A.toString(), B.toString(), W.toString());
                    }
                    // initialize
                    T.setLength(0);
                    A.setLength(0);
                    B.setLength(0);
                    W.setLength(0);
                    // everything after ".I "
                    docno = line.substring(3).trim(); 
                    section = "";                      
                } else if (line.startsWith(".T")) {
                    section = "T"; // Title
                } else if (line.startsWith(".A")) {
                    section = "A"; // Authors
                } else if (line.startsWith(".B")) {
                    section = "B"; // Bibliography
                } else if (line.startsWith(".W")) {
                    section = "W"; // Abstract / body
                } else {
                    // Fill in the content
                    switch (section) {
                        case "T": T.append(line).append('\n'); break;
                        case "A": A.append(line).append('\n'); break;
                        case "B": B.append(line).append('\n'); break;
                        case "W": W.append(line).append('\n'); break;
                        default: break;
                    }
                }
            }

            // if the last doc is still pending, add it too.
            if (docno != null) {
                addDoc(writer, docno, T.toString(), A.toString(), B.toString(), W.toString());
            }
        }
    }

    /*
     * Build a Lucene Document.
     * writer.addDocument(doc) to commit it to the index segment.
     */
    private static void addDoc(IndexWriter writer,
                               String docno,
                               String title,
                               String authors,
                               String bib,
                               String abstr) throws IOException {
        Document doc = new Document();

        doc.add(new StringField("docno", docno, Field.Store.YES));
        doc.add(new TextField("title", title, Field.Store.YES));     
        doc.add(new TextField("authors", authors, Field.Store.NO));   
        doc.add(new TextField("bib", bib, Field.Store.NO));            
        doc.add(new TextField("abstract", abstr, Field.Store.YES));  

        // Aggregate field to enable search across all textual parts
        String full = title + "\n" + authors + "\n" + bib + "\n" + abstr;
        doc.add(new TextField("content", full, Field.Store.NO));      

        writer.addDocument(doc);
    }
}
