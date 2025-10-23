import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class CranfieldSearcher {
    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("Usage: java CranfieldSearcher <indexDir> <cran.qry path> <output_run.txt> <similarity: bm25|classic> <topK>");
            System.exit(1);
        }

        Path indexPath = Paths.get(args[0]);
        String qryPath = args[1];
        String outRun = args[2];
        String sim = args[3].toLowerCase(Locale.ROOT);
        int topK = Integer.parseInt(args[4]);

        Similarity similarity = sim.equals("bm25") ? new BM25Similarity()
                : sim.equals("classic") ? new ClassicSimilarity()
                : null;
        if (similarity == null) throw new IllegalArgumentException("similarity must be 'bm25' or 'classic'");

        Analyzer analyzer = new EnglishAnalyzer();
        String[] fields = new String[]{"title", "abstract", "content"};
        MultiFieldQueryParser parser = new MultiFieldQueryParser(fields, analyzer);

        try (DirectoryReader reader = DirectoryReader.open(FSDirectory.open(indexPath));
             BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outRun), "UTF-8"))) {

            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(similarity);

            List<QueryItem> queries = readQueries(qryPath);
            String runName = "lucene_" + sim;

            for (QueryItem q : queries) {
                Query query = parser.parse(q.text);
                TopDocs td = searcher.search(query, topK);
                int rank = 1;
                for (ScoreDoc sd : td.scoreDocs) {
                    Document doc = searcher.doc(sd.doc);
                    String docno = doc.get("docno");
                    float score = sd.score;
                    bw.write(q.id + " Q0 " + docno + " " + rank + " " + score + " " + runName + "\n");
                    rank++;
                }
            }
        }
        System.out.println("Search complete -> " + outRun);
    }

    static class QueryItem { int id; String text; }

    private static List<QueryItem> readQueries(String path) throws Exception {
        List<QueryItem> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(path), "UTF-8"))) {
            String line, section = "";
            StringBuilder W = new StringBuilder();
            Integer qid = null;

            Runnable flush = () -> {
                if (qid != null) {
                    QueryItem qi = new QueryItem();
                    qi.id = qid;
                    qi.text = W.toString().trim();
                    list.add(qi);
                }
            };

            while ((line = br.readLine()) != null) {
                if (line.startsWith(".I ")) {
                    if (qid != null) flush.run();
                    qid = Integer.parseInt(line.substring(3).trim());
                    W.setLength(0);
                    section = "";
                } else if (line.startsWith(".W")) {
                    section = "W";
                } else if ("W".equals(section)) {
                    W.append(line).append(' ');
                }
            }
            if (qid != null) flush.run();
        }
        return list;
    }
}
