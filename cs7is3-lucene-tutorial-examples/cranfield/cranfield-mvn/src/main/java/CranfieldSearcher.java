import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.file.Paths;
import java.util.Locale;

public class CranfieldSearcher {

    public static void main(String[] args) throws Exception {

        if (args.length < 3) {
            System.out.println("Usage: java CranfieldSearcher <indexDir> <queryFile> <outputDir> [--model=vsm|bm25|lm|dfr]");
            return;
        }

        String indexDir = args[0];
        String queryFile = args[1];
        String outputDir = args[2];
        String model = "bm25"; // default

        // Optional parameter
        if (args.length >= 4 && args[3].startsWith("--model")) {
            model = args[3].split("=")[1].toLowerCase(Locale.ROOT);
        }

        System.out.println("Selected model: " + model.toUpperCase());

        // Set up similarity (ranking model)
        Similarity similarity;
        switch (model) {
            case "vsm":
                similarity = new ClassicSimilarity();
                break;
            case "bm25":
                similarity = new BM25Similarity();
                break;
            case "lm":
            case "lmdirichlet":
                similarity = new LMDirichletSimilarity();
                break;
            case "dfr":
                similarity = new DFRSimilarity(
                        new BasicModelIF(),  
                        new AfterEffectB(),
                        new NormalizationH2()
                );
                break;
            default:
                System.out.println("Unknown model, defaulting to BM25");
                similarity = new BM25Similarity();
        }

        // Initialize searcher
        DirectoryReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexDir)));
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);

        Analyzer analyzer = new EnglishAnalyzer();

        // Output file
        String outputFile = Paths.get(outputDir, "run_" + model + ".txt").toString();
        BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));

        // Read queries from Cranfield format
        BufferedReader br = new BufferedReader(new FileReader(queryFile));
        String line;
        int queryID = 0;
        StringBuilder queryText = new StringBuilder();

        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.startsWith(".I")) {
                if (queryID != 0) {
                    runQuery(queryID, queryText.toString(), searcher, analyzer, writer, model);
                    queryText.setLength(0);
                }
                queryID++;
            } else if (line.startsWith(".W")) {
                continue;
            } else {
                queryText.append(line).append(" ");
            }
        }

        // Run the last query
        if (queryText.length() > 0) {
            runQuery(queryID, queryText.toString(), searcher, analyzer, writer, model);
        }

        br.close();
        writer.close();
        reader.close();

        System.out.println("Search completed. Output saved to: " + outputFile);
    }

    private static void runQuery(int queryID, String queryString, IndexSearcher searcher,
                                 Analyzer analyzer, BufferedWriter writer, String model) throws Exception {

        QueryParser parser = new QueryParser("contents", analyzer);
        Query query = parser.parse(QueryParser.escape(queryString));

        TopDocs results = searcher.search(query, 1000);
        ScoreDoc[] hits = results.scoreDocs;

        for (int rank = 0; rank < hits.length; rank++) {
            Document doc = searcher.doc(hits[rank].doc);
            String docID = doc.get("docID");
            float score = hits[rank].score;
            // TREC output format: queryID Q0 docID rank score runID
            writer.write(String.format(Locale.ROOT, "%d Q0 %s %d %.6f Lucene-%s%n",
                    queryID, docID, rank + 1, score, model));
        }
    }
}

