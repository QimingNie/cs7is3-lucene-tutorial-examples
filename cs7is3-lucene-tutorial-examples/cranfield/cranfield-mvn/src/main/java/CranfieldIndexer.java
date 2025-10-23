import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.store.FSDirectory;
import java.io.*;
import java.nio.file.*;

public class CranfieldIndexer {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java CranfieldIndexer <cran.all.1400 path> <indexDir>");
            System.exit(1);
        }
        String cranPath = args[0];
        Path indexPath = Paths.get(args[1]);

        Analyzer analyzer = new EnglishAnalyzer();
        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
        iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        try (IndexWriter writer = new IndexWriter(FSDirectory.open(indexPath), iwc)) {
            parseAndIndex(cranPath, writer);
        }
        System.out.println("Indexing complete -> " + indexPath);
    }

    private static void parseAndIndex(String file, IndexWriter writer) throws Exception {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line, section = "";
            StringBuilder T = new StringBuilder(), A = new StringBuilder(), 
                          B = new StringBuilder(), W = new StringBuilder();
            String docno = null;

            Runnable flushDoc = () -> {
                try {
                    if (docno != null) {
                        Document doc = new Document();
                        doc.add(new StringField("docno", docno, Field.Store.YES));
                        doc.add(new TextField("title", T.toString(), Field.Store.YES));
                        doc.add(new TextField("authors", A.toString(), Field.Store.NO));
                        doc.add(new TextField("bib", B.toString(), Field.Store.NO));
                        doc.add(new TextField("abstract", W.toString(), Field.Store.YES));
                        doc.add(new TextField("content", T + "\n" + A + "\n" + B + "\n" + W, Field.Store.NO));
                        writer.addDocument(doc);
                    }
                } catch (IOException e) { throw new UncheckedIOException(e); }
            };

            while ((line = br.readLine()) != null) {
                if (line.startsWith(".I ")) {
                    if (docno != null) flushDoc.run();
                    T.setLength(0); A.setLength(0); B.setLength(0); W.setLength(0);
                    docno = line.substring(3).trim();
                    section = "";
                } else if (line.startsWith(".T")) section = "T";
                else if (line.startsWith(".A")) section = "A";
                else if (line.startsWith(".B")) section = "B";
                else if (line.startsWith(".W")) section = "W";
                else {
                    switch (section) {
                        case "T": T.append(line).append('\n'); break;
                        case "A": A.append(line).append('\n'); break;
                        case "B": B.append(line).append('\n'); break;
                        case "W": W.append(line).append('\n'); break;
                    }
                }
            }
            if (docno != null) flushDoc.run();
        }
    }
}
