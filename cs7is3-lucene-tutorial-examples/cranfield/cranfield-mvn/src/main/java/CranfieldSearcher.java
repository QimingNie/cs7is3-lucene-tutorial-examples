import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.file.*;

public class CranfieldSearcher {
    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("Usage: java CranfieldSearcher <indexDir> <queryFile> <outputFile> <similarity> <maxHits>");
            System.exit(1);
        }

        Path indexDir = Paths.get(args[0]);
        String queryFile = args[1];
        String outputFile = args[2];
        String simType = args[3];
        int maxHits = Integer.parseInt(args[4]);

        Similarity similarity;
        if (simType.equalsIgnoreCase("bm25")) {
            similarity = new BM25Similarity();
        } else {
            similarity = new ClassicSimilarity(); // Vector Space Model
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(queryFile));
             BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
             DirectoryReader indexReader = DirectoryReader.open(FSDirectory.open(indexDir))) {

            IndexSearcher searcher = new IndexSearcher(indexReader);
            searcher.setSimilarity(similarity);
            Analyzer analyzer = new EnglishAnalyzer();
            QueryParser parser = new QueryParser("content", analyzer);

            String line;
            StringBuilder queryText = new StringBuilder();
            String queryId = null;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith(".I ")) {
                    // previous query flush
                    if (queryId != null && queryText.length() > 0) {
                        executeQuery(searcher, parser, queryId, queryText.toString(), maxHits, writer);
                    }
                    queryId = line.substring(3).trim();
                    queryText.setLength(0);
                } else if (!line.startsWith(".W")) {
                    queryText.append(line).append(' ');
                }
            }
            // flush last query
            if (queryId != null && queryText.length() > 0) {
                executeQuery(searcher, parser, queryId, queryText.toString(), maxHits, writer);
            }
        }

        System.out.println("Search results saved to " + outputFile);
    }

    private static void executeQuery(IndexSearcher searcher, QueryParser parser, String queryId,
                                     String queryText, int maxHits, BufferedWriter writer) throws Exception {
        Query query = parser.parse(QueryParser.escape(queryText));
        TopDocs topDocs = searcher.search(query, maxHits);

        for (int i = 0; i < topDocs.scoreDocs.length; i++) {
            ScoreDoc sd = topDocs.scoreDocs[i];
            Document doc = searcher.doc(sd.doc);
            String docno = doc.get("docno");
            float score = sd.score;
            writer.write(String.format("%s Q0 %s %d %.4f Lucene\n", queryId, docno, i + 1, score));
        }
        writer.flush();
    }
}
